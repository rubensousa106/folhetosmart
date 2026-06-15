"""Scraper do Aldi — folheto regional em PDF (varia por distrito/cidade).

Navegação (instruções reais):
1. Abre https://www.aldi.pt/folhetosaldi.html
2. Seleciona o distrito do utilizador no dropdown
3. Seleciona a cidade no dropdown seguinte
4. Clica em "Ver folheto" do folheto da SEMANA ATUAL (não o da próxima)
5. Descarrega o PDF
6. OCR com pdf2image + pytesseract (PdfFlyerScraper)
7. Sem loja Aldi na cidade -> NoAldiStoreError (o pipeline regista
   flyer_available = false)

A localização vem de variáveis de ambiente do worker (ALDI_DISTRICT /
ALDI_CITY). Na app, cada utilizador define a sua em Registo/Perfil via
PUT /api/v1/users/me — o worker corre como sistema, sem JWT de utilizador,
pelo que usa a zona configurada no servidor.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from config.settings import settings
from scrapers.pdf_flyer import PdfFlyerScraper

logger = logging.getLogger(__name__)


class NoAldiStoreError(RuntimeError):
    """Não existe loja Aldi na cidade configurada."""


class AldiScraper(PdfFlyerScraper):
    slug = "aldi"
    name = "Aldi"
    start_url = "https://www.aldi.pt/folhetosaldi.html"

    def __init__(self, *, district: str | None = None, city: str | None = None, **kwargs):
        super().__init__(**kwargs)
        self.district = district or settings.aldi_district
        self.city = city or settings.aldi_city

    def download_flyer(self, page, context) -> Optional[Path]:
        if not self.district or not self.city:
            raise NoAldiStoreError(
                "Aldi: distrito/cidade não configurados (ALDI_DISTRICT / ALDI_CITY)"
            )

        # 2. Dropdown do distrito.
        district_select = page.locator("select").first
        district_select.wait_for(timeout=20_000)
        try:
            district_select.select_option(label=self.district)
        except Exception as exc:
            raise NoAldiStoreError(
                f"Aldi: distrito '{self.district}' não existe no site"
            ) from exc
        page.wait_for_timeout(1_500)

        # 3. Dropdown da cidade (aparece/atualiza depois do distrito).
        city_select = page.locator("select").nth(1)
        try:
            city_select.wait_for(timeout=10_000)
            city_select.select_option(label=self.city)
        except Exception as exc:
            # 7. Sem loja nesta cidade.
            raise NoAldiStoreError(
                f"Aldi: sem loja na cidade '{self.city}' ({self.district})"
            ) from exc
        page.wait_for_timeout(1_500)

        # 4. "Ver folheto" do folheto da semana ATUAL — é o primeiro da
        # lista (o seguinte é o da próxima semana).
        ver_folheto = page.locator(
            "a:has-text('Ver folheto'), button:has-text('Ver folheto')"
        ).first
        ver_folheto.wait_for(timeout=15_000)

        href = ver_folheto.get_attribute("href")
        if href and ".pdf" in href.lower():
            return self._download_url(page, href)

        try:
            with page.expect_download(timeout=10_000) as dl:
                ver_folheto.click()
            return self._save_download(dl.value)
        except Exception:
            page.wait_for_load_state("domcontentloaded")
            page.wait_for_timeout(2_000)

        # 5. No leitor: link do PDF.
        pdf_href = self._first_pdf_href(page)
        if pdf_href:
            return self._download_url(page, pdf_href)
        logger.info("Aldi: sem PDF descarregável — fallback para screenshots")
        return None
