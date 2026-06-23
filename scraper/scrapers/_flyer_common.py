"""Helpers partilhados pelos scrapers cujo folheto segue o padrão
`folhetos.<loja>/AAAA/.../`  +  `/GetPDF.ashx` (Aldi, Pingo Doce, …).

Mantém um único sítio para: sessão HTTP (com a flag de inspeção TLS), pasta de
downloads, descoberta do link do PDF a partir da página, download do PDF e
upload para o R2. Evita duplicar isto em cada scraper.
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import re
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


def _load_env() -> None:
    """Carrega o .env (raiz do projeto) para as R2_*/ANTHROPIC ao correr os scrapers
    como scripts. Em Docker/Actions as variáveis já vêm do ambiente (setdefault)."""
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # scraper/
    for path in (os.path.join(root, ".env"), os.path.join(root, "..", ".env")):
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        os.environ.setdefault(k.strip(), v.strip())
            return


_load_env()

# User-agent de um Chrome real (o headless por omissão é detetado por anti-bot).
REALISTIC_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)


def verify_tls() -> bool:
    """False em redes com inspeção TLS (FOLHETO_INSECURE_TLS=1, só dev)."""
    return os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"


def session():
    import requests  # lazy
    s = requests.Session()
    s.headers.update({"User-Agent": REALISTIC_UA, "Accept-Language": "pt-PT,pt;q=0.9"})
    return s


def flyers_dir() -> Path:
    base = os.getenv("SCRAPER_DATA_DIR", "data")
    try:
        from config.settings import settings  # lazy
        base = settings.data_dir
    except Exception:  # noqa: BLE001
        pass
    path = Path(base) / "folhetos"
    path.mkdir(parents=True, exist_ok=True)
    return path


def getpdf_url_from_page(html: str, flyer_host: str, prefer: str = "") -> Optional[str]:
    """Encontra a base do folheto em `flyer_host` (ex.: folhetos.pingodoce.pt/…/)
    e devolve `{base}/GetPDF.ashx`. `prefer` (minúsculas) prioriza as bases que o
    contenham (ex.: 'poupe-esta-semana/continental')."""
    pat = re.compile(r"https?://" + re.escape(flyer_host) + r"/[A-Za-z0-9_./-]+")
    bases = sorted(set(pat.findall(html)))
    if not bases:
        return None
    if prefer:
        bases = [b for b in bases if prefer in b.lower()] or bases
    base = sorted(bases, key=len, reverse=True)[0].rstrip("/")
    if base.lower().endswith(("getpdf.ashx", ".pdf")):
        return base
    return base + "/GetPDF.ashx"


def download_pdf(pdf_url: str, name: str) -> Path:
    """Descarrega um PDF (verifica os magic bytes %PDF) para data/folhetos/."""
    resp = session().get(pdf_url, verify=verify_tls(), timeout=120)
    resp.raise_for_status()
    body = resp.content
    if body[:4] != b"%PDF":
        raise RuntimeError(f"{name}: o URL não devolveu um PDF — {pdf_url}")
    target = flyers_dir() / f"{name}_{dt.date.today().isoformat()}.pdf"
    target.write_bytes(body)
    logger.info("%s: PDF guardado em %s (%d bytes)", name, target, len(body))
    return target


def week_window() -> tuple[dt.date, dt.date]:
    """Segunda a domingo da semana atual (janela de validade aproximada)."""
    today = dt.date.today()
    monday = today - dt.timedelta(days=today.weekday())
    return monday, monday + dt.timedelta(days=6)


def upload_to_r2(path: Path, key: str) -> str:
    """Carrega o PDF no R2 (para o drive_producer o processar). Devolve a key."""
    from storage.r2_storage import r2_storage  # lazy
    if not r2_storage.is_configured():
        raise RuntimeError("R2 não configurado (R2_ENDPOINT/BUCKET/keys)")
    r2_storage.upload_pdf(key, path)
    logger.info("Folheto no R2 como '%s'", key)
    return key
