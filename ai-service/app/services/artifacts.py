import hashlib
import io
import logging
import urllib.request
import zipfile
from pathlib import Path
from urllib.parse import urlsplit

from app.core.config import settings
from app.services.ingestion import _validate_url

logger = logging.getLogger(__name__)

_REQUIRED_FILES = ("model.onnx", "tokenizer.json", "manifest.json")
_USER_AGENT = "TruthGuardAI/1.0 artifact loader"


def artifacts_present() -> bool:
    return all(Path(path).is_file() for path in (settings.model_path, settings.tokenizer_path, settings.manifest_path))


def ensure_artifacts() -> None:
    """Download a checksummed model bundle when artifacts are absent.

    The bundle is an operator-provided zip from a release URL, not user input.
    It is still validated: https only, public host, size caps, SHA-256 match,
    and only the three expected file names are extracted.
    """
    if artifacts_present():
        return

    url = settings.model_artifact_url.strip()
    if not url:
        return

    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.hostname or parts.port not in (None, 443):
        raise RuntimeError("MODEL_ARTIFACT_URL must be a public https URL on port 443")
    _validate_url(url)

    expected = settings.model_artifact_sha256.strip().lower()
    if expected and (len(expected) != 64 or any(char not in "0123456789abcdef" for char in expected)):
        raise RuntimeError("MODEL_ARTIFACT_SHA256 must be a 64-character hex digest")
    if not expected and settings.is_production:
        raise RuntimeError("MODEL_ARTIFACT_SHA256 is required in production")

    max_bytes = settings.model_artifact_max_mb * 1024 * 1024
    logger.info("Downloading model artifact from %s", parts.hostname)

    request = urllib.request.Request(url, headers={"User-Agent": _USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        final_url = response.geturl()
        final_parts = urlsplit(final_url)
        if final_parts.scheme != "https":
            raise RuntimeError("Model artifact redirect must stay on https")
        if final_parts.hostname != parts.hostname:
            _validate_url(final_url)

        digest = hashlib.sha256()
        buffer = io.BytesIO()
        total = 0
        while True:
            chunk = response.read(65536)
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise RuntimeError(f"Model artifact exceeds {settings.model_artifact_max_mb} MB")
            digest.update(chunk)
            buffer.write(chunk)

    actual = digest.hexdigest()
    if expected and actual != expected:
        raise RuntimeError("Model artifact checksum does not match MODEL_ARTIFACT_SHA256")

    target_dir = Path(settings.model_path).parent
    target_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(io.BytesIO(buffer.getvalue())) as archive:
        members = {
            Path(info.filename).name: info
            for info in archive.infolist()
            if not info.is_dir()
        }
        missing = [name for name in _REQUIRED_FILES if name not in members]
        if missing:
            raise RuntimeError(f"Model artifact is missing: {', '.join(missing)}")
        for name in _REQUIRED_FILES:
            info = members[name]
            if info.file_size > max_bytes:
                raise RuntimeError(f"Model artifact member {name} is too large")
            data = archive.read(info)
            destination = target_dir / name
            temporary = destination.with_suffix(destination.suffix + ".tmp")
            temporary.write_bytes(data)
            temporary.replace(destination)

    logger.info("Model artifact extracted to %s (sha256=%s)", target_dir, actual)
