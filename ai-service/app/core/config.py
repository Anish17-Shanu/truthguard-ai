from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        protected_namespaces=("settings_",),
    )

    app_name: str = "TruthGuard AI Service"
    app_env: str = "development"
    mongodb_uri: str = "mongodb://localhost:27017"
    mongodb_db: str = "truthguard"
    primary_model_name: str = "distilbert-base-multilingual-cased"
    emotion_model_name: str = "j-hartmann/emotion-english-distilroberta-base"
    model_version: str = "v1.0.0"
    retrain_cron: str = "0 3 * * 0"


settings = Settings()
