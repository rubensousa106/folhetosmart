"""IA de normalização de produtos do FolhetoSmart.

O núcleo do projeto: usa a Claude API para decidir, semanticamente, quando
dois nomes de produto diferentes (em supermercados diferentes) são o mesmo
produto comparável.
"""
from .batch_processor import BatchProcessor, MatchCache
from .claude_matcher import ClaudeMatcher, ClaudeUnavailable
from .fallback_matcher import FallbackMatcher
from .models import (
    CandidateProduct,
    ExtractedFields,
    MatchDecision,
    MatchResult,
    RawProduct,
    classify,
)

__all__ = [
    "BatchProcessor",
    "MatchCache",
    "ClaudeMatcher",
    "ClaudeUnavailable",
    "FallbackMatcher",
    "CandidateProduct",
    "ExtractedFields",
    "MatchDecision",
    "MatchResult",
    "RawProduct",
    "classify",
]
