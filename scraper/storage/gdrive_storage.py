"""Salvaguarda de folhetos PDF no Google Drive (service account).

Funciona como camada de backup/fallback: o scraper guarda lá uma cópia após
cada download bem-sucedido, e quando o download automático falha o pipeline
procura no Drive um PDF desta semana (carregado manualmente pelo utilizador).

Degrada graciosamente: se as credenciais/pasta não estiverem configuradas,
`is_configured()` devolve False e o pipeline ignora o Drive.
"""
from __future__ import annotations

import datetime as dt
import json
import logging
import re
from pathlib import Path
from typing import Optional

from config.settings import settings

logger = logging.getLogger(__name__)

SCOPES = ["https://www.googleapis.com/auth/drive"]
_DATE_RE = re.compile(r"(\d{2})-(\d{2})-(\d{4})")  # DD-MM-YYYY no nome do ficheiro


def _downloads_dir() -> Path:
    path = Path(settings.data_dir) / "folhetos"
    path.mkdir(parents=True, exist_ok=True)
    return path


class GoogleDriveStorage:
    """Cliente do Google Drive para os folhetos (lazy: só liga quando usado)."""

    def __init__(self) -> None:
        self.folder_id = settings.gdrive_folder_id
        self._creds_info = self._load_credentials()
        self._service = None

    # -- configuração ---------------------------------------------------------
    @staticmethod
    def _load_credentials() -> Optional[dict]:
        if settings.gdrive_credentials_json.strip():
            try:
                return json.loads(settings.gdrive_credentials_json)
            except json.JSONDecodeError:
                logger.warning("GOOGLE_DRIVE_CREDENTIALS_JSON não é JSON válido")
        path = settings.gdrive_credentials_path.strip()
        if path and Path(path).exists():
            try:
                return json.loads(Path(path).read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                logger.warning("Credenciais do Drive ilegíveis em %s", path)
        return None

    def is_configured(self) -> bool:
        return bool(self._creds_info and self.folder_id)

    @property
    def service(self):
        if self._service is None:
            from google.oauth2 import service_account  # lazy
            from googleapiclient.discovery import build  # lazy

            creds = service_account.Credentials.from_service_account_info(
                self._creds_info, scopes=SCOPES
            )
            self._service = build("drive", "v3", credentials=creds, cache_discovery=False)
        return self._service

    # -- operações ------------------------------------------------------------
    def list_current_week_pdfs(self, window) -> list[dict]:
        """PDFs na pasta cujo nome contém uma data dentro (±7 dias) da semana."""
        query = (
            f"'{self.folder_id}' in parents and "
            "mimeType='application/pdf' and trashed=false"
        )
        try:
            resp = self.service.files().list(
                q=query, fields="files(id,name,modifiedTime)", pageSize=200
            ).execute()
        except Exception as exc:  # noqa: BLE001
            logger.warning("Drive: falha ao listar ficheiros: %s", exc)
            return []

        low = window.valid_from - dt.timedelta(days=7)
        high = window.valid_until + dt.timedelta(days=7)
        result = []
        for f in resp.get("files", []):
            if any(low <= d <= high for d in _dates_in(f["name"])):
                result.append(f)
        logger.info("Drive: %d PDFs desta semana na pasta", len(result))
        return result

    def find_pdf_for(self, supermarket_name: str, window) -> Optional[dict]:
        """O PDF desta semana cujo nome contém o nome do supermercado."""
        needle = supermarket_name.lower().replace("-", " ")
        for f in self.list_current_week_pdfs(window):
            if needle in f["name"].lower():
                return f
        return None

    def download_pdf(self, file_id: str, filename: str) -> Path:
        """Descarrega o PDF para data/folhetos/{filename} e devolve o path."""
        from googleapiclient.http import MediaIoBaseDownload  # lazy

        target = _downloads_dir() / _safe(filename)
        request = self.service.files().get_media(fileId=file_id)
        with open(target, "wb") as fh:
            downloader = MediaIoBaseDownload(fh, request)
            done = False
            while not done:
                _status, done = downloader.next_chunk()
        logger.info("Drive: descarregado %s", target.name)
        return target

    def upload_pdf(
        self, local_path, filename: str, replace: bool = False
    ) -> Optional[str]:
        """Faz upload de um PDF para o Drive — 1 cópia por supermercado/semana.

        Se já existir um ficheiro com o mesmo nome (mesmo supermercado, mesma
        semana):
        - `replace=False` (omissão): NÃO duplica — ignora e devolve o id existente;
        - `replace=True` (upload do ADMIN): substitui o conteúdo do existente.
        """
        from googleapiclient.http import MediaFileUpload  # lazy

        try:
            existing = self._find_by_name(filename)
            if existing and not replace:
                logger.info("Drive: %s já existe — não duplica", filename)
                return existing

            media = MediaFileUpload(str(local_path), mimetype="application/pdf")
            if existing:
                # Substitui o conteúdo, mantendo o mesmo ficheiro (sem duplicar).
                self.service.files().update(
                    fileId=existing, media_body=media
                ).execute()
                logger.info("Drive: %s substituído", filename)
                return existing

            meta = {"name": filename, "parents": [self.folder_id]}
            created = self.service.files().create(
                body=meta, media_body=media, fields="id"
            ).execute()
            logger.info("Drive: carregado %s", filename)
            return created.get("id")
        except Exception as exc:  # noqa: BLE001 — backup não deve travar a sync
            logger.warning("Drive: falha no upload de %s: %s", filename, exc)
            return None

    def _find_by_name(self, filename: str) -> Optional[str]:
        query = (
            f"'{self.folder_id}' in parents and name='{filename}' "
            "and trashed=false"
        )
        resp = self.service.files().list(q=query, fields="files(id)", pageSize=1).execute()
        files = resp.get("files", [])
        return files[0]["id"] if files else None

    # -- naming ---------------------------------------------------------------
    @staticmethod
    def pdf_filename_for(supermarket_name: str, window) -> str:
        """Ex.: 'Continente 12-06-2026 - 18-06-2026.pdf'."""
        f = window.valid_from.strftime("%d-%m-%Y")
        u = window.valid_until.strftime("%d-%m-%Y")
        return f"{supermarket_name} {f} - {u}.pdf"


def _dates_in(name: str) -> list[dt.date]:
    dates = []
    for d, m, y in _DATE_RE.findall(name):
        try:
            dates.append(dt.date(int(y), int(m), int(d)))
        except ValueError:
            continue
    return dates


def _safe(filename: str) -> str:
    return re.sub(r"[^\w .\-]", "_", filename)


# Instância partilhada (lê as settings uma vez).
drive_storage = GoogleDriveStorage()
