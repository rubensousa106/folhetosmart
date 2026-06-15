"""Modelos de dados do AI matcher.

Estes dataclasses são a fronteira entre o scraper (produz `RawProduct`),
a base de dados (fornece `CandidateProduct`) e o resultado de matching
(`MatchResult`) que o pipeline grava de volta.
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


# Limiares de decisão (ver README, Tarefa 1).
CONFIDENCE_AUTO_MATCH = 0.85   # >= aceita automaticamente
CONFIDENCE_REVIEW = 0.60       # >= 0.60 e < 0.85 -> revisão humana
#                              # < 0.60 -> novo produto canónico


class MatchDecision(str, Enum):
    """O que o pipeline deve fazer com um resultado de matching."""

    AUTO_MATCH = "auto_match"        # ligar ao produto canónico existente
    NEEDS_REVIEW = "needs_review"    # guardar para revisão humana
    NEW_PRODUCT = "new_product"      # criar novo produto canónico


@dataclass(slots=True)
class RawProduct:
    """Produto tal como saiu do folheto de um supermercado."""

    raw_name: str
    price: float
    supermarket: str                       # slug, ex.: "lidl"
    original_price: Optional[float] = None
    is_promotion: bool = False
    promotion_label: Optional[str] = None
    source_url: Optional[str] = None
    # Identificador opcional para correlacionar a resposta da IA de volta
    # ao item certo quando se processa em batch.
    external_id: Optional[str] = None
    # Validade extraída do próprio folheto (quando o scraper a consegue ler);
    # se None, a persistência usa a janela semanal por omissão.
    valid_from: Optional[dt.date] = None
    valid_until: Optional[dt.date] = None


@dataclass(slots=True)
class CandidateProduct:
    """Produto canónico já existente na BD, candidato a um match."""

    product_id: str
    canonical_name: str
    display_name: str
    brand: Optional[str] = None
    category: Optional[str] = None
    weight_grams: Optional[int] = None

    def to_prompt_dict(self) -> dict:
        """Forma compacta enviada ao Claude (menos tokens = menos custo)."""
        d = {
            "product_id": self.product_id,
            "canonical_name": self.canonical_name,
            "display_name": self.display_name,
        }
        if self.brand:
            d["brand"] = self.brand
        if self.weight_grams:
            d["weight_grams"] = self.weight_grams
        return d


@dataclass(slots=True)
class ExtractedFields:
    """Atributos estruturados que a IA extraiu do nome bruto."""

    brand: Optional[str] = None
    base_name: Optional[str] = None
    weight: Optional[str] = None
    variant: Optional[str] = None


@dataclass(slots=True)
class MatchResult:
    """Resultado final de matching para um `RawProduct`."""

    raw_product: RawProduct
    extracted: ExtractedFields
    canonical_name: str
    display_name: str
    confidence: float
    decision: MatchDecision
    product_id: Optional[str] = None      # preenchido se houve match
    reasoning: str = ""
    source: str = "claude"                # "claude" | "fallback"
    error: Optional[str] = None

    @property
    def auto_accepted(self) -> bool:
        return self.decision is MatchDecision.AUTO_MATCH

    @property
    def needs_review(self) -> bool:
        return self.decision is MatchDecision.NEEDS_REVIEW


def classify(
    product_id: Optional[str],
    confidence: float,
    *,
    auto_threshold: float = CONFIDENCE_AUTO_MATCH,
    review_threshold: float = CONFIDENCE_REVIEW,
) -> MatchDecision:
    """Aplica os limiares de confiança da Tarefa 1.

    - Sem candidato (`product_id is None`) -> novo produto canónico.
    - confiança >= 0.85 -> match automático.
    - 0.60 <= confiança < 0.85 -> revisão humana.
    - confiança < 0.60 (match fraco) -> tratar como novo produto.
    """
    if product_id is None:
        return MatchDecision.NEW_PRODUCT
    if confidence >= auto_threshold:
        return MatchDecision.AUTO_MATCH
    if confidence >= review_threshold:
        return MatchDecision.NEEDS_REVIEW
    return MatchDecision.NEW_PRODUCT
