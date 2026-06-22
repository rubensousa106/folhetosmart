"""Scraper do Aldi — folheto regional (varia por região do país).

MECANISMO (mais fiável do que navegar dropdowns com Playwright):
o Aldi publica o folheto da semana num URL regional previsível e a página tem
um botão "Descarregar" que aponta para um GetPDF.ashx. Resolvemos a região a
partir do distrito/cidade, montamos o URL da semana atual e seguimos o link.

    distrito/cidade ─► código de região (n/c/l/a/g/ac/md)
    ─► https://www.aldi.pt/folheto/esta-semana-{cod}.html
    ─► <a> "Descarregar" (…/GetPDF.ashx) ─► download do PDF
    ─► pdf_extractor.extract_products_from_pdf("…", "Aldi") ─► produtos

INTEGRAÇÃO: o projeto migrou do Google Drive para o Cloudflare R2. Por isso, em
vez de `upload_to_drive`, usa-se `download_to_r2()`: carrega o PDF no bucket com
o MESMO padrão de nome dos uploads do admin ("Aldi DD-MM-YYYY - DD-MM-YYYY.pdf"),
para que o `drive_producer` o processe com o MESMO extrator (Claude) e a app o
receba na próxima sincronização — sem código de extração/normalização duplicado.

Mantém `AldiScraper`/`NoAldiStoreError` (usados pelo registry SCRAPERS em
pipeline.py/scheduler.py).
"""
from __future__ import annotations

# Em redes com inspeção TLS, confiar no cert store do SO (mesmo padrão do
# drive_producer). Silencioso se o truststore não existir (ex.: CI sem interceção).
try:
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

import datetime as dt
import logging
import os
import re
import unicodedata
from pathlib import Path
from typing import Optional
from urllib.parse import urljoin

from config.settings import settings
from scrapers.base import FlyerWindow
from scrapers.pdf_flyer import PdfFlyerScraper, REALISTIC_UA, flyers_dir

logger = logging.getLogger(__name__)

ALDI_BASE = "https://www.aldi.pt"


class NoAldiStoreError(RuntimeError):
    """Não existe folheto Aldi para a região configurada (sem loja na zona)."""


# --- mapa região --------------------------------------------------------------
# Código de região do Aldi por DISTRITO (e algumas cidades comuns). Chaves
# normalizadas (minúsculas, sem acentos) — ver `_norm`.
#   n  Norte · c  Centro · l  Lisboa · a  Alentejo · g  Algarve
#   ac Açores · md Madeira
REGION_BY_AREA: dict[str, str] = {
    # Norte
    "porto": "n", "braga": "n", "viana do castelo": "n", "vila real": "n",
    "braganca": "n", "aveiro": "n",
    # Centro
    "coimbra": "c", "leiria": "c", "castelo branco": "c", "guarda": "c",
    # Lisboa (e Vale do Tejo)
    "lisboa": "l", "setubal": "l", "santarem": "l",
    # Alentejo
    "evora": "a", "beja": "a", "portalegre": "a",
    # Algarve
    "faro": "g", "portimao": "g", "lagos": "g",
    # Açores
    "ponta delgada": "ac", "angra do heroismo": "ac", "acores": "ac",
    # Madeira
    "funchal": "md", "madeira": "md",
}


def _norm(value: str) -> str:
    """minúsculas + sem acentos (para casar 'Bragança' com 'braganca')."""
    nfkd = unicodedata.normalize("NFKD", value.strip().lower())
    return "".join(c for c in nfkd if not unicodedata.combining(c))


def region_code(district: str | None = None, city: str | None = None) -> str:
    """Código de região Aldi a partir do distrito/cidade (cai nas settings).

    Levanta NoAldiStoreError se a zona não estiver mapeada (ex.: sem loja Aldi).
    """
    for value in (district, city, settings.aldi_district, settings.aldi_city):
        if value and _norm(value) in REGION_BY_AREA:
            return REGION_BY_AREA[_norm(value)]
    raise NoAldiStoreError(
        f"Aldi: região desconhecida (distrito='{district or settings.aldi_district}', "
        f"cidade='{city or settings.aldi_city}')"
    )


def current_week() -> int:
    """Número da semana ISO atual (1–53)."""
    return dt.date.today().isocalendar()[1]


def week_flyer_url(code: str) -> str:
    """URL da página do folheto da semana atual para a região `code`."""
    return f"{ALDI_BASE}/folheto/esta-semana-{code}.html"


# --- HTTP (honra a flag de inspeção TLS, como o resto do projeto) -------------
def _verify_tls() -> bool:
    return os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"


def _session():
    import requests  # lazy
    s = requests.Session()
    s.headers.update({"User-Agent": REALISTIC_UA, "Accept-Language": "pt-PT,pt;q=0.9"})
    return s


