from pydantic import AliasChoices, Field
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

    model_path: str = "artifacts/model.onnx"
    tokenizer_path: str = "artifacts/tokenizer.json"
    manifest_path: str = "artifacts/manifest.json"
    model_version: str = "v1.0.0"
    model_artifact_url: str = ""
    model_artifact_sha256: str = ""
    model_artifact_max_mb: int = 300

    max_chars: int = 20_000
    min_chars: int = 20
    max_file_mb: int = 10
    max_pdf_pages: int = 200

    fetch_timeout_seconds: int = 15
    fetch_max_bytes: int = 5 * 1024 * 1024
    fetch_max_redirects: int = 3
    allow_private_networks: bool = False
    respect_robots_txt: bool = True

    require_english: bool = True

    # Shares one value with the backend; both names are accepted so a single
    # platform env var can be attached to both services.
    internal_token: str = Field(
        "", validation_alias=AliasChoices("AI_INTERNAL_TOKEN", "INTERNAL_TOKEN")
    )
    retrain_enabled: bool = False
    # Production refuses to serve heuristic (non-model) results unless this is
    # explicitly set, so a missing model artifact cannot silently ship.
    allow_heuristic_fallback: bool = False

    @property
    def is_production(self) -> bool:
        return self.app_env.lower() in {"prod", "production"}


settings = Settings()
