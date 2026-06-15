"""Scraper do Intermarché — folheto semanal "Super" em PDF.

Navegação (instruções reais):
1. Abre https://www.intermarche.pt/sign/brands/catalog-page/
2. Localiza APENAS o "Folheto Semanal Super" (ignora Contact e Mini)
3. Clica na imagem do folheto Super para abrir
4. Descarrega o PDF
5. OCR com pdf2image + pytesseract (PdfFlyerScraper)
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from scrapers.pdf_flyer import PdfFlyerScraper

logger = logging.getLogger(__name__)


class IntermarcheScraper(PdfFlyerScraper):
    slug = "intermarche"
    name = "Intermarché"
    start_url = "https://www.intermarche.pt/sign/brands/catalog-page/"

    def download_flyer(self, page, context) -> Optional[Path]:
        # 2. Cartão do "Folheto Semanal Super" — exclui Contact e Mini.
        super_card = page.locator(
            "article:has-text('Super'):not(:has-text('Contact')):not(:has-text('Mini')), "
            "div[class*='catalog']:has-text('Super'):not(:has-text('Contact')):not(:has-text('Mini')), "
            "a:has-text('Super'):not(:has-text('Contact')):not(:has-text('Mini'))"
        ).first
        super_card.wait_for(timeout=20_000)

        # 3. Clica na imagem do folheto Super (ou no próprio cartão).
        target = super_card.locator("img").first
        try:
            target.wait_for(timeout=4_000)
        except Exception:
            target = super_card

        try:
            with page.expect_download(timeout=10_000) as dl:
                target.click()
            return self._save_download(dl.value)
        except Exception:
            page.wait_for_load_state("domcontentloaded")
            page.wait_for_timeout(2_000)

        # 4. No leitor do folheto: link/botão do PDF.
        pdf_href = self._first_pdf_href(page)
        if pdf_href:
            return self._download_url(page, pdf_href)
        for selector in (
            "a:has-text('Download')",
            "button:has-text('Download')",
            "a:has-text('Descarregar')",
            "[class*='download']",
        ):
            try:
                with page.expect_download(timeout=8_000) as dl:
                    page.locator(selector).first.click()
                return self._save_download(dl.value)
            except Exception:
                continue
        logger.info("Intermarché: sem PDF descarregável — fallback para screenshots")
        return None