def _find_pdf_url(html: str, base_url: str) -> Optional[str]:
    """Link do PDF na página (botão "Descarregar" / GetPDF.ashx / *.pdf).

    Usa BeautifulSoup quando disponível; caso contrário, regex (sem dependência).
    """
    hrefs: list[str] = []
    try:
        from bs4 import BeautifulSoup  # lazy/opcional
        soup = BeautifulSoup(html, "html.parser")
        for a in soup.find_all("a", href=True):
            href = a["href"]
            text = (a.get_text() or "").strip().lower()
            if (
                "getpdf" in href.lower()
                or re.search(r"\.pdf(\?|$)", href, re.IGNORECASE)
                or "descarregar" in text
                or "download" in text
            ):
                hrefs.append(href)
    except Exception:  # noqa: BLE001 — bs4 ausente ou HTML estranho: regex
        hrefs = re.findall(
            r'href=["\']([^"\']*(?:GetPDF\.ashx|\.pdf)[^"\']*)["\']', html, re.IGNORECASE
        )

    if not hrefs:
        return None
    # Prefere o GetPDF.ashx (o "Descarregar" oficial) ao primeiro .pdf qualquer.
    hrefs.sort(key=lambda h: 0 if "getpdf" in h.lower() else 1)
    return urljoin(base_url, hrefs[0])


def _resolve_pdf_url(district: str | None, city: str | None) -> str:
    code = region_code(district, city)
    page_url = week_flyer_url(code)
    logger.info("Aldi: região '%s' (semana %d) — %s", code, current_week(), page_url)
    resp = _session().get(page_url, verify=_verify_tls(), timeout=30)
    resp.raise_for_status()
    pdf_url = _find_pdf_url(resp.text, page_url)
    if not pdf_url:
        raise RuntimeError(f"Aldi: sem link de PDF na página {page_url}")
    return pdf_url


def download_pdf(district: str | None = None, city: str | None = None) -> Path:
    """Descarrega o PDF do folheto Aldi da semana atual para data/folhetos/."""
    pdf_url = _resolve_pdf_url(district, city)
    logger.info("Aldi: a descarregar PDF — %s", pdf_url)
    resp = _session().get(pdf_url, verify=_verify_tls(), timeout=120)
    resp.raise_for_status()
    body = resp.content
    if not body[:4] == b"%PDF":
        raise RuntimeError(f"Aldi: o URL não devolveu um PDF — {pdf_url}")
    code = region_code(district, city)
    target = flyers_dir() / f"aldi_{code}_{dt.date.today().isoformat()}.pdf"
    target.write_bytes(body)
    logger.info("Aldi: PDF guardado em %s (%d bytes)", target, len(body))
    return target


def fetch_products(district: str | None = None, city: str | None = None) -> list[dict]:
    """Descarrega o folheto e extrai os produtos com o extrator do projeto."""
    pdf = download_pdf(district, city)
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui gasta IA
    produtos = extract_products_from_pdf(str(pdf), "Aldi")
    logger.info("Aldi: %d produtos extraídos", len(produtos))
    return produtos


def download_to_r2(district: str | None = None, city: str | None = None) -> str:
    """Descarrega o folheto e carrega-o no R2 com o padrão de nome do admin.

    Devolve a key no bucket. O `drive_producer` apanha-o na próxima passagem e
    extrai/normaliza com o MESMO pipeline dos uploads manuais.
    """
    from storage.r2_storage import r2_storage  # lazy
    if not r2_storage.is_configured():
        raise RuntimeError("Aldi: R2 não configurado (R2_ENDPOINT/BUCKET/keys)")
    pdf = download_pdf(district, city)
    w: FlyerWindow = FlyerWindow.current_week()
    key = f"Aldi {w.valid_from:%d-%m-%Y} - {w.valid_until:%d-%m-%Y}.pdf"
    r2_storage.upload_pdf(key, pdf)
    logger.info("Aldi: folheto no R2 como '%s'", key)
    return key


# --- compatibilidade com o framework de scrapers (registry SCRAPERS) ----------
class AldiScraper(PdfFlyerScraper):
    """Adapta o mecanismo acima ao contrato BaseScraper/PdfFlyerScraper.

    `download_flyer` ignora a navegação Playwright e usa o URL regional direto
    (mais robusto). Sem região mapeada → NoAldiStoreError (o pipeline marca
    flyer_available=false).
    """

    slug = "aldi"
    name = "Aldi"
    start_url = ALDI_BASE + "/folhetosaldi.html"

    def __init__(self, *, district: str | None = None, city: str | None = None, **kwargs):
        super().__init__(**kwargs)
        self.district = district or settings.aldi_district
        self.city = city or settings.aldi_city

    def download_flyer(self, page, context) -> Optional[Path]:
        # Resolve e descarrega sem depender dos dropdowns do site.
        return download_pdf(self.district, self.city)


if __name__ == "__main__":  # teste manual: python scrapers/aldi.py --district Porto
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description="Folheto Aldi da semana atual")
    ap.add_argument("--district")
    ap.add_argument("--city")
    ap.add_argument("--r2", action="store_true", help="carrega o PDF no R2 (pipeline)")
    ap.add_argument("--url-only", action="store_true", help="só resolve o URL do PDF")
    args = ap.parse_args()

    if args.url_only:
        print(_resolve_pdf_url(args.district, args.city))
    elif args.r2:
        print("R2 key:", download_to_r2(args.district, args.city))
    else:
        prods = fetch_products(args.district, args.city)
        print(f"{len(prods)} produtos extraídos")
