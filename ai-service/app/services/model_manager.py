import logging
from datetime import datetime, timezone
from typing import Any

from pymongo import ASCENDING, MongoClient
from pymongo.errors import PyMongoError

from app.core.config import settings

logger = logging.getLogger(__name__)


class ModelManager:
    """Persistence for feedback, user-data deletion, and retrain requests.

    Analysis itself never depends on MongoDB; the API stays usable when the
    database is temporarily unavailable and only feedback/retrain fail.
    """

    def __init__(self) -> None:
        self.client = MongoClient(
            settings.mongodb_uri,
            serverSelectionTimeoutMS=3000,
            connectTimeoutMS=3000,
            socketTimeoutMS=5000,
        )
        self.db = self.client[settings.mongodb_db]

    def ensure_indexes(self) -> None:
        try:
            self.db.feedback.create_index([("analysisId", ASCENDING)], unique=True)
            self.db.feedback.create_index([("userId", ASCENDING), ("createdAt", ASCENDING)])
            self.db.retrain_requests.create_index([("createdAt", ASCENDING)])
        except PyMongoError as exc:
            logger.warning("Could not create AI service indexes: %s", exc)

    def save_feedback(self, payload: dict[str, Any]) -> None:
        now = datetime.now(timezone.utc)
        document = {
            "analysisId": payload["analysis_id"],
            "userId": payload.get("user_id"),
            "correct": payload["correct"],
            "correctedLabel": payload.get("corrected_label"),
            "comment": payload.get("comment"),
            "modelVersion": payload.get("model_version"),
            "contentHash": payload.get("content_hash"),
            "updatedAt": now,
        }
        self.db.feedback.update_one(
            {"analysisId": payload["analysis_id"]},
            {"$set": document, "$setOnInsert": {"createdAt": now}},
            upsert=True,
        )

    def delete_user_data(self, user_id: str) -> int:
        result = self.db.feedback.delete_many({"userId": user_id})
        return int(result.deleted_count)

    def request_retrain(self, requested_by: str) -> str:
        now = datetime.now(timezone.utc)
        result = self.db.retrain_requests.insert_one(
            {"requestedBy": requested_by, "status": "queued", "createdAt": now}
        )
        return str(result.inserted_id)

    def registered_metrics(self, version: str) -> dict[str, float]:
        try:
            document = self.db.models.find_one({"version": version})
        except PyMongoError as exc:
            logger.warning("Could not read model registry: %s", exc)
            return {}
        if not document:
            return {}
        metrics = document.get("metrics") or {}
        return {str(key): float(value) for key, value in metrics.items() if isinstance(value, (int, float))}

    def close(self) -> None:
        self.client.close()
