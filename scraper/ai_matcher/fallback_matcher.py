"""Matcher local de recurso (fallback) sem Claude API.

Usa `sentence-transformers` para comparar o nome bruto com os candidatos por
similaridade semântica de embeddings. É menos preciso que o Claude (não
raciocina sobre variantes/gramagens), por isso é deliberadamente conservador:
nunca aceita automaticamente — no máximo encaminha para revisão humana.

O modelo de embeddings é carregado de forma preguiçosa e é injetável, para que
os testes possam usar um codificador falso sem instalar o pacote pesado.
"""
from __future__ import annotations

import logging
import math
import re
from typing import Optional, Protocol, Sequence

from .models import (
    CONFIDENCE_AUTO_MATCH,
    CandidateProduct,
    ExtractedFields,
    MatchDecision,
    MatchResult,
    RawProduct,
    classify,
)

logger = logging.getLogger(__name__)

DEFAULT_EMBED_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"

_WEIGHT_RE = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*(kg|g|gr|grs|ml|cl|l|lt)\b", re.IGNORECASE
)


class Encoder(Protocol):
    """Interface mínima de um codificador de frases."""

    def encode(self, sentences: list[str]) -> list[list[float]]: ...


class FallbackMatcher:
    """Matching por similaridade de embeddings (sem IA generativa)."""

    def __init__(
        self,
        *,
        model_name: str = DEFAULT_EMBED_MODEL,
        encoder: Optional[Encoder] = None,
        # Mesmo com alta similaridade, o fallback nunca aceita sozinho;
        # tudo o que passa daqui vai no máximo para revisão.
        max_auto_confidence: float = CONFIDENCE_AUTO_MATCH - 0.01,
    ) -> None:
        self.model_name = model_name
        self._encoder = encoder
        self._max_auto_confidence = max_auto_confidence

    # -- carregamento preguiçoso do modelo ---------------------------------
    @property
    def encoder(self) -> Encoder:
        if self._encoder is None:
            from sentence_transformers import SentenceTransformer  # lazy

            logger.info("A carregar modelo de embeddings %s", self.model_name)
            self._encoder = SentenceTransformer(self.model_name)
        return self._encoder

    # -- API pública --------------------------------------------------------
    def match_batch(
        self,
        raw_products: Sequence[RawProduct],
        candidates: Sequence[CandidateProduct],
    ) -> list[MatchResult]:
        if not raw_products:
            return []

        if not candidates:
            return [self._new_product(rp) for rp in raw_products]

        raw_vecs = self.encoder.encode([rp.raw_name for rp in raw_products])
        cand_vecs = self.encoder.encode([c.display_name for c in candidates])

        results: list[MatchResult] = []
        for rp, rvec in zip(raw_products, raw_vecs):
            best_i, best_sim = _best_match(rvec, cand_vecs)
            results.append(self._build(rp, candidates, best_i, best_sim))
        return results

    # -- construção do resultado -------------------------------------------
    def _build(
        self,
        rp: RawProduct,
        candidates: Sequence[CandidateProduct],
        best_i: int,
        similarity: float,
    ) -> MatchResult:
        candidate = candidates[best_i]
        # Penaliza se as gramagens detetadas diferirem (heurística simples).
        raw_weight = _extract_weight(rp.raw_name)
        cand_weight = _extract_weight(candidate.display_name)
        if raw_weight and cand_weight and raw_weight != cand_weight:
            similarity *= 0.5

        confidence = min(similarity, self._max_auto_confidence)
        product_id = candidate.product_id if confidence >= 0.60 else None
        decision = classify(product_id, confidence)

        # Garante que nunca há auto-match vindo do fallback.
        if decision is MatchDecision.AUTO_MATCH:
            decision = MatchDecision.NEEDS_REVIEW

        return MatchResult(
            raw_product=rp,
            extracted=ExtractedFields(weight=raw_weight),
            product_id=product_id,
            canonical_name=candidate.canonical_name if product_id else _slug(rp.raw_name),
            display_name=candidate.display_name if product_id else rp.raw_name,
            confidence=round(confidence, 3),
            decision=decision,
            reasoning=f"Similaridade de embeddings {similarity:.2f} (fallback local)",
            source="fallback",
        )

    def _new_product(self, rp: RawProduct) -> MatchResult:
        return MatchResult(
            raw_product=rp,
            extracted=ExtractedFields(weight=_extract_weight(rp.raw_name)),
            product_id=None,
            canonical_name=_slug(rp.raw_name),
            display_name=rp.raw_name,
            confidence=0.0,
            decision=MatchDecision.NEW_PRODUCT,
            reasoning="Sem candidatos (fallback local)",
            source="fallback",
        )


# --- Helpers ----------------------------------------------------------------
def cosine_similarity(a: Sequence[float], b: Sequence[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


def _best_match(
    raw_vec: Sequence[float], cand_vecs: Sequence[Sequence[float]]
) -> tuple[int, float]:
    best_i, best_sim = 0, -1.0
    for i, cv in enumerate(cand_vecs):
        sim = cosine_similarity(raw_vec, cv)
        if sim > best_sim:
            best_i, best_sim = i, sim
    return best_i, max(0.0, best_sim)


def _extract_weight(name: str) -> Optional[str]:
    """Normaliza gramagem/volume para comparação. Ex.: '1,5 L' -> '1500ml'."""
    m = _WEIGHT_RE.search(name)
    if not m:
        return None
    value = float(m.group(1).replace(",", "."))
    unit = m.group(2).lower()
    grams_or_ml = {
        "kg": value * 1000,
        "g": value,
        "gr": value,
        "grs": value,
        "l": value * 1000,
        "lt": value * 1000,
        "cl": value * 10,
        "ml": value,
    }[unit]
    suffix = "ml" if unit in {"ml", "cl", "l", "lt"} else "g"
    return f"{int(round(grams_or_ml))}{suffix}"


def _slug(name: str) -> str:
    s = name.lower().strip()
    s = re.sub(r"[^\w\s-]", "", s)
    s = re.sub(r"[\s-]+", "_", s)
    return s[:200] or "produto"
