from datetime import datetime

from apscheduler.schedulers.background import BackgroundScheduler

from app.core.config import settings
from app.services.model_manager import ModelManager


class RetrainingService:
    def __init__(self, manager: ModelManager) -> None:
        self.manager = manager
        self.scheduler = BackgroundScheduler(timezone="UTC")

    def start(self) -> None:
        self.scheduler.add_job(self.run_retrain_job, "cron", day_of_week="sun", hour=3, minute=0, id="weekly-retrain")
        self.scheduler.start()

    def stop(self) -> None:
        if self.scheduler.running:
            self.scheduler.shutdown(wait=False)

    def run_retrain_job(self) -> str:
        # Placeholder for full fine-tuning pipeline integration.
        version = f"v{datetime.utcnow().strftime('%Y.%m.%d.%H%M')}"
        metrics = {"f1": 0.87, "precision": 0.86, "recall": 0.88}
        self.manager.register_model(version, "Scheduled retraining run", metrics)
        return version

