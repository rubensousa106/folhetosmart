"""Produtor: Drive (PDF) -> Claude (pdf_extractor) -> backend (Postgres) -> app.

Reaproveita o extrator do Ruben (`pdf_extractor.extract_products_from_pdf`) TAL E
QUAL. Em vez de escrever no Drive (a service account não tem quota de
armazenamento numa conta pessoal), faz POST do JSON {supermercado, produtos}
para o backend, que o guarda no Postgres; a app lê em GET /api/v1/products/latest.

PONTOS-CHAVE (para NÃO gastar IA/dinheiro à toa):
  - CACHE por folheto em data/json_cache/ — se já existir, NÃO reextrai;
  - só o folheto MAIS RECENTE de cada supermercado é processado.

Uso:
    python drive_producer.py             # todos os supermercados
    python drive_producer.py Continente  # só um

Variáveis (no .env ou no ambiente):
    FOLHETO_BACKEND_URL     ex.: https://folhetosmart.onrender.com  (ou http://localhost:8080)
    FOLHETO_ADMIN_EMAIL     rubensousa106@gmail.com
    FOLHETO_ADMIN_PASSWORD  ********
"""
from __future__ import annotations

# Em redes com inspeção TLS, confiar no cert store do Windows (onde está o CA da
# interceção): faz a Anthropic e o Drive (httpx/httplib2) funcionarem SEM mexer
# no pdf_extractor. Silencioso se o truststore não existir (ex.: no GitHub
# Actions, sem interceção, usa-se o certifi normal).
import os  # noqa: E402

os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
try:
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

import json
import logging
import os
import sys


def _load_env_file() -> None:
    """Carrega o .env ANTES de importar settings/gdrive_storage (que leem o env
    no momento do import). Em Docker/Actions as variáveis já vêm do ambiente —
    o setdefault não as sobrepõe."""
    for path in (
        os.path.join(os.path.dirname(__file__), ".env"),
        os.path.join(os.path.dirname(__file__), "..", ".env"),
        ".env",
    ):
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        key, value = line.split("=", 1)
                        os.environ.setdefault(key.strip(), value.strip())
            return


_load_env_file()

import requests  # noqa: E402
from storage.r2_storage import r2_storage  # noqa: E402

logger = logging.getLogger(__name__)

CACHE_DIR = os.path.join(os.path.dirname(__file__), "data", "json_cache")
_DOWNLOADS_DIR = os.path.join(os.path.dirname(__file__), "data", "folhetos")

# Deteção do supermercado a partir do NOME CRU do ficheiro do Drive
# (sem os.path.basename: o nome tem barras nas datas e o Windows trata '/' como
#  separador de caminho).
_SUPERMERCADOS = {
    "continente": "Continente",
    "pingo doce": "Pingo Doce",
    "pingodoce": "Pingo Doce",
    "lidl": "Lidl",
    "intermarche": "Intermarché",
    "intermarché": "Intermarché",
}

# O Aldi é REGIONAL: o produtor SABE distinguir as 7 regiões (Aldi Norte, …) para
# a app poder mostrar só o folheto da zona do utilizador.
#
# ⚠️ ESTA SEMANA os folhetos das 7 regiões são IGUAIS, por isso processa-se UM só
# "Aldi" (nacional) — poupa 7× a extração/IA. NÃO APAGAR a lógica regional abaixo:
# pode voltar a ser precisa. Basta pôr ALDI_POR_REGIAO=True quando os folhetos
# regionais voltarem a divergir, e o produtor distingue de novo Aldi Norte/Centro/…
ALDI_POR_REGIAO = False
_ALDI_REGIOES = ("Norte", "Centro", "Lisboa", "Sul", "Algarve", "Açores", "Madeira")


def _detect_supermarket(filename: str) -> str | None:
    low = filename.lower()
    if "aldi" in low:
        if ALDI_POR_REGIAO:
            for reg in _ALDI_REGIOES:
                if reg.lower() in low:
                    return f"Aldi {reg}"
        # Um só Aldi (folhetos regionais iguais) ou Aldi sem região no nome (nacional).
        return "Aldi"
    for chave, nome in _SUPERMERCADOS.items():
        if chave in low:
            return nome
    return None


