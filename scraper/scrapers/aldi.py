"""Scraper do Aldi — folheto regional (7 regiões).

Regiões (decidido c/ Ruben): Norte · Centro · Lisboa · Sul (Alentejo) · Algarve
· Açores · Madeira.

MECANISMO (mais fiável do que navegar dropdowns com Playwright):
o Aldi publica o folheto da semana num URL regional previsível e a página tem
um botão "Descarregar" que aponta para um GetPDF.ashx. Resolvemos a região a
partir do distrito/cidade, montamos o URL da semana atual e seguimos o link.

    distrito/cidade ─► região (norte/…/madeira) ─► slug de URL
    ─► https://www.aldi.pt/folheto/esta-semana-{slug}.html
    ─► <a> "Descarregar" (…/GetPDF.ashx) ─► download do PDF
    ─► pdf_extractor.extract_products_from_pdf("…", "Aldi") ─► produtos

INTEGRAÇÃO: o projeto migrou do Google Drive para o Cloudflare R2. Em vez de
`upload_to_drive`, usa-se `download_*_to_r2()`: carrega cada PDF no bucket com o
padrão de nome dos uploads do admin ("Aldi {Região} DD-MM-YYYY - DD-MM-YYYY.pdf"),
para o `drive_producer` o processar com o MESMO extrator/normalização. Há 1 PDF
por região; cada utilizador vê a sua (filtragem na app pelo distrito de /users/me).

NOTA: o caminho leve (resolver região/URL) NÃO depende do framework de scrapers
nem de Playwright/parsel/OCR — para `python scrapers/aldi.py --url-only` correr
sem instalar nada. O adaptador `AldiScraper` (registry SCRAPERS) é opcional e só
existe se o framework importar.
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import re
import sys
import unicodedata
from pathlib import Path
from typing import Optional
from urllib.parse import urljoin

# Permite correr como script (python scrapers/aldi.py …): põe a RAIZ do scraper
# no sys.path, para encontrar config/, storage/, pdf_extractor (senão dá
# "ModuleNotFoundError: No module named 'config'").
_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)


def _load_env() -> None:
    """Carrega o .env (raiz do projeto) para as R2_* ao correr como script."""
    for path in (os.path.join(_ROOT, ".env"), os.path.join(_ROOT, "..", ".env")):
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        os.environ.setdefault(k.strip(), v.strip())
            return


_load_env()

# Rede com inspeção TLS: confiar no cert store do SO (silencioso se ausente).
os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
try:
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

logger = logging.getLogger(__name__)

ALDI_BASE = "https://www.aldi.pt"
REALISTIC_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)


class NoAldiStoreError(RuntimeError):
    """Não existe folheto Aldi para a região configurada (sem loja na zona)."""


# --- 7 regiões do folheto Aldi -----------------------------------------------
ALL_REGIONS = ("norte", "centro", "lisboa", "sul", "algarve", "acores", "madeira")

REGION_LABEL = {
    "norte": "Norte", "centro": "Centro", "lisboa": "Lisboa", "sul": "Sul",
    "algarve": "Algarve", "acores": "Açores", "madeira": "Madeira",
}

# ⚠️ SLUG usado no URL .../folheto/esta-semana-{slug}.html.
# É o ÚNICO sítio a corrigir se o site usar outra abreviatura. Confirmar ao vivo:
#   python scrapers/aldi.py --url-only --region norte   (idem para cada região)
REGION_SLUG = {
    "norte": "n", "centro": "c", "lisboa": "l", "sul": "a",
    "algarve": "g", "acores": "ac", "madeira": "md",
}

# Distrito (e ilhas) -> região. Cobre os 20 itens do dropdown do registo
# (ver OnboardingScreen.DISTRITOS). Chaves normalizadas (ver `_norm`).
REGION_BY_DISTRICT = {
    # Norte
    "viana do castelo": "norte", "braga": "norte", "porto": "norte",
    "vila real": "norte", "braganca": "norte",
    # Centro
    "aveiro": "centro", "viseu": "centro", "guarda": "centro",
    "coimbra": "centro", "leiria": "centro", "castelo branco": "centro",
    # Lisboa (e Vale do Tejo)
    "lisboa": "lisboa", "setubal": "lisboa", "santarem": "lisboa",
    # Sul (Alentejo)
    "portalegre": "sul", "evora": "sul", "beja": "sul",
    # Algarve
    "faro": "algarve",
    # Açores
    "r. a. acores": "acores", "acores": "acores",
    "ponta delgada": "acores", "angra do heroismo": "acores",
    # Madeira
    "r. a. madeira": "madeira", "madeira": "madeira", "funchal": "madeira",
}


def _norm(value: str) -> str:
    """minúsculas + sem acentos (para casar 'Bragança' com 'braganca')."""
    nfkd = unicodedata.normalize("NFKD", value.strip().lower())
    return "".join(c for c in nfkd if not unicodedata.combining(c))


def _settings_zone() -> tuple[Optional[str], Optional[str]]:
    """Distrito/cidade por omissão (settings; cai no env se settings indisponível)."""
    try:
        from config.settings import settings  # lazy
        return settings.aldi_district, settings.aldi_city
    except Exception:  # noqa: BLE001
        return os.getenv("ALDI_DISTRICT"), os.getenv("ALDI_CITY")


def region_for(district: str | None = None, city: str | None = None) -> str:
    """Região a partir do distrito/cidade (cai nas settings). NoAldiStoreError se não mapear."""
    d_default, c_default = _settings_zone()
    for value in (district, city, d_default, c_default):
        if value and _norm(value) in REGION_BY_DISTRICT:
            return REGION_BY_DISTRICT[_norm(value)]
    raise NoAldiStoreError(
        f"Aldi: região desconhecida (distrito='{district or d_default}', "
        f"cidade='{city or c_default}')"
    )


def region_code(district: str | None = None, city: str | None = None) -> str:
    """Slug de URL da região do distrito/cidade (esta-semana-{slug}.html)."""
    return REGION_SLUG[region_for(district, city)]


def current_week() -> int:
    """Número da semana ISO atual (1–53)."""
    return dt.date.today().isocalendar()[1]


def week_flyer_url(slug: str) -> str:
    """URL da página do folheto da semana atual para o `slug` de região."""
    return f"{ALDI_BASE}/folheto/esta-semana-{slug}.html"


# --- HTTP (honra a flag de inspeção TLS, como o resto do projeto) -------------
def _verify_tls() -> bool:
    return os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"


def _flyers_dir() -> Path:
    try:
        from config.settings import settings  # lazy
        base = settings.data_dir
    except Exception:  # noqa: BLE001
        base = os.getenv("SCRAPER_DATA_DIR", "data")
    path = Path(base) / "folhetos"
    path.mkdir(parents=True, exist_ok=True)
    return path


def _session():
    import requests  # lazy
    s = requests.Session()
    s.headers.update({"User-Agent": REALISTIC_UA, "Accept-Language": "pt-PT,pt;q=0.9"})
    return s


def _find_pdf_url(html: str, base_url: str) -> Optional[str]:
    """Link do PDF a partir do HTML da página do folheto.

    O site é Next.js: a base do folheto (`folhetos.aldi.pt/AAAA/sNN/<nome>/`) vem
    no blob `__NEXT_DATA__`, e o PDF descarrega-se em `{base}/GetPDF.ashx`
    (verificado ao vivo). Fallback: link de download clássico (bs4/regex).
    """
    # 1) Mecanismo real: base do folheto no JSON do Next.js -> GetPDF.ashx.
    bases = re.findall(r"https?://folhetos\.aldi\.pt/[A-Za-z0-9_./-]+", html)
    if bases:
        base = sorted(set(bases), key=len, reverse=True)[0].rstrip("/")
        if base.lower().endswith(("getpdf.ashx", ".pdf")):
            return base
        return base + "/GetPDF.ashx"

    # 2) Fallback: botão "Descarregar"/GetPDF/.pdf num <a href> clássico.
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
    hrefs.sort(key=lambda h: 0 if "getpdf" in h.lower() else 1)
    return urljoin(base_url, hrefs[0])


# Slug do folheto NACIONAL — o único que existe sempre. Os slugs regionais
# (esta-semana-c/l/a/g/…) só existem nas semanas em que há folheto regional;
# fora disso a resolução cai aqui (verificado: na semana 26/2026 só havia 'n').
NATIONAL_SLUG = "n"


def resolve_pdf_url(slug: str) -> str:
    """URL do PDF do folheto da semana atual para o `slug`, com fallback nacional.

    Se a página regional não existir (404) ou não tiver folheto, usa o nacional.
    """
    candidates = list(dict.fromkeys([slug, NATIONAL_SLUG]))  # sem repetir
    for s_try in candidates:
        page_url = week_flyer_url(s_try)
        logger.info("Aldi: slug '%s' (semana %d) — %s", s_try, current_week(), page_url)
        resp = _session().get(page_url, verify=_verify_tls(), timeout=30)
        if resp.status_code == 404:
            logger.info("Aldi: %s não existe (404) — fallback nacional", page_url)
            continue
        resp.raise_for_status()
        pdf_url = _find_pdf_url(resp.text, page_url)
        if pdf_url:
            if s_try != slug:
                logger.info("Aldi: região '%s' sem folheto próprio — usei o nacional", slug)
            return pdf_url
    raise RuntimeError(f"Aldi: sem folheto disponível (tentei {candidates})")


def _download_for_slug(slug: str, name: str) -> Path:
    """Descarrega o PDF da região `slug` para data/folhetos/aldi_{name}_{data}.pdf."""
    pdf_url = resolve_pdf_url(slug)
    logger.info("Aldi: a descarregar PDF — %s", pdf_url)
    resp = _session().get(pdf_url, verify=_verify_tls(), timeout=120)
    resp.raise_for_status()
    body = resp.content
    if body[:4] != b"%PDF":
        raise RuntimeError(f"Aldi: o URL não devolveu um PDF — {pdf_url}")
    target = _flyers_dir() / f"aldi_{name}_{dt.date.today().isoformat()}.pdf"
    target.write_bytes(body)
    logger.info("Aldi: PDF guardado em %s (%d bytes)", target, len(body))
    return target


def download_pdf(district: str | None = None, city: str | None = None) -> Path:
    """Descarrega o folheto Aldi da região do distrito/cidade (semana atual)."""
    region = region_for(district, city)
    return _download_for_slug(REGION_SLUG[region], region)


def download_region_pdf(region: str) -> Path:
    """Descarrega o folheto de uma região específica (ver ALL_REGIONS)."""
    if region not in REGION_SLUG:
        raise ValueError(f"Região inválida: {region!r} (use {', '.join(ALL_REGIONS)})")
    return _download_for_slug(REGION_SLUG[region], region)


def fetch_products(district: str | None = None, city: str | None = None) -> list[dict]:
    """Descarrega o folheto da região e extrai os produtos com o extrator do projeto."""
    pdf = download_pdf(district, city)
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui gasta IA
    produtos = extract_products_from_pdf(str(pdf), "Aldi")
    logger.info("Aldi: %d produtos extraídos", len(produtos))
    return produtos


# --- integração R2 (substitui o antigo upload_to_drive) -----------------------
def _current_window() -> tuple[dt.date, dt.date]:
    """Janela do folheto desta semana (quinta a quarta, ciclo típico PT)."""
    today = dt.date.today()
    thursday = today - dt.timedelta(days=(today.weekday() - 3) % 7)
    return thursday, thursday + dt.timedelta(days=6)


def _validity(pdf_path) -> tuple[dt.date, dt.date]:
    """Datas REAIS do folheto (texto — o Aldi tem-nas em texto; visão se for
    só-imagem); recurso à janela da semana (quinta-quarta) se não conseguir ler."""
    from pdf_extractor import extract_validity_smart  # lazy
    ini, fim = extract_validity_smart(pdf_path, "Aldi")
    return (ini, fim) if ini and fim else _current_window()


def download_region_to_r2(region: str) -> str:
    """Descarrega o folheto de uma região e carrega-o no R2. Devolve a key."""
    from storage.r2_storage import r2_storage  # lazy
    if not r2_storage.is_configured():
        raise RuntimeError("Aldi: R2 não configurado (R2_ENDPOINT/BUCKET/keys)")
    pdf = download_region_pdf(region)
    ini, fim = _validity(pdf)
    key = f"Aldi {REGION_LABEL[region]} {ini:%d-%m-%Y} - {fim:%d-%m-%Y}.pdf"
    r2_storage.upload_pdf(key, pdf)
    logger.info("Aldi: folheto de %s no R2 como '%s'", REGION_LABEL[region], key)
    return key


def download_all_regions_to_r2() -> dict[str, str]:
    """Carrega no R2 o folheto das 7 regiões. Best-effort: regista falhas e segue.

    Devolve {região: key} das que correram bem (uma região pode falhar sem
    bloquear as outras — ex.: sem folheto nas ilhas numa dada semana).
    """
    done: dict[str, str] = {}
    for region in ALL_REGIONS:
        try:
            done[region] = download_region_to_r2(region)
        except Exception as exc:  # noqa: BLE001 — não bloquear as restantes regiões
            logger.warning("Aldi: região %s falhou — %s", REGION_LABEL[region], exc)
    logger.info("Aldi: %d/%d regiões carregadas no R2", len(done), len(ALL_REGIONS))
    return done


if __name__ == "__main__":  # teste manual
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description="Folheto Aldi da semana atual (7 regiões)")
    ap.add_argument("--region", choices=ALL_REGIONS, help="região específica")
    ap.add_argument("--district", help="distrito (deriva a região)")
    ap.add_argument("--city", help="cidade (deriva a região)")
    ap.add_argument("--url-only", action="store_true", help="só resolve o URL do PDF (testar slug)")
    ap.add_argument("--r2", action="store_true", help="carrega o PDF da região no R2")
    ap.add_argument("--all-regions", action="store_true", help="carrega as 7 regiões no R2")
    args = ap.parse_args()

    if args.all_regions:
        print("R2:", download_all_regions_to_r2())
    elif args.url_only:
        slug = REGION_SLUG[args.region] if args.region else region_code(args.district, args.city)
        print(resolve_pdf_url(slug))
    elif args.r2:
        region = args.region or region_for(args.district, args.city)
        print("R2 key:", download_region_to_r2(region))
    elif args.region:
        print("PDF:", download_region_pdf(args.region))
    else:
        prods = fetch_products(args.district, args.city)
        print(f"{len(prods)} produtos extraídos")
