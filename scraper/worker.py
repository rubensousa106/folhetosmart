"""Worker HTTP do scraper.

O backend Spring Boot chama este worker para disparar o pipeline de
scraping + IA matching (POST /run) ou para processar um PDF carregado
manualmente na app (POST /process-pdf, Fix 3). O progresso é escrito em
`sync_runs`, que o backend expõe à app via `GET /api/v1/sync/status`.

Arranque local:
    uvicorn worker:app --host 0.0.0.0 --port 8090
"""
from __future__ import annotations

import logging
import time
from pathlib import Path

from fastapi import BackgroundTasks, FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field

import pipeline
from config.settings import settings

UPLOADS_DIR = Path("/tmp/uploads")

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("worker")

app = FastAPI(title="FolhetoSmart Scraper Worker", version="1.0.0")


class RunRequest(BaseModel):
    supermarkets: list[str] = Field(..., description="slugs a sincronizar")
    sync_run_id: str | None = Field(
        None, description="id do sync_run criado pelo backend"
    )


class RunResponse(BaseModel):
    accepted: bool
    sync_run_id: str | None
    supermarkets: list[str]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": settings.anthropic_model}


@app.post("/run", response_model=RunResponse, status_code=202)
def run(req: RunRequest, background: BackgroundTasks) -> RunResponse:
    """Dispara o pipeline em background e devolve imediatamente (202)."""
    background.add_task(
        pipeline.run_sync, req.supermarkets, sync_run_id=req.sync_run_id
    )
    return RunResponse(
        accepted=True,
        sync_run_id=req.sync_run_id,
        supermarkets=req.supermarkets,
    )


@app.post("/process-pdf", status_code=202)
async def process_pdf(
    background: BackgroundTasks,
    file: UploadFile = File(...),
    supermarket_slug: str = Form(...),
    sync_run_id: str | None = Form(None),
    drive_filename: str | None = Form(None),
) -> dict:
    """Processa um folheto PDF carregado manualmente (Fix 3).

    O backend valida e reenvia o ficheiro; aqui guardamos a nossa cópia e
    corremos OCR + AI matcher em background.

    Se `drive_filename` vier preenchido (upload do ADMIN), guardamos primeiro o
    PDF no Google Drive com esse nome (substituindo se já existir) e devolvemos
    o `drive_file_id` — feito de forma síncrona porque é rápido e o backend
    precisa do id na resposta.
    """
    data = await file.read()
    if not data.startswith(b"%PDF"):
        raise HTTPException(status_code=400, detail="O ficheiro não é um PDF válido.")

    UPLOADS_DIR.mkdir(parents=True, exist_ok=True)
    target = UPLOADS_DIR / f"{supermarket_slug}_{int(time.time())}.pdf"
    target.write_bytes(data)

    drive_file_id = None
    if drive_filename:
        from storage.gdrive_storage import drive_storage

        if drive_storage.is_configured():
            drive_file_id = drive_storage.upload_pdf(target, drive_filename, replace=True)
        else:
            logger.warning("Drive não configurado — folheto não guardado no Drive")

    background.add_task(
        pipeline.run_pdf_sync,
        supermarket_slug,
        str(target),
        sync_run_id=sync_run_id,
    )
    return {
        "accepted": True,
        "sync_run_id": sync_run_id,
        "supermarket": supermarket_slug,
        "pdf": target.name,
        "drive_file_id": drive_file_id,
    }