def _list_flyer_pdfs() -> list[dict]:
    """PDFs VÁLIDOS no bucket R2 (mais recentes primeiro). Cada item: {key, name, modified}.

    Ignora a pasta `backup/`: são folhetos já rodados/expirados que o `rotate_r2`
    arquivou. Sem este filtro eram reprocessados, porque a rotação dá-lhes um
    `modified` mais recente que o folheto atual no root e ficavam à frente na
    ordenação — fazendo extrair a semana PASSADA em vez da atual.
    """
    return [p for p in r2_storage.list_pdfs() if not p["key"].startswith("backup/")]


def _cache_path(flyer_name: str) -> str:
    safe = "".join(c if (c.isalnum() or c in " .-_") else "_" for c in flyer_name)
    return os.path.join(CACHE_DIR, safe + ".json")


def _extract_or_cache(flyer: dict, supermercado: str) -> list[dict]:
    """Produtos do folheto — usa a CACHE (NÃO chama a IA se já existir)."""
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache = _cache_path(flyer["name"])
    if os.path.exists(cache):
        logger.info("💾 A usar cache (SEM gastar IA): %s", os.path.basename(cache))
        with open(cache, encoding="utf-8") as fh:
            return json.load(fh)

    logger.info("📄 A descarregar e extrair %s (folheto: %s)…", supermercado, flyer["name"])
    pdf_path = r2_storage.download_pdf(flyer["key"], _DOWNLOADS_DIR)
    from pdf_extractor import extract_products_from_pdf  # lazy: só aqui é que gasta IA

    produtos = extract_products_from_pdf(str(pdf_path), supermercado)
    if produtos:
        with open(cache, "w", encoding="utf-8") as fh:
            json.dump(produtos, fh, ensure_ascii=False, indent=2)
        logger.info("✅ Claude analisou %s: %d produtos (guardado em cache)", supermercado, len(produtos))
    else:
        # NÃO grava cache vazia: senão uma falha (ex.: TLS) ficava "colada" e
        # impedia a reextração na próxima vez.
        logger.warning("⚠️ %s: extração devolveu 0 produtos — NÃO guardo cache", supermercado)
    return produtos


def _verify_tls() -> bool:
    # Em redes com inspeção TLS (dev), FOLHETO_INSECURE_TLS=1 desliga a verificação.
    return os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"


def _backend_base() -> str:
    return os.getenv("FOLHETO_BACKEND_URL", "https://folhetosmart.onrender.com").rstrip("/")


def _backend_token() -> str | None:
    email = os.getenv("FOLHETO_ADMIN_EMAIL")
    pwd = os.getenv("FOLHETO_ADMIN_PASSWORD")
    if not email or not pwd:
        logger.error("❌ FOLHETO_ADMIN_EMAIL/PASSWORD em falta — não consigo autenticar.")
        return None
    # timeout alto: o Render free pode demorar ~3 min a "acordar" no 1.º pedido.
    resp = requests.post(
        f"{_backend_base()}/api/v1/auth/login",
        json={"email": email, "password": pwd},
        timeout=200, verify=_verify_tls(),
    )
    resp.raise_for_status()
    return resp.json().get("token")


def _already_analyzed(token: str, supermercado: str, flyer_name: str) -> bool:
    """Pergunta ao backend qual o folheto já analisado; True se for este mesmo
    (flag "analisar 1×" — durável, sobrevive ao container efémero do Actions)."""
    try:
        resp = requests.get(
            f"{_backend_base()}/api/v1/admin/products/source",
            params={"supermarket": supermercado},
            headers={"Authorization": f"Bearer {token}"},
            timeout=60, verify=_verify_tls(),
        )
        resp.raise_for_status()
        return (resp.json().get("flyer") or "").strip() == flyer_name.strip()
    except Exception as exc:  # noqa: BLE001 — na dúvida processa (não bloqueia a produção)
        logger.warning("⚠️ Não consegui verificar a flag (%s) — vou processar", exc)
        return False


