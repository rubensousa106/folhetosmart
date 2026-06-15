"""Orquestração de matching em batch.

Responsabilidades:
- partir a lista de produtos em batches (<= 20 por chamada, para controlar custo);
- tentar o `ClaudeMatcher` e, em caso de falha, cair para o `FallbackMatcher`;
- cache de matches já resolvidos (por supermercado + nome bruto) para não
  pagar a IA duas vezes pelo mesmo produto.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Optional, Sequence

from .claude_matcher import ClaudeMatcher, ClaudeUnavailable
from .fallback_matcher import FallbackMatcher
from .models import CandidateProduct, MatchResult, RawProduct

logger = logging.getLogger(__name__)

MAX_BATCH_SIZE = 20


class MatchCache:
    """Cache simples persistida em JSON: (supermercado, nome) -> match."""

    def __init__(self, path: Optional[Path] = None) -> None:
        self.path = path
        self._data: dict[str, dict] = {}
        if path and path.exists():
            try:
                self._data = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                logger.warning("Cache de matches ilegível em %s; a ignorar.", path)

    @staticmethod
    def _key(rp: RawProduct) -> str:
        return f"{rp.supermarket}::{rp.raw_name.strip().lower()}"

    def get(self, rp: RawProduct) -> Optional[dict]:
        return self._data.get(self._key(rp))

    def put(self, rp: RawProduct, result: MatchResult) -> None:
        self._data[self._key(rp)] = {
            "product_id": result.product_id,
            "canonical_name": result.canonical_name,
            "display_name": result.display_name,
            "confidence": result.confidence,
            "decision": result.decision.value,
        }

    def flush(self) -> None:
        if not self.path:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(
            json.dumps(self._data, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


class BatchProcessor:
    """Processa produtos em batch com fallback e cache."""

    def __init__(
        self,
        primary: ClaudeMatcher,
        fallback: Optional[FallbackMatcher] = None,
        *,
        batch_size: int = MAX_BATCH_SIZE,
        cache: Optional[MatchCache] = None,
    ) -> None:
        self.primary = primary
        self.fallback = fallback
        self.batch_size = min(batch_size, MAX_BATCH_SIZE)
        self.cache = cache

    def process(
        self,
        raw_products: Sequence[RawProduct],
        candidates: Sequence[CandidateProduct],
    ) -> list[MatchResult]:
        results: list[MatchResult] = []
        # Produtos ainda por resolver (não estavam em cache).
        pending: list[RawProduct] = []

        for rp in raw_products:
            cached = self.cache.get(rp) if self.cache else None
            if cached is not None:
                results.append(_result_from_cache(rp, cached))
            else:
                pending.append(rp)

        for batch in _chunks(pending, self.batch_size):
            results.extend(self._process_batch(batch, candidates))

        if self.cache:
            self.cache.flush()
        return results

    def _process_batch(
        self,
        batch: Sequence[RawProduct],
        candidates: Sequence[CandidateProduct],
    ) -> list[MatchResult]:
        try:
            batch_results = self.primary.match_batch(batch, candidates)
        except ClaudeUnavailable as exc:
            if not self.fallback:
                logger.error("Claude indisponível e sem fallback: %s", exc)
                raise
            logger.warning("A usar fallback local para %d produtos.", len(batch))
            batch_results = self.fallback.match_batch(batch, candidates)

        if self.cache:
            for r in batch_results:
                self.cache.put(r.raw_product, r)
        return batch_results


# --- Helpers ----------------------------------------------------------------
def _chunks(seq: Sequence[RawProduct], n: int):
    for i in range(0, len(seq), n):
        yield seq[i : i + n]


def _result_from_cache(rp: RawProduct, cached: dict) -> MatchResult:
    from .models import ExtractedFields, MatchDecision

    return MatchResult(
        raw_product=rp,
        extracted=ExtractedFields(),
        product_id=cached.get("product_id"),
        canonical_name=cached.get("canonical_name", rp.raw_name),
        display_name=cached.get("display_name", rp.raw_name),
        confidence=cached.get("confidence", 0.0),
        decision=MatchDecision(cached.get("decision", "needs_review")),
        reasoning="Resultado em cache",
        source="cache",
    )
