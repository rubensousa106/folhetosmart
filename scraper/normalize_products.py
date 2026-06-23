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
import re
import sys
from datetime import date


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
from storage.r2_storage import r2_storage  # noqa: E402

logger = logging.getLogger(__name__)

# Chaves (em minúsculas) tal como o produtor as grava no backend.
STORE_KEYS = ["continente", "pingo doce", "lidl", "aldi", "intermarché", "intermarche"]

BATCH = 60  # produtos por chamada ao Claude (evita truncar o output)

PROMPT_HEADER = (
    "És um normalizador de produtos de folhetos de supermercados portugueses. "
    "Recebes uma lista numerada de nomes de produtos (de vários supermercados). "
    "Para cada um devolve o NOME CANÓNICO e a MARCA, de forma CONSISTENTE: o MESMO "
    "produto vendido em supermercados diferentes deve receber EXATAMENTE o mesmo "
    "nome canónico, para poderem ser comparados.\n\n"
    "NOME CANÓNICO (campo 'canonico'):\n"
    "- Nome GENÉRICO do produto, SEM a marca do supermercado (Continente, Pingo Doce, "
    "Lidl, Aldi, Intermarché, Minipreço, Auchan, Dia, Mercadona...) — essa é a loja, "
    "mostrada à parte.\n"
    "- Usa sempre o SINGULAR (ex.: 'Sardinha', nunca 'Sardinhas').\n"
    "- Capitaliza as palavras principais; normaliza unidades (g, kg, L, ml, un).\n"
    "- Remove 'REF: 1234567' e o prefixo 'Emb.:'/'EMB:' (fica só a quantidade: "
    "'Emb.: 800 G' → '800g').\n"
    "- INCLUI a quantidade/tamanho e, se for conjunto, o pack (ex.: '6x1L', '500g'). "
    "Individual e pack do mesmo produto têm nomes canónicos DIFERENTES.\n"
    "- Remove texto de marketing/promoção/balcão de atendimento.\n"
    "- NÃO inventes informação que não está no nome.\n"
    "- Exemplo: 'Continente Sardinha Inteira Nacional Congelada' → 'Sardinha Inteira Congelada'.\n\n"
    "MARCA (campo 'marca'):\n"
    "- A marca NACIONAL/externa do produto (ex.: 'Mimosa', 'Agros', 'Doritos', 'Compal').\n"
    "- Se for marca-própria do supermercado ou produto sem marca, devolve \"\" (vazio).\n\n"
    "- REGRAS ADICIONAIS IMPORTANTES:\n"
    "- Padroniza unidades**: 'G' → 'g', 'KG' → 'kg', 'L' → 'l', 'ML' → 'ml', 'UN' → 'un'.\n"
    "- Formata números**: '1,2' → '1.2', '1.200' → '1200'.\n"
    "- Produtos com variantes**: se a lista incluir 'Bife da Vazia' e 'Vazia', normaliza para o mesmo nome base.\n"
    "- Produtos de diferentes marcas**: 'Leite Mimosa' e 'Leite Agros' → 'Leite' (a marca vai para o campo 'marca').\n"
    "- Produtos com diferentes pesos**: 'Leite 1L' e 'Leite 2L' → têm nomes canónicos diferentes porque a quantidade é diferente.\n"
    "- Variações de sabor**: inclui o sabor no nome canónico, mas de forma genérica (ex.: 'Iogurte Morango', 'Iogurte Baunilha').\n"
    "- Produtos de higiene/limpeza**: mantém a função principal (ex.: 'Champô' em vez de 'Champô Protetor').\n"

    "MARCA (campo 'marca'):\n"
    "- A marca NACIONAL/externa do produto (ex.: 'Mimosa', 'Agros', 'Doritos', 'Compal', 'Nestlé', 'Lay's').\n"
    "- Se for marca-própria do supermercado (ex.: 'Continente Equilíbrio', 'Pingo Doce', 'Lidl') ou produto sem marca, devolve "" (vazio).\n"
    "- Para produtos de higiene/limpeza, devolve a marca se for conhecida e não for a do supermercado.\n"

    "Devolve APENAS um array JSON: "
    "[{\"i\": <número>, \"canonico\": \"<nome>\", \"marca\": \"<marca ou vazio>\"}]. "
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


# --- Validade (mesma lógica do backend) + publicação do feed no R2 ----------
_DATE_TOKEN = re.compile(r"\d{1,2}[/-]\d{1,2}[/-]\d{4}|\d{8}|\d{6}")


def _norm_date(tok: str) -> str | None:
    try:
        if "/" in tok or "-" in tok:
            d, m, y = re.split(r"[/-]", tok)
            return "%02d/%02d/%04d" % (int(d), int(m), int(y))
        digits = re.sub(r"\D", "", tok)
        if len(digits) == 8:
            return f"{digits[0:2]}/{digits[2:4]}/{digits[4:8]}"
        if len(digits) == 6:
            return f"{digits[0:2]}/{digits[2:4]}/20{digits[4:6]}"
    except Exception:
        return None
    return None


def _parse_validade(flyer: str | None) -> str | None:
    if not flyer:
        return None
    ds = [d for d in (_norm_date(m.group()) for m in _DATE_TOKEN.finditer(flyer)) if d][:2]
    return f"{ds[0]} a {ds[1]}" if len(ds) == 2 else None


def _validade_for(token: str, supermercado: str) -> str | None:
    try:
        resp = requests.get(
            f"{_backend_base()}/api/v1/admin/products/source",
            params={"supermarket": supermercado},
            headers={"Authorization": f"Bearer {token}"},
            timeout=60, verify=_verify_tls(),
        )
        flyer = resp.json().get("flyer", "") if resp.status_code == 200 else ""
        return _parse_validade(flyer)
    except Exception:
        return None


def _date_obj(token_date: str):
    """'DD/MM/AAAA' -> datetime.date, ou None se ilegível."""
    try:
        d, m, y = token_date.strip().split("/")
        return date(int(y), int(m), int(d))
    except Exception:  # noqa: BLE001
        return None


def _feed_span_key(validades: list[str]) -> str:
    """Nome do feed pelo INTERVALO de validade entre TODAS as lojas:
    `produtos_{inicio_mais_cedo}_{fim_mais_tarde}.json` (datas DD-MM-AAAA).

    Ex.: Continente 22/06–27/06 + Aldi 25/06–30/06 -> produtos_22-06-2026_30-06-2026.json.
    Cai na data de hoje se não houver validades legíveis. (Não se usa '/' no nome
    porque no R2 criaria pastas.)
    """
    starts, ends = [], []
    for v in validades:
        if not v or " a " not in v:
            continue
        ini, fim = v.split(" a ", 1)
        s, e = _date_obj(ini), _date_obj(fim)
        if s:
            starts.append(s)
        if e:
            ends.append(e)
    if starts and ends:
        return f"produtos_{min(starts):%d-%m-%Y}_{max(ends):%d-%m-%Y}.json"
    return f"produtos_{date.today():%d-%m-%Y}.json"


def _publish_feed_to_r2(token: str, stores: dict, canon: dict, marca: dict) -> None:
    """Constrói o feed (formato /all) e publica-o no R2; envia ao backend o link
    assinado para o /all passar a redirecionar a app para o R2."""
    if not r2_storage.is_configured():
        logger.warning("⚠️ R2 não configurado — feed NÃO publicado (a app usa o /all da BD).")
        return
    feed = []
    validades: list[str] = []
    for nome, produtos in stores.items():
        validade = _validade_for(token, nome)
        if validade:
            validades.append(validade)
        for i, p in enumerate(produtos):
            original = p.get("produto")
            feed.append({
                "produto": canon.get((nome, i), original),  # nome canónico
                "preco": p.get("preco"),
                "supermercado": nome,
                "validade": validade,
                "original": original,
                "marca": marca.get((nome, i), ""),
            })
    # Nome pelo intervalo de validade (início mais cedo -> fim mais tarde).
    key = _feed_span_key(validades)
    r2_storage.upload_json(key, feed)
    url = r2_storage.presign_get(key)
    # Data de fim mais tarde — o backend usa-a no /feeds para ignorar feeds já
    # totalmente expirados (DD-MM-AAAA).
    ends = [_date_obj(v.split(" a ", 1)[1]) for v in validades if v and " a " in v]
    ends = [e for e in ends if e]
    valid_until = max(ends).strftime("%d-%m-%Y") if ends else ""
    logger.info("📦 Feed em %s (válido até %s)", key, valid_until or "?")
    resp = requests.post(
        f"{_backend_base()}/api/v1/admin/feed-url",
        json={"url": url, "valid_until": valid_until},
        headers={"Authorization": f"Bearer {token}"},
        timeout=120, verify=_verify_tls(),
    )
    if resp.status_code < 400:
        logger.info("✅ Feed publicado no R2 (%d ofertas) — /all passa a servir do R2", len(feed))
        # Alerta push aos utilizadores: "produtos da semana disponíveis" (best-effort).
        try:
            from notifications import notify_new_flyers  # lazy
            notify_new_flyers(len(feed), len(stores), None)
        except Exception as exc:  # noqa: BLE001 — a notificação não trava a publicação
            logger.warning("⚠️ Notificação FCM não enviada (%s)", exc)
    else:
        logger.error("❌ Backend recusou feed-url (%s): %s", resp.status_code, resp.text[:160])


def publish_only() -> bool:
    """Publica no R2 o que JÁ está normalizado no backend (NÃO gasta IA)."""
    token = _backend_token()
    if not token:
        return False
    logger.info("🔑 Autenticado: %s", _backend_base())
    stores: dict[str, list[dict]] = {}
    for key in dict.fromkeys(STORE_KEYS):
        res = _fetch_store(key, token)
        if res and res[1]:
            stores.setdefault(res[0], res[1])
            logger.info("📦 %s: %d produtos", res[0], len(res[1]))
    if not stores:
        logger.error("❌ Nenhuma loja com produtos.")
        return False
    canon = {
        (nome, i): (p.get("canonico") or p.get("produto"))
        for nome, produtos in stores.items()
        for i, p in enumerate(produtos)
    }
    marca = {
        (nome, i): p.get("marca", "")
        for nome, produtos in stores.items()
        for i, p in enumerate(produtos)
    }
    _publish_feed_to_r2(token, stores, canon, marca)
    return True


def _chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def _canonical_batch(client, model: str, nomes: list[str]) -> list[tuple[str, str]]:
    """Devolve (nome canónico, marca) por nome (alinhado por índice)."""
    listagem = "\n".join(f"{i + 1}. {n}" for i, n in enumerate(nomes))
    msg = client.messages.create(
        model=model, max_tokens=4000,
        messages=[{"role": "user", "content": PROMPT_HEADER + listagem}],
    )
    texto = "".join(b.text for b in msg.content if getattr(b, "type", "") == "text").strip()
    if texto.startswith("```"):
        texto = texto.split("```")[1].lstrip("json").strip()
    dados = json.loads(texto)
    by_i = {
        int(d["i"]): (str(d.get("canonico") or "").strip(), str(d.get("marca") or "").strip())
        for d in dados
    }
    # Fallback ao nome original se o Claude falhar algum índice.
    out: list[tuple[str, str]] = []
    for i in range(len(nomes)):
        c, m = by_i.get(i + 1, ("", ""))
        out.append((c or nomes[i], m))
    return out


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
    marca: dict[tuple[str, int], str] = {}
    for n, batch in enumerate(_chunks(flat, BATCH), 1):
        nomes = [it[2] for it in batch]
        try:
            resultados = _canonical_batch(client, model, nomes)
            for it, (c, m) in zip(batch, resultados):
                canon[(it[0], it[1])] = c
                marca[(it[0], it[1])] = m
            logger.info("✅ Lote %d/%d normalizado (%d produtos)",
                        n, (len(flat) + BATCH - 1) // BATCH, len(batch))
        except Exception as exc:  # noqa: BLE001
            logger.warning("⚠️ Lote %d falhou (%s) — uso os nomes originais", n, exc)
            for it in batch:
                canon[(it[0], it[1])] = it[2]
                marca[(it[0], it[1])] = ""

    # 4) Volta a publicar cada loja com o campo `canonico` (sem `flyer` para
    #    NÃO mexer na flag "analisar 1×").
    resumo = []
    for nome, produtos in stores.items():
        enriched = [{
            "produto": p.get("produto"),
            "preco": p.get("preco"),
            "canonico": canon.get((nome, i), p.get("produto")),
            "marca": marca.get((nome, i), ""),
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

    _publish_feed_to_r2(token, stores, canon, marca)
    return resumo


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    if "--publish" in sys.argv:
        # Só publica no R2 o que já está normalizado (não chama o Claude).
        ok = publish_only()
        print("\n=== PUBLICAÇÃO R2 ===")
        print("OK" if ok else "FALHOU")
    else:
        resultados = normalize()
        print("\n=== RESUMO ===")
        for r in resultados:
            print(r)
        if not resultados:
            print("(nada normalizado)")
