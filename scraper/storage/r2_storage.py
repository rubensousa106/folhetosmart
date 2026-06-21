"""Armazenamento no Cloudflare R2 (S3-compatível) — substitui o Google Drive.

Guarda os folhetos PDF (que o produtor descarrega) e o JSON normalizado (que a
app descarrega via link assinado). Degrada graciosamente: se as credenciais não
estiverem configuradas, `is_configured()` devolve False.

Variáveis de ambiente:
    R2_ENDPOINT          https://<accountid>.r2.cloudflarestorage.com  (SEM o bucket)
    R2_BUCKET            folhetosmart
    R2_ACCESS_KEY_ID     <chave da API do R2>
    R2_SECRET_ACCESS_KEY <segredo da API do R2>
    FOLHETO_INSECURE_TLS 1  (dev, rede com inspeção TLS)
"""
from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


def _safe(name: str) -> str:
    return re.sub(r"[^\w .\-]", "_", os.path.basename(name))


class R2Storage:
    """Cliente R2 (lazy: só liga quando usado)."""

    def __init__(self) -> None:
        self.bucket = os.getenv("R2_BUCKET", "folhetosmart")
        self.endpoint = (os.getenv("R2_ENDPOINT") or "").rstrip("/")
        self.access_key = os.getenv("R2_ACCESS_KEY_ID")
        self.secret_key = os.getenv("R2_SECRET_ACCESS_KEY")
        self._client = None

    def is_configured(self) -> bool:
        return bool(self.endpoint and self.bucket and self.access_key and self.secret_key)

    @property
    def client(self):
        if self._client is None:
            import boto3  # lazy
            from botocore.config import Config  # lazy

            insecure = os.getenv("FOLHETO_INSECURE_TLS", "0") == "1"
            self._client = boto3.client(
                "s3",
                endpoint_url=self.endpoint,
                aws_access_key_id=self.access_key,
                aws_secret_access_key=self.secret_key,
                region_name="auto",
                config=Config(signature_version="s3v4"),
                verify=not insecure,  # rede com inspeção TLS (dev)
            )
        return self._client

    # -- folhetos PDF ---------------------------------------------------------
    def list_pdfs(self) -> list[dict]:
        """PDFs no bucket, mais recentes primeiro. key == name (sem id, ao contrário do Drive)."""
        resp = self.client.list_objects_v2(Bucket=self.bucket)
        pdfs = [
            {"key": o["Key"], "name": o["Key"], "modified": o.get("LastModified")}
            for o in resp.get("Contents", [])
            if o["Key"].lower().endswith(".pdf")
        ]
        pdfs.sort(key=lambda o: o.get("modified") or 0, reverse=True)
        return pdfs

    def download_pdf(self, key: str, dest_dir: str) -> Path:
        os.makedirs(dest_dir, exist_ok=True)
        target = Path(dest_dir) / _safe(key)
        self.client.download_file(self.bucket, key, str(target))
        logger.info("R2: descarregado %s", target.name)
        return target

    # -- JSON normalizado -----------------------------------------------------
    def upload_json(self, key: str, obj) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.client.put_object(
            Bucket=self.bucket, Key=key, Body=body,
            ContentType="application/json; charset=utf-8",
        )
        logger.info("R2: carregado %s (%d bytes)", key, len(body))

    def presign_get(self, key: str, expires: int = 7 * 24 * 3600) -> str:
        """Link assinado e temporário para GET (a app descarrega por aqui; máx. 7 dias)."""
        return self.client.generate_presigned_url(
            "get_object",
            Params={"Bucket": self.bucket, "Key": key},
            ExpiresIn=expires,
        )


# Instância partilhada.
r2_storage = R2Storage()
