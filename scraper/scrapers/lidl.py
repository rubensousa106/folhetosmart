"""Scraper do Lidl — folheto digital com produtos em HTML (sem PDF).

Navegação (instruções reais):
1. Abre https://www.lidl.pt/l/pt/folhetos
2. Descobre o folheto da semana atual (o primeiro da lista)
3. Itera TODAS as páginas (/page/1, /page/2, ...) até não haver produtos
4. Extrai produtos, preços e datas de validade

O `parse()` é puro (recebe HTML, devolve produtos) e usa listas de seletores
candidatos — cobre a estrutura atual do site e a fixture de testes.
"""
from __future__ import annotations

import datetime as dt
import logging
import re
import time
from typing import Optional

from parsel import Selector

from ai_matcher.models import RawProduct
from ocr.extractor import extract_flyer_products_images
# Import absoluto (não relativo) para o módulo também poder ser executado
# diretamente como script (ex.: python scrapers/lidl.py com PYTHONPATH=/app).
from scrapers.base import BaseScraper
from scrapers.pdf_flyer import (
    MAX_ATTEMPTS,
    RETRY_DELAY_S,
    accept_cookies,
    flyers_dir,
    launch_realistic_context,
)

logger = logging.getLogger(__name__)

FLYERS_URL = "https://www.lidl.pt/l/pt/folhetos"
MAX_PAGES = 80

# "1,39 €" / "1.39€" / "0,89 EUR" -> 1.39
_PRICE_RE = re.compile(r"(\d+)[.,](\d{2})")
# "-30%" -> 30
_DISCOUNT_RE = re.compile(r"-?\s*(\d{1,2})\s*%")
# Validade no cabeçalho do folheto: "05.06. - 11.06." / "05.06 a 11.06"
_VALIDITY_RE = re.compile(
    r"(\d{1,2})\.(\d{1,2})\.?\s*(?:-|–|a|até)\s*(\d{1,2})\.(\d{1,2})\.?"
)

# Cartões de produto: estrutura atual do Lidl + classes antigas + fixture.
_CARD_SELECTORS = [
    "article[data-grid-box]",
    ".product-grid-box",
    "article.product-grid-box",
    "li[class*='product']",
    ".product",
]
_NAME_SELECTORS = [
    ".product-grid-box__title",
    "[class*='grid-box__title']",
    ".product__title",
    "h3", "h2",
]
_PRICE_SELECTORS = [
    ".m-price__price",
    "[class*='price__price']",
    ".product__price",
    ".price",
    "[class*='price']",
]
_OLD_PRICE_SELECTORS = [
    ".m-price__rrp",
    "[class*='strikethrough']",
    ".product__old-price",
    "del",
]
_DISCOUNT_SELECTORS = [
    ".m-price__discount",
    "[class*='discount']",
    ".product__discount",
    ".badge",
]