def _post_to_backend(token: str, supermercado: str, flyer_name: str, produtos: list[dict]) -> bool:
    payload = {"supermercado": supermercado, "produtos": produtos}
    resp = requests.post(
        f"{_backend_base()}/api/v1/admin/products",
        params={"supermarket": supermercado, "count": len(produtos), "flyer": flyer_name},
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
        timeout=200, verify=_verify_tls(),
    )
    if resp.status_code >= 400:
        logger.error("❌ Backend recusou (%s): %s", resp.status_code, resp.text[:200])
        return False
    return True


def produce(only_supermarket: str | None = None, force: bool = False) -> list[dict]:
    if not r2_storage.is_configured():
        logger.error(
            "❌ Cloudflare R2 não configurado — endpoint=%r, bucket=%r, chave=%s",
            r2_storage.endpoint, r2_storage.bucket,
            "OK" if r2_storage.access_key else "EM FALTA",
        )
        return []

    token = _backend_token()
    if not token:
        return []
    logger.info("🔑 Autenticado no backend: %s", _backend_base())

    flyers = _list_flyer_pdfs()  # mais recentes primeiro
    logger.info("📁 %d folhetos PDF na pasta do Drive", len(flyers))

    vistos: set[str] = set()
    resultados: list[dict] = []
    for flyer in flyers:
        supermercado = _detect_supermarket(flyer["name"])
        if not supermercado:
            logger.warning("⚠️ Supermercado desconhecido em '%s' — ignorado", flyer["name"])
            continue
        if only_supermarket and supermercado.lower() != only_supermarket.lower():
            continue
        if supermercado in vistos:
            continue  # só o folheto MAIS RECENTE de cada supermercado
        vistos.add(supermercado)

        # Flag "analisar 1×": se este folheto já foi analisado, não gasta IA.
        if not force and _already_analyzed(token, supermercado, flyer["name"]):
            logger.info("⏭️  %s: folheto '%s' JÁ analisado — não gasto IA", supermercado, flyer["name"])
            resultados.append({"supermercado": supermercado, "saltado": "já analisado"})
            continue

        try:
            produtos = _extract_or_cache(flyer, supermercado)
            if not produtos:
                logger.error("❌ %s: 0 produtos", supermercado)
                resultados.append({"supermercado": supermercado, "error": "0 produtos"})
                continue
            enviado = _post_to_backend(token, supermercado, flyer["name"], produtos)
            if enviado:
                logger.info("✅ %s: %d produtos enviados para o backend", supermercado, len(produtos))
            resultados.append({
                "supermercado": supermercado,
                "produtos": len(produtos),
                "enviado": enviado,
            })
        except Exception as exc:  # noqa: BLE001 — um folheto mau não trava os outros
            logger.error("❌ Erro em %s: %s", supermercado, exc)
            resultados.append({"supermercado": supermercado, "error": str(exc)})
    return resultados


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    alvo = sys.argv[1] if len(sys.argv) > 1 else None
    forcar = os.getenv("FOLHETO_FORCE", "0") == "1"
    if alvo:
        logger.info("A produzir apenas para: %s", alvo)
    if forcar:
        logger.info("⚠️ FOLHETO_FORCE=1 — vai reextrair mesmo que já analisado.")
    resultados = produce(only_supermarket=alvo, force=forcar)
    print("\n=== RESUMO ===")
    for r in resultados:
        print(r)
    if not resultados:
        print("(nada processado)")

    # Código de saída para o pipeline decidir se vale a pena NORMALIZAR (gasta IA):
    #   0  = houve folheto(s) NOVO(s) processado(s)  -> normalizar + publicar o feed
    #   10 = nada novo (tudo já analisado/sem folhetos) -> saltar a normalização
    # Assim a automação pode correr vários dias sem desperdiçar IA quando nada mudou.
    novos = sum(1 for r in resultados if r.get("enviado") and r.get("produtos"))
    if novos:
        print(f"\n🆕 {novos} folheto(s) novo(s) — normalização recomendada.")
        sys.exit(0)
    print("\nℹ️ Sem folhetos novos — normalização pode ser saltada (poupa IA).")
    sys.exit(10)
