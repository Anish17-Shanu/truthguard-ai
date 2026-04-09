from datetime import datetime, timezone

from pymongo import MongoClient

from app.core.config import settings


class ModelManager:
    def __init__(self) -> None:
        self.client = MongoClient(settings.mongodb_uri)
        self.db = self.client[settings.mongodb_db]

    def current_model_version(self) -> str:
        current = self.db.models.find_one({"active": True}, sort=[("trainedAt", -1)])
        return (current or {}).get("version", settings.model_version)

    def save_feedback(self, payload: dict) -> None:
        payload["createdAt"] = datetime.now(timezone.utc)
        self.db.feedback.insert_one(payload)

    def register_model(self, version: str, notes: str, metrics: dict[str, float]) -> None:
        self.db.models.update_many({"active": True}, {"$set": {"active": False}})
        self.db.models.insert_one(
            {
                "version": version,
                "notes": notes,
                "metrics": metrics,
                "active": True,
                "trainedAt": datetime.now(timezone.utc),
            }
        )

