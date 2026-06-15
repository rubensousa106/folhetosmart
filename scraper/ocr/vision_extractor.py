"""Extração de produtos de folhetos gráficos via Claude (visão multimodal).

Os folhetos de supermercado são documentos gráficos densos — preços grandes
estilizados sobre imagens, várias colunas, texto legal. O Tesseract puro não
os segmenta de forma fiável (lê as colunas na horizontal e perde os preços).

A Claude API com visão lê o layout como um humano: associa nome, preço
promocional e preço original mesmo em colunas, e ignora o texto institucional.
Enviamos cada página como imagem e pedimos os produtos em JSON.

Se a Claude API falhar, o `pipeline` cai para o OCR Tesseract (flyer_parser).
"""
from __future__ import annotations

import base64
import datetime as dt
import io
import logging
from pathlib import Path
from typing import Optional, Union

import anthropic

from ai_matcher.claude_matcher import _extract_json
from ai_matcher.models import RawProduct
from config.settings import settings

logger = logging.getLogger(__name__)

# Lado maior da imagem enviada ao Claude. ~1600px mantém os preços legíveis
# sem gastar tokens a mais (recomendação de visão: <= ~1568px no lado maior).
MAX_IMAGE_SIDE = 1600
VISION_DPI = 200

VISION_PROMPT = """\
Esta imagem é uma página de um folheto de promoções de um supermercado \
português. Extrai TODOS os produtos com preço visíveis nesta página.

Para cada produto devolve:
- "name": nome completo do produto (marca + descrição + gramagem/volume se houver)
- "price": preço promocional em euros (número, ex.: 11.99)
- "original_price": preço original/riscado em euros se existir, senão null
- "unit": "kg" | "un" | "embalagem" se visível, senão null
- "promotion_label": texto da promoção se houver ("Apenas", "33% Desconto \
Direto", "Sobre PVPR", "Leve X pague Y", ...), senão null

Regras:
- Ignora texto institucional/legal (condições do cartão, cupões, rodapés).
- Não inventes produtos: só os que têm preço claramente visível.
- O preço promocional é o número grande em destaque; o original é o menor \
riscado (maior que o promocional).

Responde APENAS com JSON válido:
{"products": [{"name": "...", "price": 0.00, "original_price": null, \
"unit": null, "promotion_label": null}]}
"""


def _client() -> anthropic.Anthropic:
    http_client = None
    if settings.insecure_tls:
        import httpx

        http_client = httpx.Client(verify=False, timeout=120.0)
    return anthropic.Anthropic(
        api_key=settings.anthropic_api_key or None, http_client=http_client
    )


def extract_products_from_pdf_vision(
    pdf_path: Union[str, Path],
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    """Extrai produtos de um folheto PDF usando a Claude API com visão."""
    from pdf2image import convert_from_path  # lazy

    pages = convert_from_path(str(pdf_path), dpi=VISION_DPI, fmt="png")
    logger.info("Visão: %s — %d páginas", pdf_path, len(pages))
    return _extract_from_images(pages, supermarket, source_url)


def extract_products_from_images_vision(
    image_paths,
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    from PIL import Image  # lazy

    images = [Image.open(p) for p in image_paths]
    return _extract_from_images(images, supermarket, source_url)


# --- Núcleo -----------------------------------------------------------------
def _extract_from_images(images, supermarket, source_url) -> list[RawProduct]:
    client = _client()
    valid_from, valid_until = None, None
    products: list[RawProduct] = []
    seen: set[str] = set()

    for i, image in enumerate(images, start=1):
        try:
            raw = _ask_page(client, image)
        except Exception as exc:  # noqa: BLE001 — uma página não trava o folheto
            logger.warning("Visão: página %d falhou: %s", i, exc)
            continue

        # A validade pode vir no topo/rodapé de qualquer página.
        if valid_from is None and raw.get("valid_from"):
            valid_from, valid_until = _parse_dates(raw)

        for item in raw.get("products", []):
            product = _to_rawproduct(item, supermarket, source_url, valid_from, valid_until)
            if product is None:
                continue
            key = f"{product.raw_name.lower()}::{product.price}"
            if key in seen:
                continue
            seen.add(key)
            products.append(product)
        logger.info("Visão: página %d -> %d produtos acumulados", i, len(products))

    logger.info("Visão: %d produtos extraídos (%s)", len(products), supermarket)
    return products


def _ask_page(client: anthropic.Anthropic, pil_image) -> dict:
    b64 = _png_b64(_resize(pil_image))
    message = client.messages.create(
        model=settings.anthropic_model,
        max_tokens=4096,
        messages=[{
            "role": "user",
            "content": [
                {"type": "image", "source": {
                    "type": "base64", "media_type": "image/png", "data": b64}},
                {"type": "text", "text": VISION_PROMPT},
            ],
        }],
    )
    text = "".join(b.text for b in message.content if b.type == "text")
    return _extract_json(text) or {}


def _to_rawproduct(
    item: dict,
    supermarket: str,
    source_url: Optional[str],
    valid_from: Optional[dt.date],
    valid_until: Optional[dt.date],
) -> Optional[RawProduct]:
    name = (item.get("name") or "").strip()
    if len(name) < 3:
        return None
    try:
        price = float(item["price"])
    except (KeyError, TypeError, ValueError):
        return None
    if not (0.10 <= price <= 999.0):
        return None

    original = item.get("original_price")
    try:
        original = float(original) if original is not None else None
    except (TypeError, ValueError):
        original = None
    if original is not None and original <= price:
        original = None

    label = item.get("promotion_label")
    return RawProduct(
        raw_name=name,
        price=round(price, 2),
        supermarket=supermarket,
        original_price=round(original, 2) if original else None,
        is_promotion=original is not None or bool(label),
        promotion_label=label,
        source_url=source_url,
        valid_from=valid_from,
        valid_until=valid_until,
    )


def _resize(pil_image):
    w, h = pil_image.size
    scale = MAX_IMAGE_SIDE / max(w, h)
    if scale >= 1:
        return pil_image.convert("RGB")
    return pil_image.convert("RGB").resize((int(w * scale), int(h * scale)))


def _png_b64(pil_image) -> str:
    buf = io.BytesIO()
    pil_image.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


def _parse_dates(raw: dict) -> tuple[Optional[dt.date], Optional[dt.date]]:
    def parse(value):
        try:
            return dt.date.fromisoformat(value)
        except (TypeError, ValueError):
            return None
    return parse(raw.get("valid_from")), parse(raw.get("valid_until"))
