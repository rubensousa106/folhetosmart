"""Vigia a pasta do Google Drive e processa PDFs novos automaticamente.

Quando um PDF cai na pasta, espera-se 2 minutos (para garantir que o upload
terminou) e só depois é processado — sem intervenção manual. Corre num loop
infinito, a verificar a cada 2 minutos (serviço `drive-scheduler` no compose).
"""
import logging
import re
import time
from datetime import datetime, timedelta, timezone

from db import get_processed_file_ids, mark_file_processed
from pipeline import run_drive_flyer_sync
from storage.gdrive_storage import drive_storage

logger = logging.getLogger(__name__)

# Slugs como estão na BD (supermarkets.slug) — Pingo Doce usa hífen!
SLUG_MAP = {
    "Continente": "continente",
    "Pingo Doce": "pingo-doce",
    "PingoDoce": "pingo-doce",
    "Lidl": "lidl",
    "Intermarché": "intermarche",
    "Intermarche": "intermarche",
    "Aldi": "aldi",
}


def parse_filename(filename: str) -> dict | None:
    """Extrai supermercado e datas do nome do ficheiro.

    Formatos aceites:
      "Continente 12-06-2026 - 18-06-2026.pdf"
      "Continente 8/06/2026 - 16/06/2026.pdf"
    """
    name = filename.replace(".pdf", "").strip()

    pattern = (
        r"^(.+?)\s+"
        r"(\d{1,2}[-/]\d{2}[-/]?\d{4})"
        r"\s*[-–]\s*"
        r"(\d{1,2}[-/]\d{2}[-/]?\d{4})$"
    )
    match = re.match(pattern, name)
    if not match:
        return None

    supermarket = match.group(1).strip()
    valid_from = match.group(2).replace("/", "-")
    valid_until = match.group(3).replace("/", "-")

    slug = SLUG_MAP.get(supermarket)
    if not slug:
        logger.warning("Supermercado desconhecido: %s", supermarket)
        return None

    return {
        "supermarket": supermarket,
        "slug": slug,
        "valid_from": valid_from,
        "valid_until": valid_until,
    }


def check_and_process_new_files():
    """Processa os PDFs da pasta com mais de 2 minutos (upload completo)."""
    if not drive_storage.is_configured():
        logger.warning("Google Drive não configurado — nada a verificar.")
        return

    logger.info("A verificar pasta do Google Drive...")

    result = drive_storage.service.files().list(
        q=(
            f"'{drive_storage.folder_id}' in parents "
            f"and mimeType='application/pdf' "
            f"and trashed=false"
        ),
        fields="files(id, name, createdTime)",
        orderBy="createdTime desc",
    ).execute()

    files = result.get("files", [])
    logger.info("Encontrados %d PDFs na pasta", len(files))

    # Só processa ficheiros com mais de 2 minutos.
    now = datetime.now(timezone.utc)
    two_minutes_ago = now - timedelta(minutes=2)

    # IDs já processados (guardados na BD).
    processed_ids = get_processed_file_ids()

    for file in files:
        file_id = file["id"]
        filename = file["name"]

        # O upload tem de ter pelo menos 2 minutos.
        created = datetime.fromisoformat(file["createdTime"].replace("Z", "+00:00"))
        if created > two_minutes_ago:
            logger.info("Ficheiro recente, aguarda 2 minutos: %s", filename)
            continue

        if file_id in processed_ids:
            logger.debug("Já processado: %s", filename)
            continue

        logger.info("Novo ficheiro pronto para processar: %s", filename)

        meta = parse_filename(filename)
        if not meta:
            logger.error("Nome de ficheiro inválido, não processo: %s", filename)
            continue

        logger.info(
            "A processar %s (%s - %s)...",
            meta["supermarket"], meta["valid_from"], meta["valid_until"],
        )

        try:
            summary = run_drive_flyer_sync(
                drive_file_id=file_id,
                supermarket_slug=meta["slug"],
                valid_from=meta["valid_from"],
                valid_until=meta["valid_until"],
            )

            # Marca como processado mesmo que o matcher tenha posto alguns por
            # rever — evita reprocessar o mesmo PDF a cada 2 minutos.
            mark_file_processed(file_id, filename, meta["slug"])

            logger.info(
                "✅ %s: %d produtos importados",
                meta["supermarket"], summary.products_matched,
            )
        except Exception as exc:  # noqa: BLE001 — um ficheiro mau não trava os outros
            logger.error("❌ Erro ao processar %s: %s", filename, exc)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    logger.info("Scheduler do Google Drive iniciado")
    logger.info("Verifica a pasta a cada 2 minutos.")
    logger.info("Ficheiros novos aguardam 2 minutos após o upload antes de processar.")

    while True:
        try:
            check_and_process_new_files()
        except Exception as exc:  # noqa: BLE001 — o loop nunca pode morrer
            logger.error("Erro no scheduler: %s", exc)

        logger.info("Próxima verificação em 2 minutos...")
        time.sleep(120)
