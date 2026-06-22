"""Extração de produtos de um folheto, com a melhor estratégia disponível.

1.ª escolha: Claude com visão (lê folhetos gráficos de forma fiável).
Fallback: OCR Tesseract + agrupamento espacial (quando não há chave da API
ou a visão falha).
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional, Sequence, Union

from ai_matcher.models import RawProduct
from config.settings import settings
from ocr.flyer_parser import parse_blocks
# As funções de blocos OCR vivem no pdf_extractor de topo (não em ocr.pdf_extractor,
# que não existe) — corrige o ModuleNotFoundError que quebrava o pacote scrapers.
from pdf_extractor import extract_blocks_from_images, extract_blocks_from_pdf

logger = logging.getLogger(__name__)


def extract_flyer_products_pdf(
    pdf_path: Union[str, Path],
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    if settings.anthropic_api_key:
        try:
            from ocr.vision_extractor import extract_products_from_pdf_vision

            products = extract_products_from_pdf_vision(
                pdf_path, supermarket=supermarket, source_url=source_url
            )
            if products:
                return products
            logger.info("Visão não devolveu produtos — fallback Tesseract")
        except Exception as exc:  # noqa: BLE001
            logger.warning("Visão falhou (%s) — fallback Tesseract", exc)

    blocks = extract_blocks_from_pdf(pdf_path)
    return parse_blocks(blocks, supermarket=supermarket, source_url=source_url)


def extract_flyer_products_images(
    image_paths: Sequence[Union[str, Path]],
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    if settings.anthropic_api_key:
        try:
            from ocr.vision_extractor import extract_products_from_images_vision

            products = extract_products_from_images_vision(
                image_paths, supermarket=supermarket, source_url=source_url
            )
            if products:
                return products
            logger.info("Visão não devolveu produtos — fallback Tesseract")
        except Exception as exc:  # noqa: BLE001
            logger.warning("Visão falhou (%s) — fallback Tesseract", exc)

    blocks = extract_blocks_from_images(image_paths)
    return parse_blocks(blocks, supermarket=supermarket, source_url=source_url)
