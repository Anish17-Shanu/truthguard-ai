import base64

import pytest

from app.services.ingestion import (
    _validate_url,
    extract_text_from_file,
)


@pytest.mark.parametrize(
    "url",
    [
        "ftp://example.com/file",
        "file:///etc/passwd",
        "http://127.0.0.1/",
        "http://localhost:8080/",
        "http://169.254.169.254/latest/meta-data/",
        "http://10.0.0.5/",
        "http://user:pass@example.com/",
        "http://example.com:6379/",
    ],
)
def test_validate_url_rejects_unsafe_targets(url):
    with pytest.raises(ValueError):
        _validate_url(url)


def test_extract_text_from_txt():
    raw = b"The council approved the budget after a public hearing on Tuesday."
    content = extract_text_from_file(base64.b64encode(raw).decode(), "story.txt")
    assert "council approved" in content


def test_extract_rejects_fake_pdf():
    with pytest.raises(ValueError):
        extract_text_from_file(base64.b64encode(b"not a pdf").decode(), "story.pdf")


def test_extract_rejects_unsupported_extension():
    with pytest.raises(ValueError):
        extract_text_from_file(base64.b64encode(b"data").decode(), "story.docx")


def test_extract_rejects_binary_txt():
    with pytest.raises(ValueError):
        extract_text_from_file(base64.b64encode(b"\x00\x01\x02binary").decode(), "story.txt")
