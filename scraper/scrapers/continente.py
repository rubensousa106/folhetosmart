"""Scraper do Continente — Folheto Semanal (lojas continentais / mainland).

Mesmo mecanismo do Aldi/Pingo Doce (verificado ao vivo): a página
https://www.continente.pt/folhetos/ lista os folhetos em
`folhetos.continente.pt/AAAA/MM/semanal-{semana}-xxxx/`, e o PDF descarrega-se em
`{base}/GetPDF.ashx`. Escolhe o semanal principal (exclui as variantes regionais
Madeira/Açores e o formato Bom Dia "cbd").

Integra com R2 ("Continente DD-MM-YYYY - DD-MM-YYYY.pdf"); upload manual = fallback.
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import re
import sys
from pathlib import Path

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

try:  # redes com inspeção TLS
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

from scrapers import _flyer_common as fc  # noqa: E402

logger = logging.getLogger(__name__)

FOLHETOS_PAGE = "https://www.continente.pt/folhetos/"

# Marcadores que NÃO são o folheto semanal principal (variantes/regiões/formatos).
_EXCLUDE = ("mad", "acores", "açores", "cbd", "modelo", "-bd")

_MESES = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4, "maio": 5,
    "junho": 6, "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10,
    "novembro": 11, "dezembro": 12,
}


def _select_semanal(html: str, week: int) -> str:
    """Base do folheto semanal principal da semana atual."""
    links = sorted(set(re.findall(r"https?://folhetos\.continente\.pt/[A-Za-z0-9_./-]+", html)))
    semanais = [l for l in links if re.search(rf"/semanal-{week}\b", l, re.IGNORECASE)]
    principal = [l for l in semanais if not any(x in l.lower() for x in _EXCLUDE)] or semanais
    if not principal:
        raise RuntimeError(f"Continente: sem folheto semanal {week} em {FOLHETOS_PAGE}")
    return sorted(principal, key=len)[0].rstrip("/")  # o mais curto = o principal


def _validity(html: str) -> tuple[dt.date, dt.date]:
    """(início, fim) a partir de 'De DD de Mês até DD de Mês'; cai na semana ISO."""
    m = re.search(r"De\s+(\d{1,2})\s+de\s+(\w+)\s+at[ée]\s+(\d{1,2})\s+de\s+(\w+)", html, re.IGNORECASE)
    if not m:
        return fc.week_window()
    try:
        y = dt.date.today().year
        ini = dt.date(y, _MESES[m.group(2).lower()], int(m.group(1)))
        fim = dt.date(y, _MESES[m.group(4).lower()], int(m.group(3)))
        if fim < ini:
            fim = fim.replace(year=y + 1)  # intervalo a virar o ano
        return ini, fim
    except Exception:  # noqa: BLE001
        return fc.week_window()


def _resolve() -> tuple[str, tuple[dt.date, dt.date]]:
    resp = fc.session().get(FOLHETOS_PAGE, verify=fc.verify_tls(), timeout=30)
    resp.raise_for_status()
    base = _select_semanal(resp.text, dt.date.today().isocalendar()[1])
    logger.info("Continente: %s", base)
    return base, _validity(resp.text)


def resolve_pdf_url() -> str:
    base, _ = _resolve()
    return base + "/GetPDF.ashx"


def download_pdf() -> Path:
    base, _ = _resolve()
    return fc.download_pdf(base + "/GetPDF.ashx", "continente")


def fetch_products() -> list[dict]:
    pdf = download_pdf()
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui gasta IA
    produtos = extract_products_from_pdf(str(pdf), "Continente")
    logger.info("Continente: %d produtos extraídos", len(produtos))
    return produtos


def download_to_r2() -> str:
    base, (ini, fim) = _resolve()
    pdf = fc.download_pdf(base + "/GetPDF.ashx", "continente")
    key = f"Continente {ini:%d-%m-%Y} - {fim:%d-%m-%Y}.pdf"
    return fc.upload_to_r2(pdf, key)


if __name__ == "__main__":  # teste manual
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description="Folheto Semanal do Continente")
    ap.add_argument("--url-only", action="store_true", help="só resolve o URL do PDF")
    ap.add_argument("--r2", action="store_true", help="carrega o PDF no R2")
    args = ap.parse_args()

    if args.url_only:
        print(resolve_pdf_url())
    elif args.r2:
        print("R2 key:", download_to_r2())
    else:
        print(f"{len(fetch_products())} produtos extraídos")
