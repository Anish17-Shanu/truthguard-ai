from fastapi.testclient import TestClient

from app.main import app
from app.core.config import settings


client = TestClient(app)

ENGLISH_TEXT = (
    "The city council approved the new transit budget on Tuesday after a public "
    "hearing, and construction is expected to begin next spring."
)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_ready_reports_classifier_mode():
    response = client.get("/ready")
    assert response.status_code == 200
    body = response.json()
    assert body["ready"] is True
    assert body["classifier_mode"] in {"transformer", "heuristic"}


def test_analyze_rejects_empty():
    response = client.post("/analyze", json={})
    assert response.status_code == 400


def test_analyze_rejects_ambiguous_sources():
    response = client.post(
        "/analyze",
        json={"text": ENGLISH_TEXT, "url": "https://example.com/story"},
    )
    assert response.status_code == 400


def test_analyze_returns_classification():
    response = client.post("/analyze", json={"text": ENGLISH_TEXT})
    assert response.status_code == 200
    body = response.json()
    assert body["label"] in {"real", "fake", "propaganda", "uncertain"}
    assert 0 <= body["confidence"] <= 1
    assert body["classifier_mode"] in {"transformer", "heuristic"}
    assert len(body["content_hash"]) == 64


def test_analyze_rejects_short_text():
    response = client.post("/analyze", json={"text": "too short"})
    assert response.status_code == 400


def test_internal_token_is_enforced(monkeypatch):
    monkeypatch.setattr(settings, "internal_token", "a" * 32)
    response = client.post("/feedback", json={"analysis_id": "abc", "correct": True})
    assert response.status_code == 401

    response = client.post(
        "/feedback",
        json={"analysis_id": "abc", "correct": True},
        headers={"X-Internal-Token": "wrong-token-value-that-is-32-chars"},
    )
    assert response.status_code == 401


def test_retrain_is_disabled_by_default(monkeypatch):
    monkeypatch.setattr(settings, "internal_token", "")
    monkeypatch.setattr(settings, "retrain_enabled", False)
    response = client.post("/retrain")
    assert response.status_code == 501


def test_request_id_is_echoed():
    response = client.get("/health", headers={"X-Request-ID": "test-request-id"})
    assert response.headers["X-Request-ID"] == "test-request-id"
