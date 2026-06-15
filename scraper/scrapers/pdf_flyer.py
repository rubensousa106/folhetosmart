"""Base comum para scrapers cujo folheto é um PDF ou um viewer de imagens
(Continente, Pingo Doce, Intermarché, Aldi).

Fluxo: navegação Playwright (contexto realista anti-bot, com retry) ->
download do PDF OU screenshots das páginas do viewer -> OCR (300 DPI +
OpenCV) -> parsing heurístico -> RawProduct.

Cada supermercado implementa apenas `download_flyer(page, context)` com a
navegação exata do seu site (devolve o Path do PDF, ou None para usar o
fallback de screenshots).
"""
from __future__ import annotations

import abc
import datetime as dt
import logging
import re
import time
from pathlib import Path
from typing import Optional

from ai_matcher.models import RawProduct
from config.settings import settings
from ocr.extractor import extract_flyer_products_images, extract_flyer_products_pdf
from scrapers.base import BaseScraper

logger = logging.getLogger(__name__)

# User-agent de um Chrome real (não o headless por omissão, que é detetado).
REALISTIC_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

MAX_ATTEMPTS = 3
RETRY_DELAY_S = 5
MAX_VIEWER_PAGES = 10

# Banners de cookies mais comuns nos sites PT.
COOKIE_SELECTORS = [
    "#onetrust-accept-btn-handler",
    "button#onetrust-accept-btn-handler",
    "button:has-text('Aceitar todos')",
    "button:has-text('Aceitar Todos')",
    "button:has-text('Aceitar cookies')",
    "button:has-text('Aceitar')",
    "button:has-text('Concordo')",
    "[id*='accept'][id*='cookie']",
]


def launch_realistic_context(playwright):
    """Browser + contexto que se faz passar por um Chrome real (anti-bot)."""
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(
        user_agent=REALISTIC_UA,
        viewport={"width": 1920, "height": 1080},
        locale="pt-PT",
        timezone_id="Europe/Lisbon",
        accept_downloads=True,
    )
    # Esconde a flag navigator.webdriver (sinal típico de automação).
    context.add_init_script(
        "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
    )
    return browser, context


def accept_cookies(page) -> None:
    for selector in COOKIE_SELECTORS:
        try:
            page.locator(selector).first.click(timeout=2_500)
            page.wait_for_timeout(400)
            return
        except Exception:  # noqa: BLE001 — tenta o próximo seletor
            continue


def flyers_dir() -> Path:
    path = Path(settings.data_dir) / "folhetos"
    path.mkdir(parents=True, exist_ok=True)
    return path


