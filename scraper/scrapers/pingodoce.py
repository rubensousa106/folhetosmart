"""Scraper do Pingo Doce — folheto semanal em PDF.

Navegação (instruções reais):
1. Abre https://www.pingodoce.pt/folhetos/
2. A página agrupa folhetos por zona: Continente / Madeira / Açores
3. Usa SEMPRE a zona "Continente" (a mais abrangente)
4. Clica em "Ver o folheto" ao lado do folheto semanal atual
5. Descarrega o PDF
6. OCR com pdf2image + pytesseract (PdfFlyerScraper)
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from scrapers.pdf_flyer import PdfFlyerScraper

logger = logging.getLogger(__name__)


class PingoDoceScraper(PdfFlyerScraper):
    slug = "pingo-doce"
    name = "Pingo Doce"
    start_url = "https://www.pingodoce.pt/folhetos/"

    def download_flyer(self, page, context) -> Optional[Path]:
        # 2-3. Seleciona a zona "Continente" (tab/filtro de região).
        for selector in (
            "button:has-text('Continente')",
            "a:has-text('Continente')",
            "[role='tab']:has-text('Continente')",
            "label:has-text('Continente')",
        ):
            try:
                page.locator(selector).first.click(timeout=4_000)
                page.wait_for_timeout(1_200)
                break
            except Exception:
                continue
        # Se nenhum seletor de zona existir, a página já mostra a zona
        # Continente por omissão — seguimos em frente.

        # 4. "Ver o folheto" do folheto semanal atual (o primeiro da zona).
        ver_folheto = page.locator(
            "a:has-text('Ver o folheto'), button:has-text('Ver o folheto'), "
            "a:has-text('Ver folheto'), button:has-text('Ver folheto')"
        ).first
        ver_folheto.wait_for(timeout=20_000)

        # O link pode ser diretamente o PDF…
        href = ver_folheto.get_attribute("href")
        if href and ".pdf" in href.lower():
            return self._download_url(page, href)

        # …ou abrir o leitor: clica (com expect_download como atalho).
        try:
            with page.expect_download(timeout=10_000) as dl:
                ver_folheto.click()
            return self._save_download(dl.value)
        except Exception:
            page.wait_for_load_state("domcontentloaded")
            page.wait_for_timeout(2_000)

        # 5. Dentro do leitor: procura o link/botão do PDF.
        pdf_href = self._first_pdf_href(page)
        if pdf_href:
            return self._download_url(page, pdf_href)
        for selector in (
            "a:has-text('Download')",
            "button:has-text('Download')",
            "a:has-text('Descarregar')",
        ):
            try:
                with page.expect_download(timeout=8_000) as dl:
                    page.locator(selector).first.click()
                return self._save_download(dl.value)
            except Exception:
                continue
        logger.info("Pingo Doce: sem PDF descarregável — fallback para screenshots")
        return None
