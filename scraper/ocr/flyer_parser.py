"""Transforma blocos de texto OCR de um folheto em `RawProduct`s.

Cada bloco (produzido pelo agrupamento espacial do `pdf_extractor`) tem o
padrão visual de um produto:
    [label]  [preço promocional]  [preço original riscado]  [nome 1-4 linhas]  [EMB.: ...]

Estratégia por bloco:
- 1.º preço = promocional; 2.º preço (se maior) = original riscado;
- nome = linhas sem preço e sem label de promoção;
- is_promotion = há preço original OU label de desconto.
"""
from __future__ import annotations

import datetime as dt
import logging
import re
from typing import Optional

from ai_matcher.models import RawProduct

logger = logging.getLogger(__name__)

# Preço: "11,99€", "5€", "9,99 EUR". Cêntimos opcionais; € ou EUR obrigatório.
# (eur sem ser seguido de letra, para não casar "europa"/"euro" em texto.)
_PRICE_RE = re.compile(r"(\d{1,3})(?:[.,](\d{2}))?\s*(?:€|eur(?![a-z]))", re.IGNORECASE)
# Desconto "-30%" / "30% Desconto".
_DISCOUNT_RE = re.compile(r"-?\s*(\d{1,2})\s*%")
# Validade: "11 a 17 de junho de 2026" / "11-17 jun".
_VALIDITY_RE = re.compile(
    r"(\d{1,2})\s*(?:a|[—–-])\s*(\d{1,2})\s+(?:de\s+)?([a-zà-ú]{3,})\.?"
    r"(?:\s+(?:de\s+)?(\d{4}))?",
    re.IGNORECASE,
)
_MESES = {
    "jan": 1, "fev": 2, "mar": 3, "abr": 4, "mai": 5, "jun": 6,
    "jul": 7, "ago": 8, "set": 9, "out": 10, "nov": 11, "dez": 12,
}

# Labels de promoção (não fazem parte do nome).
PROMO_LABELS = [
    "apenas", "poupe", "poupanca", "poupança", "desconto direto", "desconto",
    "sobre pvpr", "pvpr", "leve", "pague", "edição especial", "edicao especial",
    "cartão", "cartao", "vale", "oferta", "talão", "talao", "fica a",
]
# Ruído / rodapé que nunca é nome de produto.
_NOISE_RE = re.compile(
    r"^(página|pag\.|www\.|http|folheto|promo|válid|valid|desde|preço|preco|"
    r"unid|emb\.|cada|consulte|condições|condicoes|copyright|©|stock|imagens|"
    r"meramente|ilustrativ|\d{1,2}[./]\d{1,2})",
    re.IGNORECASE,
)

MIN_NAME_LEN = 3
MAX_NAME_LEN = 140
MIN_PRICE = 0.10
MAX_PRICE = 999.0


def parse_flyer_text(
    text: str,
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    """Compat: divide o texto em blocos (linhas em branco) e parseia."""
    blocks = [b for b in re.split(r"\n\s*\n", text) if b.strip()]
    return parse_blocks(blocks, supermarket=supermarket, source_url=source_url)


def parse_blocks(
    blocks: list[str],
    *,
    supermarket: str,
    source_url: Optional[str] = None,
) -> list[RawProduct]:
    """Parseia uma lista de blocos de texto (um candidato a produto cada)."""
    valid_from, valid_until = _extract_validity("\n".join(blocks))

    products: list[RawProduct] = []
    seen: set[str] = set()
    for block in blocks:
        product = _parse_block(block, supermarket, source_url, valid_from, valid_until)
        if product is None:
            continue
        key = f"{product.raw_name.lower()}::{product.price}"
        if key in seen:
            continue
        seen.add(key)
        products.append(product)

    logger.info("OCR: %d produtos extraídos de %d blocos (%s)",
                len(products), len(blocks), supermarket)
    return products


# --- Parsing de um bloco ----------------------------------------------------
def _parse_block(
    block: str,
    supermarket: str,
    source_url: Optional[str],
    valid_from: Optional[dt.date],
    valid_until: Optional[dt.date],
) -> Optional[RawProduct]:
    lines = [ln.strip() for ln in block.splitlines() if ln.strip()]
    if not lines:
        return None

    # Preços por ordem de leitura (1.º promocional, 2.º original se maior).
    prices = [_price_value(m) for ln in lines for m in _PRICE_RE.finditer(ln)]
    prices = [p for p in prices if MIN_PRICE <= p <= MAX_PRICE]
    if not prices:
        return None

    price = prices[0]
    original = next((p for p in prices[1:] if p > price), None)

    # Nome = linhas sem preço e sem label/ruído.
    name_lines = [
        ln for ln in lines
        if not _PRICE_RE.search(ln) and not _is_label_or_noise(ln)
    ]
    name = _clean(" ".join(name_lines))
    if not _is_valid_name(name):
        return None

    label = _promo_label(lines)
    discount = next((_DISCOUNT_RE.search(ln) for ln in lines if _DISCOUNT_RE.search(ln)), None)
    if not label and discount:
        label = f"-{discount.group(1)}%"

    return RawProduct(
        raw_name=name,
        price=price,
        supermarket=supermarket,
        original_price=original,
        is_promotion=original is not None or label is not None,
        promotion_label=label,
        source_url=source_url,
        valid_from=valid_from,
        valid_until=valid_until,
    )


# --- Helpers ----------------------------------------------------------------
def _price_value(m: re.Match) -> float:
    return float(f"{m.group(1)}.{m.group(2) or '00'}")


def _promo_label(lines: list[str]) -> Optional[str]:
    for ln in lines:
        low = ln.lower()
        for lbl in PROMO_LABELS:
            if lbl in low:
                return ln.strip()[:100]
    return None


def _extract_validity(text: str) -> tuple[Optional[dt.date], Optional[dt.date]]:
    m = _VALIDITY_RE.search(text)
    if not m:
        return None, None
    d1, d2 = int(m.group(1)), int(m.group(2))
    month = _MESES.get(m.group(3).lower()[:3])
    year = int(m.group(4)) if m.group(4) else dt.date.today().year
    if not month:
        return None, None
    try:
        start, end = dt.date(year, month, d1), dt.date(year, month, d2)
        if end < start:
            return None, None
        logger.info("Validade do folheto: %s a %s", start, end)
        return start, end
    except ValueError:
        return None, None


def _is_valid_name(text: str) -> bool:
    if not (MIN_NAME_LEN <= len(text) <= MAX_NAME_LEN):
        return False
    if _is_label_or_noise(text):
        return False
    # Tem de ter letras (não ser só números/símbolos).
    return bool(re.search(r"[a-zà-ú]{2,}", text, re.IGNORECASE))


def _is_label_or_noise(text: str) -> bool:
    low = text.lower()
    if _NOISE_RE.search(text):
        return True
    return any(lbl in low for lbl in PROMO_LABELS)


def _clean(text: str) -> str:
    return re.sub(r"\s{2,}", " ", text).strip(" -–·|.,")