class LidlScraper(BaseScraper):
    slug = "lidl"
    name = "Lidl"

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.flyer_url: Optional[str] = None
        self.detected_validity: Optional[tuple[dt.date, dt.date]] = None

    # -- fluxo multi-página ----------------------------------------------------
    def scrape(self) -> list[RawProduct]:
        """Descobre o folheto da semana e percorre todas as páginas.

        Estratégia 1 (rápida): parsear cartões de produto em HTML.
        Estratégia 2 (fallback): se não houver cartões — o caso real, pois o
        folheto é um viewer de imagens — faz screenshot de cada página e OCR.
        Tudo dentro de um retry 3x com contexto anti-bot.
        """
        from playwright.sync_api import sync_playwright  # lazy

        html_products: list[RawProduct] = []
        screenshots: list = []
        last_error: Optional[Exception] = None

        for attempt in range(1, MAX_ATTEMPTS + 1):
            html_products, screenshots = [], []
            try:
                with sync_playwright() as p:
                    browser, context = launch_realistic_context(p)
                    try:
                        page = context.new_page()
                        logger.info("Lidl: tentativa %d/%d — %s",
                                    attempt, MAX_ATTEMPTS, FLYERS_URL)
                        page.goto(FLYERS_URL, wait_until="networkidle", timeout=60_000)
                        accept_cookies(page)
                        page.wait_for_timeout(2_000)

                        base_url = self._discover_current_flyer(page)
                        logger.info("Lidl: folheto da semana em %s", base_url)
                        self._detect_validity(page)

                        stamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
                        last_sig: Optional[int] = None
                        for n in range(1, MAX_PAGES + 1):
                            page.goto(f"{base_url}/page/{n}",
                                      wait_until="networkidle", timeout=60_000)
                            page.wait_for_timeout(1_500)

                            page_products = self.parse(page.content())
                            if page_products:
                                logger.info("Lidl: página %d -> %d produtos (HTML)",
                                            n, len(page_products))
                                html_products.extend(page_products)
                                continue

                            # Sem cartões -> screenshot para OCR.
                            shot = flyers_dir() / f"lidl_{stamp}_page_{n}.png"
                            page.screenshot(path=str(shot), full_page=True)
                            sig = shot.stat().st_size
                            if last_sig is not None and abs(sig - last_sig) < 2_000:
                                shot.unlink(missing_ok=True)
                                logger.info("Lidl: página %d repetida — fim do folheto", n)
                                break
                            last_sig = sig
                            screenshots.append(shot)
                        break  # tentativa bem-sucedida
                    finally:
                        browser.close()
            except Exception as exc:  # noqa: BLE001 — retry
                last_error = exc
                logger.warning("Lidl: tentativa %d falhou: %s", attempt, exc)
                if attempt < MAX_ATTEMPTS:
                    time.sleep(RETRY_DELAY_S)
        else:
            raise RuntimeError(f"Lidl: falhou após {MAX_ATTEMPTS} tentativas — {last_error}")

        # Escolhe a estratégia que produziu dados.
        if html_products:
            products = _dedupe(html_products)
        elif screenshots:
            logger.info("Lidl: %d screenshots — extração por visão/OCR", len(screenshots))
            products = extract_flyer_products_images(
                screenshots, supermarket=self.slug, source_url=self.flyer_url or FLYERS_URL
            )
        else:
            products = []

        if self.detected_validity:
            valid_from, valid_until = self.detected_validity
            for rp in products:
                rp.valid_from = valid_from
                rp.valid_until = valid_until
        logger.info("Lidl: %d produtos extraídos no total", len(products))
        return products

    def _discover_current_flyer(self, page) -> str:
        """Primeiro link de folheto na página de listagem (semana atual)."""
        hrefs: list[str] = page.eval_on_selector_all(
            "a[href]", "els => els.map(e => e.href)"
        )
        for href in hrefs:
            if re.search(r"/folhetos?/", href) and href.rstrip("/") != FLYERS_URL:
                # Normaliza: remove sufixo /page/N e âncoras.
                clean = re.sub(r"/page/\d+.*$", "", href.split("#")[0].split("?")[0])
                self.flyer_url = clean.rstrip("/")
                return self.flyer_url
        raise RuntimeError("Lidl: não encontrei nenhum folheto na listagem")

    def _detect_validity(self, page) -> None:
        m = _VALIDITY_RE.search(page.inner_text("body")[:4000])
        if not m:
            return
        d1, m1, d2, m2 = (int(g) for g in m.groups())
        year = dt.date.today().year
        try:
            start = dt.date(year, m1, d1)
            end = dt.date(year, m2, d2)
            if end < start:  # vira o ano (ex.: 30.12 - 05.01)
                end = dt.date(year + 1, m2, d2)
            self.detected_validity = (start, end)
            logger.info("Lidl: validade do folheto %s a %s", start, end)
        except ValueError:
            pass  # datas ilegíveis — usa a janela semanal por omissão

    # -- parse de cartões HTML (puro, testável com fixtures) ------------------
    def parse(self, content: str) -> list[RawProduct]:
        sel = Selector(text=content)
        products: list[RawProduct] = []

        cards = []
        for css in _CARD_SELECTORS:
            cards = sel.css(css)
            if cards:
                break

        for card in cards:
            name = _first_text(card, _NAME_SELECTORS)
            if not name:
                continue
            price = _parse_price(_first_text(card, _PRICE_SELECTORS))
            if price is None:
                continue
            original = _parse_price(_first_text(card, _OLD_PRICE_SELECTORS))
            discount = _first_text(card, _DISCOUNT_SELECTORS)

            products.append(
                RawProduct(
                    raw_name=name.strip(),
                    price=price,
                    supermarket=self.slug,
                    original_price=original,
                    is_promotion=original is not None or bool(discount),
                    promotion_label=_clean_discount(discount),
                    source_url=self.flyer_url or FLYERS_URL,
                )
            )
        return products


# --- Helpers ----------------------------------------------------------------
def _first_text(card: Selector, css_options: list[str]) -> Optional[str]:
    for css in css_options:
        # Junta os nós de texto do elemento (nomes podem vir em <span>s).
        parts = card.css(f"{css} ::text").getall() or card.css(f"{css}::text").getall()
        text = " ".join(t.strip() for t in parts if t.strip())
        if text:
            return text
    return None


def _parse_price(raw: Optional[str]) -> Optional[float]:
    if not raw:
        return None
    m = _PRICE_RE.search(raw)
    if not m:
        return None
    return float(f"{m.group(1)}.{m.group(2)}")


def _clean_discount(raw: Optional[str]) -> Optional[str]:
    if not raw:
        return None
    m = _DISCOUNT_RE.search(raw)
    return f"-{m.group(1)}%" if m else raw.strip()[:100]


def _dedupe(products: list[RawProduct]) -> list[RawProduct]:
    seen: set[str] = set()
    unique: list[RawProduct] = []
    for rp in products:
        key = f"{rp.raw_name.lower()}::{rp.price}"
        if key not in seen:
            seen.add(key)
            unique.append(rp)
    return unique
