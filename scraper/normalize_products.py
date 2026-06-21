"""Normalização: junta os produtos de TODOS os supermercados e pede ao Claude um
NOME CANÓNICO por produto, para o mesmo produto de lojas diferentes se juntar no
ecrã Comparar (e a ⭐ "mais barato" acender).

Estratégia (robusta, sem truncar): o Claude normaliza em LOTES (nome canónico por
produto); o agrupamento por esse nome é feito no código/app. NÃO re-extrai nada —
lê o que já está no backend e volta a publicar cada loja com o campo `canonico`.

Uso:
    python normalize_products.py

Variáveis (no .env): FOLHETO_BACKEND_URL, FOLHETO_ADMIN_EMAIL, FOLHETO_ADMIN_PASSWORD,
ANTHROPIC_API_KEY, ANTHROPIC_MODEL (opcional), FOLHETO_INSECURE_TLS (dev).
"""
from __future__ import annotations

# Em redes com inspeção TLS, confiar no cert store do Windows (Anthropic/httpx).
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

logger = logging.getLogger(__name__)

# Chaves (em minúsculas) tal como o produtor as grava no backend.
STORE_KEYS = ["continente", "pingo doce", "lidl", "aldi", "intermarché", "intermarche"]

BATCH = 60  # produtos por chamada ao Claude (evita truncar o output)

PROMPT_HEADER = (
    "És um normalizador de produtos de folhetos de supermercados portugueses. "
    "Recebes uma lista numerada de nomes de produtos (de vários supermercados). "
    "Para cada um devolve um NOME CANÓNICO, de forma CONSISTENTE: o MESMO produto "
    "vendido em supermercados diferentes deve receber EXATAMENTE o mesmo nome "
    "canónico, para poderem ser comparados.\n\n"
    "Regras:\n"
    "- Formato: 'Marca Produto Variante Quantidade' (ex.: 'Doritos Queijo 100g'). "
    "Mantém a marca quando existir.\n"
    "- Capitaliza as palavras principais; normaliza unidades (g, kg, L, ml, un).\n"
    "- Remove códigos tipo 'REF: 1234567' e o prefixo 'Emb.:'/'EMB:' — fica só a "
    "quantidade ('Emb.: 800 G' → '800g'; 'Emb.: 6 Unid' → '6 un').\n"
    "- Remove texto de marketing/promoção/balcão de atendimento.\n"
    "- INCLUI SEMPRE a quantidade/tamanho e, se for conjunto, o pack "
    "(ex.: '6x1L', '4x200ml', '500g', '6 un'). Um produto INDIVIDUAL e um PACK do "
    "mesmo produto têm nomes canónicos DIFERENTES — NUNCA os juntes.\n"
    "- Frescos sem marca → 'Produto Variante' (ex.: 'Bacalhau Graúdo 1ª').\n"
    "- NÃO inventes informação que não está no nome.\n"
    "- Produtos DIFERENTES (marca/origem/tamanho/pack diferentes) → nomes canónicos "
    "DIFERENTES.\n\n"
    "Devolve APENAS um array JSON: [{\"i\": <número>, \"canonico\": \"<nome>\"}]. "
    "Sem texto à volta.\n\nLista:\n"
)


def _verify_tls() -> bool:
    return os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"


def _backend_base() -> str:
    return os.getenv("FOLHETO_BACKEND_URL", "http://localhost:8080").rstrip("/")


def _backend_token() -> str | None:
    email = os.getenv("FOLHETO_ADMIN_EMAIL")
    pwd = os.getenv("FOLHETO_ADMIN_PASSWORD")
    if not email or not pwd:
        logger.error("❌ FOLHETO_ADMIN_EMAIL/PASSWORD em falta.")
        return None
    resp = requests.post(
        f"{_backend_base()}/api/v1/auth/login",
        json={"email": email, "password": pwd}, timeout=200, verify=_verify_tls(),
    )
    resp.raise_for_status()
    return resp.json().get("token")


def _fetch_store(key: str, token: str) -> tuple[str, list[dict]] | None:
    """Produtos atuais de uma loja (GET /products/latest, autenticado)."""
    resp = requests.get(
        f"{_backend_base()}/api/v1/products/latest",
        params={"supermarket": key},
        headers={"Authorization": f"Bearer {token}"},
        timeout=120, verify=_verify_tls(),
    )
    if resp.status_code != 200:
        return None
    data = resp.json()
    return data.get("supermercado", key), data.get("produtos", [])


