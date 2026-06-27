"""Scraper do Pingo Doce — folheto semanal "Poupe Esta Semana" (continental).

Mesmo mecanismo do Aldi (verificado ao vivo): a página https://www.pingodoce.pt/
folhetos/ aponta para `folhetos.pingodoce.pt/AAAA/poupe-esta-semana/continental-
lojas-grandes/S{semana}/`, e o PDF descarrega-se em `{base}/GetPDF.ashx`.

Integra com R2: `download_to_r2()` carrega o PDF no bucket com o padrão de nome
do admin ("Pingo Doce DD-MM-YYYY - DD-MM-YYYY.pdf"), para o `drive_producer` o
processar com o mesmo extrator. O upload manual continua a ser o fallback.
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import sys
from pathlib import Path

# Permite correr como script (python scrapers/pingodoce.py): põe a raiz no path.
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
try:  # redes com inspeção TLS: confiar no cert store do SO
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

from scrapers import _flyer_common as fc  # noqa: E402

logger = logging.getLogger(__name__)

FOLHETOS_PAGE = "https://www.pingodoce.pt/folhetos/"
FLYER_HOST = "folhetos.pingodoce.pt"


def resolve_pdf_url() -> str:
    """URL do PDF do folheto 'Poupe Esta Semana' (continental) da semana atual."""
    resp = fc.session().get(FOLHETOS_PAGE, verify=fc.verify_tls(), timeout=30)
    resp.raise_for_status()
    url = fc.getpdf_url_from_page(resp.text, FLYER_HOST, prefer="poupe-esta-semana/continental")
    if not url:
        raise RuntimeError(f"Pingo Doce: sem folheto 'poupe-esta-semana' em {FOLHETOS_PAGE}")
    logger.info("Pingo Doce: %s", url)
    return url


def download_pdf() -> Path:
    return fc.download_pdf(resolve_pdf_url(), "pingodoce")


def fetch_products() -> list[dict]:
    pdf = download_pdf()
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui gasta IA
    produtos = extract_products_from_pdf(str(pdf), "Pingo Doce")
    logger.info("Pingo Doce: %d produtos extraídos", len(produtos))
    return produtos


def download_to_r2() -> str:
    """Descarrega o folheto e carrega-o no R2 com o padrão de nome do admin."""
    pdf = download_pdf()
    # O folheto do Pingo Doce é só-imagem: as datas ("Promoção válida de X a X")
    # leem-se por VISÃO. Recurso ao método antigo (semana segunda-domingo) se não der.
    from pdf_extractor import extract_validity_smart  # lazy
    ini, fim = extract_validity_smart(str(pdf), "Pingo Doce")
    if not (ini and fim):
        ini, fim = fc.week_window()
    key = f"Pingo Doce {ini:%d-%m-%Y} - {fim:%d-%m-%Y}.pdf"
    return fc.upload_to_r2(pdf, key)


if __name__ == "__main__":  # teste manual
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description="Folheto Pingo Doce da semana atual")
    ap.add_argument("--url-only", action="store_true", help="só resolve o URL do PDF")
    ap.add_argument("--r2", action="store_true", help="carrega o PDF no R2")
    args = ap.parse_args()

    if args.url_only:
        print(resolve_pdf_url())
    elif args.r2:
        print("R2 key:", download_to_r2())
    else:
        print(f"{len(fetch_products())} produtos extraídos")
