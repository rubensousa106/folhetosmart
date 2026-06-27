"""Scraper do Continente — Folheto Semanal (lojas continentais / mainland).

Mesmo mecanismo do Aldi/Pingo Doce (verificado ao vivo): a página
https://www.continente.pt/folhetos/ lista os folhetos em
`folhetos.continente.pt/AAAA/MM/semanal-{semana}-xxxx/`, e o PDF descarrega-se em
`{base}/GetPDF.ashx`. Escolhe o semanal principal (exclui as variantes regionais
Madeira/Açores e o formato Bom Dia "cbd").

Datas de validade: usam a janela da semana (segunda-domingo) — as datas do site
não correspondem ao folheto; upload manual = fallback.
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

os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
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


def _select_semanal(html: str, week: int) -> str:
    """Base do folheto semanal principal da semana atual."""
    links = sorted(set(re.findall(r"https?://folhetos\.continente\.pt/[A-Za-z0-9_./-]+", html)))
    semanais = [l for l in links if re.search(rf"/semanal-{week}\b", l, re.IGNORECASE)]
    principal = [l for l in semanais if not any(x in l.lower() for x in _EXCLUDE)] or semanais
    if not principal:
        raise RuntimeError(f"Continente: sem folheto semanal {week} em {FOLHETOS_PAGE}")
    return sorted(principal, key=len)[0].rstrip("/")  # o mais curto = o principal


def _resolve_base() -> str:
    resp = fc.session().get(FOLHETOS_PAGE, verify=fc.verify_tls(), timeout=30)
    resp.raise_for_status()
    base = _select_semanal(resp.text, dt.date.today().isocalendar()[1])
    logger.info("Continente: %s", base)
    return base


def resolve_pdf_url() -> str:
    return _resolve_base() + "/GetPDF.ashx"


def download_pdf() -> Path:
    return fc.download_pdf(_resolve_base() + "/GetPDF.ashx", "continente")


def fetch_products() -> list[dict]:
    pdf = download_pdf()
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui gasta IA
    produtos = extract_products_from_pdf(str(pdf), "Continente")
    logger.info("Continente: %d produtos extraídos", len(produtos))
    return produtos


def download_to_r2() -> str:
    pdf = download_pdf()
    # Datas REAIS do folheto ("Promoção válida de X a X"): texto e, se for só-imagem,
    # por visão. Recurso ao método antigo (semana segunda-domingo) se não der.
    from pdf_extractor import extract_validity_smart  # lazy
    ini, fim = extract_validity_smart(str(pdf), "Continente")
    if not (ini and fim):
        ini, fim = fc.week_window()
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
