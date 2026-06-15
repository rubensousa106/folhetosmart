"""Configuração central do scraper (lida de variáveis de ambiente)."""
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql://folheto:folheto@localhost:5432/folhetosmart",
    )
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    # claude-sonnet-4-20250514 foi reformado (06/2026); sucessor: claude-sonnet-4-6.
    anthropic_model: str = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-6")
    # Redes com inspeção TLS: desliga a verificação de certificados nas
    # chamadas à Claude API (APENAS dev; nunca em produção).
    insecure_tls: bool = os.getenv("FOLHETO_INSECURE_TLS", "0") == "1"
    worker_port: int = int(os.getenv("WORKER_PORT", "8090"))
    # Diretório onde os folhetos descarregados são guardados.
    data_dir: str = os.getenv("SCRAPER_DATA_DIR", "data")
    # User-agent "educado" para o scraping.
    user_agent: str = os.getenv(
        "SCRAPER_USER_AGENT",
        "FolhetoSmartBot/1.0 (+https://folhetosmart.pt/bot)",
    )
    # Zona para o folheto regional do Aldi (o worker corre como sistema;
    # cada utilizador define a sua zona na app via PUT /api/v1/users/me).
    aldi_district: str = os.getenv("ALDI_DISTRICT", "Lisboa")
    aldi_city: str = os.getenv("ALDI_CITY", "Lisboa")
    # Google Drive — salvaguarda de folhetos PDF. Credenciais via JSON inline
    # OU caminho para o ficheiro da service account.
    gdrive_credentials_json: str = os.getenv("GOOGLE_DRIVE_CREDENTIALS_JSON", "")
    gdrive_credentials_path: str = os.getenv("GOOGLE_DRIVE_CREDENTIALS_PATH", "")
    gdrive_folder_id: str = os.getenv("GOOGLE_DRIVE_FOLDER_ID", "")
    # Firebase Cloud Messaging (avisar o admin de folhetos em falta).
    fcm_credentials_json: str = os.getenv("FCM_CREDENTIALS_JSON", "")
    fcm_credentials_path: str = os.getenv("FCM_CREDENTIALS_PATH", "")
    fcm_project_id: str = os.getenv("FCM_PROJECT_ID", "")


settings = Settings()
