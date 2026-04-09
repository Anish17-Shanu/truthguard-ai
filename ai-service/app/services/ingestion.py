import base64
import io
from pathlib import Path

import requests
import trafilatura
from pypdf import PdfReader


def extract_text_from_url(url: str) -> str:
    response = requests.get(url, timeout=20)
    response.raise_for_status()
    extracted = trafilatura.extract(response.text, include_comments=False, include_tables=False)
    if not extracted:
        raise ValueError("Could not extract text from URL")
    return extracted


def extract_text_from_file(file_base64: str, file_name: str | None) -> str:
    raw = base64.b64decode(file_base64)
    suffix = Path(file_name or "").suffix.lower()
    if suffix == ".txt":
        return raw.decode("utf-8", errors="ignore")
    if suffix == ".pdf":
        reader = PdfReader(io.BytesIO(raw))
        pages = [page.extract_text() or "" for page in reader.pages]
        return "\n".join(pages).strip()
    try:
        return raw.decode("utf-8", errors="ignore")
    except Exception as exc:
        raise ValueError("Unsupported file format") from exc