class PdfFlyerScraper(BaseScraper):
    """Scraper de folheto em PDF/viewer. Subclasses implementam `download_flyer`."""

    start_url: str = ""

    def __init__(self, window=None) -> None:
        super().__init__(window=window)
        # Path do PDF descarregado (para o pipeline guardar cópia no Drive).
        self.last_pdf_path: Optional[Path] = None

    # -- fluxo completo (visão/OCR) -------------------------------------------
    def scrape(self) -> list[RawProduct]:
        """Navega (com retry 3x), descarrega o folheto e extrai os produtos.

        Com PDF: extração por visão (Claude) com fallback OCR Tesseract.
        Sem PDF: screenshots das páginas do viewer -> mesma extração.
        """
        from playwright.sync_api import sync_playwright  # lazy

        last_error: Optional[Exception] = None
        for attempt in range(1, MAX_ATTEMPTS + 1):
            try:
                with sync_playwright() as p:
                    browser, context = launch_realistic_context(p)
                    try:
                        page = context.new_page()
                        logger.info("%s: tentativa %d/%d — %s",
                                    self.name, attempt, MAX_ATTEMPTS, self.start_url)
                        page.goto(self.start_url, wait_until="networkidle", timeout=60_000)
                        accept_cookies(page)
                        page.wait_for_timeout(1_500)

                        pdf_path = self.download_flyer(page, context)
                        if pdf_path is not None:
                            logger.info("%s: PDF em %s — a extrair", self.name, pdf_path)
                            self.last_pdf_path = pdf_path
                            return extract_flyer_products_pdf(
                                pdf_path, supermarket=self.slug, source_url=self.start_url)

                        images = self.screenshot_pages(page)
                        if images:
                            logger.info("%s: %d screenshots do viewer — a extrair",
                                        self.name, len(images))
                            return extract_flyer_products_images(
                                images, supermarket=self.slug, source_url=self.start_url)
                        raise RuntimeError(f"{self.name}: sem PDF nem páginas de viewer")
                    finally:
                        browser.close()
            except Exception as exc:  # noqa: BLE001 — retry
                last_error = exc
                logger.warning("%s: tentativa %d falhou: %s", self.name, attempt, exc)
                if attempt < MAX_ATTEMPTS:
                    time.sleep(RETRY_DELAY_S)

        raise RuntimeError(f"{self.name}: falhou após {MAX_ATTEMPTS} tentativas — {last_error}")

    # -- a implementar por supermercado ---------------------------------------
    @abc.abstractmethod
    def download_flyer(self, page, context) -> Optional[Path]:
        """Navega o site e devolve o PDF, ou None para usar screenshots."""

    # -- fallback genérico de screenshots -------------------------------------
    def screenshot_pages(self, page) -> list[Path]:
        """Tira screenshot de cada página do viewer (avança com seta/botão).

        Genérico — funciona quando o viewer reage à seta direita ou tem um
        botão de "próxima página". Para quando o conteúdo deixa de mudar.
        """
        images: list[Path] = []
        stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        last_signature: Optional[int] = None

        for n in range(1, MAX_VIEWER_PAGES + 1):
            page.wait_for_timeout(1_200)
            target = flyers_dir() / f"{self.slug}_{stamp}_page_{n}.png"
            try:
                page.screenshot(path=str(target), full_page=True)
            except Exception:  # noqa: BLE001
                break

            signature = target.stat().st_size
            # Duas páginas idênticas seguidas -> chegámos ao fim.
            if last_signature is not None and abs(signature - last_signature) < 2_000:
                target.unlink(missing_ok=True)
                break
            last_signature = signature
            images.append(target)

            if not self._go_next_page(page):
                break
        return images

    def _go_next_page(self, page) -> bool:
        """Avança para a página seguinte do viewer. True se conseguiu."""
        for selector in (
            "button[aria-label*='próxim']", "button[aria-label*='next']",
            "button[aria-label*='seguinte']", "a[aria-label*='next']",
            ".next", "[class*='next']:not([class*='context'])",
        ):
            try:
                page.locator(selector).first.click(timeout=1_500)
                return True
            except Exception:  # noqa: BLE001
                continue
        try:
            page.keyboard.press("ArrowRight")
            return True
        except Exception:  # noqa: BLE001
            return False

    # -- helpers de download ---------------------------------------------------
    def _save_download(self, download) -> Path:
        stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        target = flyers_dir() / f"{self.slug}_{stamp}.pdf"
        download.save_as(str(target))
        return target

    def _download_url(self, page, url: str) -> Path:
        response = page.request.get(url, timeout=120_000)
        if not response.ok:
            raise RuntimeError(f"{self.name}: download falhou ({response.status}) {url}")
        body = response.body()
        if not body.startswith(b"%PDF"):
            raise RuntimeError(f"{self.name}: o URL não devolveu um PDF: {url}")
        stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        target = flyers_dir() / f"{self.slug}_{stamp}.pdf"
        target.write_bytes(body)
        return target

    def _first_pdf_href(self, page) -> Optional[str]:
        for href in page.eval_on_selector_all("a[href]", "els => els.map(e => e.href)"):
            if re.search(r"\.pdf(\?|$)", href, re.IGNORECASE):
                return href
        return None