def _chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def _canonical_batch(client, model: str, nomes: list[str]) -> list[str]:
    """Devolve um nome canónico por nome (alinhado por índice)."""
    listagem = "\n".join(f"{i + 1}. {n}" for i, n in enumerate(nomes))
    msg = client.messages.create(
        model=model, max_tokens=4000,
        messages=[{"role": "user", "content": PROMPT_HEADER + listagem}],
    )
    texto = "".join(b.text for b in msg.content if getattr(b, "type", "") == "text").strip()
    if texto.startswith("```"):
        texto = texto.split("```")[1].lstrip("json").strip()
    dados = json.loads(texto)
    canon = {int(d["i"]): str(d["canonico"]).strip() for d in dados}
    # Fallback ao nome original se o Claude falhar algum índice.
    return [canon.get(i + 1) or nomes[i] for i in range(len(nomes))]


def normalize() -> list[dict]:
    token = _backend_token()
    if not token:
        return []
    logger.info("🔑 Autenticado: %s", _backend_base())

    # 1) Recolhe os produtos atuais de cada loja (sem duplicar chaves).
    stores: dict[str, list[dict]] = {}
    for key in dict.fromkeys(STORE_KEYS):
        res = _fetch_store(key, token)
        if res and res[1]:
            nome, produtos = res
            stores.setdefault(nome, produtos)
            logger.info("📦 %s: %d produtos", nome, len(produtos))
    if not stores:
        logger.error("❌ Nenhuma loja com produtos no backend.")
        return []

    # 2) Achata para normalizar (mantém a referência loja+índice).
    flat: list[tuple[str, int, str]] = []
    for nome, produtos in stores.items():
        for i, p in enumerate(produtos):
            flat.append((nome, i, str(p.get("produto", ""))))
    # Ordena por nome para que produtos semelhantes de lojas diferentes caiam no
    # MESMO lote → o Claude atribui-lhes o mesmo nome canónico (melhor matching).
    flat.sort(key=lambda it: it[2].lower())
    logger.info("🧮 %d produtos no total — a normalizar em lotes de %d", len(flat), BATCH)

    # 3) Claude atribui o nome canónico, em lotes.
    from anthropic import Anthropic  # lazy
    client = Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])
    model = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-6")
    canon: dict[tuple[str, int], str] = {}
    for n, batch in enumerate(_chunks(flat, BATCH), 1):
        nomes = [it[2] for it in batch]
        try:
            resultados = _canonical_batch(client, model, nomes)
            for it, c in zip(batch, resultados):
                canon[(it[0], it[1])] = c
            logger.info("✅ Lote %d/%d normalizado (%d produtos)",
                        n, (len(flat) + BATCH - 1) // BATCH, len(batch))
        except Exception as exc:  # noqa: BLE001
            logger.warning("⚠️ Lote %d falhou (%s) — uso os nomes originais", n, exc)
            for it in batch:
                canon[(it[0], it[1])] = it[2]

    # 4) Volta a publicar cada loja com o campo `canonico` (sem `flyer` para
    #    NÃO mexer na flag "analisar 1×").
    resumo = []
    for nome, produtos in stores.items():
        enriched = [{
            "produto": p.get("produto"),
            "preco": p.get("preco"),
            "canonico": canon.get((nome, i), p.get("produto")),
        } for i, p in enumerate(produtos)]
        resp = requests.post(
            f"{_backend_base()}/api/v1/admin/products",
            params={"supermarket": nome, "count": len(enriched)},
            json={"supermercado": nome, "produtos": enriched},
            headers={"Authorization": f"Bearer {token}"},
            timeout=200, verify=_verify_tls(),
        )
        ok = resp.status_code < 400
        if not ok:
            logger.error("❌ %s: backend recusou (%s) %s", nome, resp.status_code, resp.text[:160])
        else:
            logger.info("✅ %s: %d produtos republicados com nome canónico", nome, len(enriched))
        resumo.append({"supermercado": nome, "produtos": len(enriched), "enviado": ok})
    return resumo


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    resultados = normalize()
    print("\n=== RESUMO ===")
    for r in resultados:
        print(r)
    if not resultados:
        print("(nada normalizado)")
