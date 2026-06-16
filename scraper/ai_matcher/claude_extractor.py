"""Extração de produtos de um folheto PDF via Claude (API de documentos).

Fluxo (só ADMIN, 1× por PDF — nunca por utilizador):
  Google Drive --(em memória)--> Claude (PDF nativo) --> lista de produtos.

O PDF é descarregado do Drive para memória (BytesIO) e enviado ao Claude como
bloco `document` base64 — NUNCA é guardado em disco do servidor.

O modelo vem de `settings.anthropic_model` (env `ANTHROPIC_MODEL`, por omissão
`claude-sonnet-4-6`). NÃO se fixa um id de modelo no código: o projeto já foi
mordido por isso (o `claude-sonnet-4-20250514` original foi reformado e dava
404), por isso o modelo é sempre configurável por variável de ambiente.
"""
from __future__ import annotations

import base64
import io
import json
import logging
import re
from typing import Optional

import anthropic

from config.settings import settings

logger = logging.getLogger(__name__)

# Folga suficiente para um folheto com dezenas de produtos em JSON (mantém-se
# abaixo do timeout HTTP do SDK por ser uma chamada não-streaming).
MAX_TOKENS = 16_000


def process_flyer_from_drive(
    drive_file_id: str,
    supermarket: str,
    valid_from: str,
    valid_until: str,
) -> list[dict]:
    """Descarrega o PDF do Drive (em memória) e extrai os produtos com IA."""
    pdf_bytes = download_from_drive(drive_file_id)
    products = extract_products_from_pdf(pdf_bytes, supermarket, valid_from, valid_until)
    logger.info("Claude extraiu %d produtos do %s", len(products), supermarket)
    return products


def extract_products_from_pdf(
    pdf_bytes: bytes,
    supermarket: str,
    valid_from: str,
    valid_until: str,
) -> list[dict]:
    """Envia o PDF (base64) ao Claude e devolve a lista de produtos."""
    pdf_base64 = base64.standard_b64encode(pdf_bytes).decode("utf-8")

    client = _anthropic_client()
    message = client.messages.create(
        model=settings.anthropic_model,
        max_tokens=MAX_TOKENS,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "document",
                        "source": {
                            "type": "base64",
                            "media_type": "application/pdf",
                            "data": pdf_base64,
                        },
                    },
                    {"type": "text", "text": _build_prompt(supermarket, valid_from, valid_until)},
                ],
            }
        ],
    )

    text = "".join(b.text for b in message.content if b.type == "text").strip()
    return _parse_products(text)


def download_from_drive(file_id: str) -> bytes:
    """Descarrega um ficheiro do Drive para memória (BytesIO) — sem tocar no disco.

    Reutiliza o cliente Drive partilhado (`gdrive_storage`), que já trata das
    credenciais (JSON inline OU ficheiro da service account).
    """
    from googleapiclient.http import MediaIoBaseDownload  # lazy

    from storage.gdrive_storage import drive_storage

    buffer = io.BytesIO()
    request = drive_storage.service.files().get_media(fileId=file_id)
    downloader = MediaIoBaseDownload(buffer, request)
    done = False
    while not done:
        _status, done = downloader.next_chunk()
    return buffer.getvalue()


# --- internos ---------------------------------------------------------------
def _anthropic_client() -> anthropic.Anthropic:
    http_client = None
    if settings.insecure_tls:
        # Redes com inspeção TLS (apenas dev): desliga a verificação de certificados.
        import httpx  # transitivo do SDK anthropic

        http_client = httpx.Client(verify=False, timeout=120.0)
    return anthropic.Anthropic(
        api_key=settings.anthropic_api_key or None,
        http_client=http_client,
    )


def _build_prompt(supermarket: str, valid_from: str, valid_until: str) -> str:
    return f"""Analisa este folheto do {supermarket} e extrai TODOS os produtos em promoção.
Período de validade: {valid_from} a {valid_until}.

Para cada produto devolve um objeto JSON com:
{{
  "raw_name": "nome completo como aparece no folheto",
  "brand": "marca se identificável ou null",
  "weight_or_volume": "ex: 150g, 1L ou null",
  "price": 1.99,
  "original_price": 2.99,
  "is_promotion": true,
  "promotion_label": "Apenas / 30% Desconto / null",
  "unit": "KG / UNID. / null",
  "valid_from": "{valid_from}",
  "valid_until": "{valid_until}"
}}

Responde APENAS com um array JSON válido, sem texto antes ou depois.
Extrai absolutamente TODOS os produtos sem exceção."""


_JSON_FENCE = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL)


def _parse_products(text: str) -> list[dict]:
    """Extrai o array JSON da resposta, tolerante a ```fences``` e ruído."""
    payload = _extract_json_array(text)
    if payload is None:
        logger.warning("Resposta do Claude sem array JSON válido")
        return []
    if not isinstance(payload, list):
        return []
    return [p for p in payload if isinstance(p, dict)]


def _extract_json_array(text: str) -> Optional[list]:
    if not text:
        return None
    fence = _JSON_FENCE.search(text)
    candidate = fence.group(1).strip() if fence else text
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        pass
    start = candidate.find("[")
    end = candidate.rfind("]")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(candidate[start : end + 1])
        except json.JSONDecodeError:
            return None
    return None
