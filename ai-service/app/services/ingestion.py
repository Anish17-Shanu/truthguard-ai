import base64
import io
import ipaddress
import logging
import socket
import time
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.robotparser import RobotFileParser

import requests
import trafilatura
from pypdf import PdfReader

from app.core.config import settings

logger = logging.getLogger(__name__)

_ALLOWED_PORTS = {None, 80, 443}
_ALLOWED_CONTENT_TYPES = {
    "text/html",
    "application/xhtml+xml",
    "text/plain",
    "application/xml",
    "text/xml",
}
_USER_AGENT = "TruthGuardAI/1.0 (+https://truthguard.ai; content analysis bot)"
_ROBOTS_CACHE: dict[str, tuple[float, RobotFileParser | None]] = {}
_ROBOTS_TTL_SECONDS = 3600


def extract_text_from_url(url: str) -> str:
    _validate_url(url)
    if not _robots_allowed(url):
        raise ValueError("This URL disallows automated retrieval via robots.txt")
    html = _fetch(url)
    extracted = trafilatura.extract(html, include_comments=False, include_tables=False)
    if not extracted:
        raise ValueError("Could not extract readable text from the URL")
    return extracted


def extract_text_from_file(file_base64: str, file_name: str | None) -> str:
    try:
        raw = base64.b64decode(file_base64, validate=True)
    except Exception as exc:
        raise ValueError("Invalid file encoding") from exc

    if len(raw) > settings.max_file_mb * 1024 * 1024:
        raise ValueError(f"File exceeds {settings.max_file_mb} MB")

    suffix = Path(file_name or "").suffix.lower()
    if suffix == ".txt":
        if b"\x00" in raw[:4096]:
            raise ValueError("File does not look like text")
        return raw.decode("utf-8", errors="replace")
    if suffix == ".pdf":
        if not raw.startswith(b"%PDF-"):
            raise ValueError("File is not a valid PDF")
        try:
            reader = PdfReader(io.BytesIO(raw))
            if reader.is_encrypted:
                raise ValueError("Encrypted PDFs are not supported")
            if len(reader.pages) > settings.max_pdf_pages:
                raise ValueError(f"PDF exceeds {settings.max_pdf_pages} pages")
            pages = [page.extract_text() or "" for page in reader.pages]
        except ValueError:
            raise
        except Exception as exc:
            raise ValueError("Could not read PDF") from exc
        return "\n".join(pages).strip()

    raise ValueError("Unsupported file type; upload a .txt or .pdf file")


def _validate_url(url: str) -> None:
    if len(url) > 2048:
        raise ValueError("URL is too long")
    parts = urlsplit(url)
    if parts.scheme not in {"http", "https"}:
        raise ValueError("Only http and https URLs are allowed")
    if parts.username or parts.password:
        raise ValueError("URLs with credentials are not allowed")
    if not parts.hostname:
        raise ValueError("URL must include a host")
    if parts.port not in _ALLOWED_PORTS:
        raise ValueError("Only ports 80 and 443 are allowed")
    _resolve_and_validate(parts.hostname)


def _resolve_and_validate(host: str) -> None:
    try:
        addresses = socket.getaddrinfo(host, None, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise ValueError("URL host could not be resolved") from exc
    if not addresses:
        raise ValueError("URL host could not be resolved")

    for address in addresses:
        ip = ipaddress.ip_address(address[4][0])
        candidates = [ip]
        if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
            candidates.append(ip.ipv4_mapped)
        for candidate in candidates:
            if settings.allow_private_networks:
                continue
            if (
                candidate.is_private
                or candidate.is_loopback
                or candidate.is_link_local
                or candidate.is_multicast
                or candidate.is_reserved
                or candidate.is_unspecified
            ):
                raise ValueError("URL resolves to a disallowed network address")


def _fetch(url: str) -> str:
    current = url
    for _ in range(settings.fetch_max_redirects + 1):
        _validate_url(current)
        try:
            response = requests.get(
                current,
                allow_redirects=False,
                stream=True,
                timeout=(5, settings.fetch_timeout_seconds),
                headers={"User-Agent": _USER_AGENT, "Accept": "text/html,text/plain;q=0.9"},
            )
        except requests.RequestException as exc:
            raise ValueError("Could not fetch the URL") from exc

        if response.is_redirect or response.is_permanent_redirect:
            location = response.headers.get("Location")
            response.close()
            if not location:
                raise ValueError("Redirect without a target")
            current = urljoin(current, location)
            continue

        try:
            response.raise_for_status()
        except requests.RequestException as exc:
            response.close()
            raise ValueError("The URL returned an error status") from exc

        content_type = response.headers.get("Content-Type", "").split(";")[0].strip().lower()
        if content_type and content_type not in _ALLOWED_CONTENT_TYPES:
            response.close()
            raise ValueError("The URL does not point to readable text or HTML")

        charset = response.encoding or "utf-8"
        chunks: list[bytes] = []
        total = 0
        try:
            for chunk in response.iter_content(chunk_size=65536):
                if not chunk:
                    continue
                total += len(chunk)
                if total > settings.fetch_max_bytes:
                    raise ValueError("URL content exceeds the size limit")
                chunks.append(chunk)
        finally:
            response.close()
        return b"".join(chunks).decode(charset, errors="ignore")

    raise ValueError("Too many redirects")


def _robots_allowed(url: str) -> bool:
    if not settings.respect_robots_txt:
        return True
    parts = urlsplit(url)
    robots_url = f"{parts.scheme}://{parts.netloc}/robots.txt"
    cached = _ROBOTS_CACHE.get(robots_url)
    now = time.time()
    if cached and cached[0] > now:
        parser = cached[1]
        return True if parser is None else parser.can_fetch(_USER_AGENT, url)

    parser: RobotFileParser | None = None
    try:
        content = _fetch(robots_url)
        parser = RobotFileParser()
        parser.parse(content.splitlines())
    except Exception as exc:
        logger.info("robots.txt unavailable for %s: %s", robots_url, exc)

    _ROBOTS_CACHE[robots_url] = (now + _ROBOTS_TTL_SECONDS, parser)
    return True if parser is None else parser.can_fetch(_USER_AGENT, url)
