"""Scraper do Continente — folheto semanal em PDF.

Navegação (instruções reais):
1. Abre https://www.continente.pt/folhetos/
2. Localiza o cartão "Folheto Semanal" (por baixo tem "Ver produtos")
3. Clica em "Ver produtos"
4. Descarrega o PDF do folheto semanal
5. OCR com pdf2image + pytesseract (PdfFlyerScraper)
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from scrapers.pdf_flyer import PdfFlyerScraper

logger = logging.getLogger(__name__)


class ContinenteScraper(PdfFlyerScraper):
    slug = "continente"
    name = "Continente"
    start_url = "https://www.continente.pt/folhetos/"

    def download_flyer(self, page, context) -> Optional[Path]:
        # 2-3. Cartão "Folheto Semanal" -> botão "Ver produtos" por baixo.
        weekly = page.locator(
            "section:has-text('Folheto Semanal'), "
            "article:has-text('Folheto Semanal'), "
            "div[class*='folheto']:has-text('Folheto Semanal')"
        ).first
        weekly.wait_for(timeout=20_000)

        ver_produtos = weekly.locator(
            "a:has-text('Ver produtos'), button:has-text('Ver produtos')"
        ).first
        try:
            ver_produtos.click(timeout=10_000)
        except Exception:
            # Fallback: botão "Ver produtos" em qualquer parte da página
            # (o primeiro pertence ao folheto semanal, o destaque da página).
            page.locator(
                "a:has-text('Ver produtos'), button:has-text('Ver produtos')"
            ).first.click(timeout=10_000)
        page.wait_for_load_state("domcontentloaded")
        page.wait_for_timeout(2_000)

        # 4. Na vista do folheto: link/botão de download do PDF.
        for selector in (
            "a[href*='.pdf']",
            "a:has-text('Download')",
            "button:has-text('Download')",
            "a:has-text('Descarregar')",
            "button:has-text('Descarregar')",
            "[class*='download']",
        ):
            locator = page.locator(selector).first
            try:
                locator.wait_for(timeout=4_000)
            except Exception:
                continue
            href = locator.get_attribute("href")
            if href and ".pdf" in href.lower():
                return self._download_url(page, href)
            try:
                with page.expect_download(timeout=15_000) as dl:
                    locator.click()
                return self._save_download(dl.value)
            except Exception:
                continue

        # Último recurso: qualquer link .pdf presente na página.
        href = self._first_pdf_href(page)
        if href:
            return self._download_url(page, href)
        # Sem PDF -> a base faz fallback para screenshots do viewer + OCR.
        logger.info("Continente: sem PDF descarregável — fallback para screenshots")
        return None
