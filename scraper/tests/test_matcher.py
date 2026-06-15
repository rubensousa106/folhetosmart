"""Testes unitários do AI matcher (sem rede — Claude API mockado)."""
from __future__ import annotations

import json
import re

import pytest

from ai_matcher import (
    BatchProcessor,
    CandidateProduct,
    ClaudeMatcher,
    ClaudeUnavailable,
    FallbackMatcher,
    MatchDecision,
    RawProduct,
    classify,
)
from ai_matcher.batch_processor import MatchCache
from ai_matcher.claude_matcher import _extract_json
from ai_matcher.fallback_matcher import _extract_weight, cosine_similarity
from ai_matcher.prompts import build_batch_user_message


CAND = [
    CandidateProduct(
        product_id="11111111-1111-1111-1111-111111111111",
        canonical_name="doritos_150g",
        display_name="Doritos 150g",
        brand="Doritos",
        weight_grams=150,
    ),
]


# --- Mock do cliente anthropic ---------------------------------------------
class _Block:
    type = "text"

    def __init__(self, text: str) -> None:
        self.text = text


class _Message:
    def __init__(self, text: str) -> None:
        self.content = [_Block(text)]


class FakeMessages:
    def __init__(self, payload: str) -> None:
        self._payload = payload
        self.calls = 0

    def create(self, **kwargs):
        self.calls += 1
        return _Message(self._payload)


class FakeClient:
    def __init__(self, payload: str) -> None:
        self.messages = FakeMessages(payload)


def _claude_with(payload: str) -> ClaudeMatcher:
    return ClaudeMatcher(client=FakeClient(payload))


# --- classify() ------------------------------------------------------------
def test_classify_thresholds():
    pid = "abc"
    assert classify(pid, 0.90) is MatchDecision.AUTO_MATCH
    assert classify(pid, 0.85) is MatchDecision.AUTO_MATCH
    assert classify(pid, 0.70) is MatchDecision.NEEDS_REVIEW
    assert classify(pid, 0.60) is MatchDecision.NEEDS_REVIEW
    assert classify(pid, 0.40) is MatchDecision.NEW_PRODUCT
    # Sem candidato é sempre novo produto.
    assert classify(None, 0.99) is MatchDecision.NEW_PRODUCT


# --- _extract_json() -------------------------------------------------------
def test_extract_json_plain():
    assert _extract_json('{"a": 1}') == {"a": 1}


def test_extract_json_with_fence_and_noise():
    text = 'Claro!\n```json\n{"results": [1, 2]}\n```\nFim.'
    assert _extract_json(text) == {"results": [1, 2]}


def test_extract_json_recorta_chavetas():
    text = 'lixo antes {"x": true} lixo depois'
    assert _extract_json(text) == {"x": True}


def test_extract_json_invalido():
    assert _extract_json("não há json aqui") is None


# --- ClaudeMatcher: parsing ------------------------------------------------
def _payload_match(product_id, confidence=0.92):
    return json.dumps(
        {
            "results": [
                {
                    "index": 0,
                    "extracted": {
                        "brand": "Doritos",
                        "base_name": "Doritos",
                        "weight": "150g",
                        "variant": "Chilli",
                    },
                    "match": {
                        "product_id": product_id,
                        "canonical_name": "doritos_150g",
                        "display_name": "Doritos 150g",
                        "confidence": confidence,
                        "reasoning": "Mesma marca e gramagem.",
                    },
                }
            ]
        }
    )


def test_match_batch_auto_match():
    matcher = _claude_with(_payload_match(CAND[0].product_id, 0.92))
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    [res] = matcher.match_batch(raw, CAND)

    assert res.product_id == CAND[0].product_id
    assert res.decision is MatchDecision.AUTO_MATCH
    assert res.confidence == 0.92
    assert res.extracted.variant == "Chilli"
    assert res.source == "claude"


def test_match_batch_ignora_product_id_inventado():
    # A IA devolve um id que não existe nos candidatos -> deve ser anulado.
    matcher = _claude_with(_payload_match("id-que-nao-existe", 0.95))
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    [res] = matcher.match_batch(raw, CAND)

    assert res.product_id is None
    assert res.decision is MatchDecision.NEW_PRODUCT


def test_match_batch_confidence_clamped():
    matcher = _claude_with(_payload_match(CAND[0].product_id, 1.7))
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    [res] = matcher.match_batch(raw, CAND)
    assert res.confidence == 1.0


def test_match_batch_resposta_sem_json_levanta_unavailable():
    matcher = _claude_with("desculpa, não consigo")
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    with pytest.raises(ClaudeUnavailable):
        matcher.match_batch(raw, CAND)


# --- BatchProcessor: fallback + batching -----------------------------------
class _RaisingPrimary:
    def match_batch(self, raw, cand):
        raise ClaudeUnavailable("boom")


class _StubFallback:
    def __init__(self):
        self.seen = 0

    def match_batch(self, raw, cand):
        self.seen += len(raw)
        from ai_matcher.models import ExtractedFields, MatchResult

        return [
            MatchResult(
                raw_product=rp,
                extracted=ExtractedFields(),
                product_id=None,
                canonical_name="x",
                display_name=rp.raw_name,
                confidence=0.0,
                decision=MatchDecision.NEW_PRODUCT,
                source="fallback",
            )
            for rp in raw
        ]


def test_batch_processor_cai_para_fallback():
    fallback = _StubFallback()
    proc = BatchProcessor(primary=_RaisingPrimary(), fallback=fallback)
    raw = [RawProduct(f"P{i}", 1.0, "lidl") for i in range(3)]

    results = proc.process(raw, CAND)

    assert fallback.seen == 3
    assert all(r.source == "fallback" for r in results)


def test_batch_processor_sem_fallback_propaga():
    proc = BatchProcessor(primary=_RaisingPrimary(), fallback=None)
    raw = [RawProduct("P", 1.0, "lidl")]
    with pytest.raises(ClaudeUnavailable):
        proc.process(raw, CAND)


def test_batch_processor_respeita_tamanho_de_batch():
    calls = []

    class _CountingPrimary:
        def match_batch(self, raw, cand):
            calls.append(len(raw))
            from ai_matcher.models import ExtractedFields, MatchResult

            return [
                MatchResult(
                    raw_product=rp,
                    extracted=ExtractedFields(),
                    canonical_name="x",
                    display_name=rp.raw_name,
                    confidence=1.0,
                    decision=MatchDecision.NEW_PRODUCT,
                )
                for rp in raw
            ]

    proc = BatchProcessor(primary=_CountingPrimary(), batch_size=20)
    raw = [RawProduct(f"P{i}", 1.0, "lidl") for i in range(45)]
    proc.process(raw, CAND)

    assert calls == [20, 20, 5]


def test_match_cache_round_trip(tmp_path):
    cache_path = tmp_path / "cache.json"
    cache = MatchCache(cache_path)
    rp = RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")

    from ai_matcher.models import ExtractedFields, MatchResult

    cache.put(
        rp,
        MatchResult(
            raw_product=rp,
            extracted=ExtractedFields(),
            product_id="pid",
            canonical_name="doritos_150g",
            display_name="Doritos 150g",
            confidence=0.9,
            decision=MatchDecision.AUTO_MATCH,
        ),
    )
    cache.flush()

    # Recarrega de disco e processa: não deve chamar o primary.
    reloaded = MatchCache(cache_path)

    class _Boom:
        def match_batch(self, *a):
            raise AssertionError("não devia ser chamado — está em cache")

    proc = BatchProcessor(primary=_Boom(), cache=reloaded)
    [res] = proc.process([rp], CAND)
    assert res.source == "cache"
    assert res.product_id == "pid"


# --- prompts ---------------------------------------------------------------
def test_build_batch_user_message_inclui_dados():
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    msg = build_batch_user_message(raw, CAND)
    assert "Doritos Chilli 150g" in msg
    assert CAND[0].product_id in msg
    assert "pingo-doce" in msg


# --- FallbackMatcher (encoder falso, sem sentence-transformers) ------------
class FakeEncoder:
    """Bag-of-words sobre um vocabulário fixo -> cosseno significativo."""

    VOCAB = ["doritos", "chilli", "spicy", "150g", "leite", "mimosa", "1l"]

    def encode(self, sentences):
        vecs = []
        for s in sentences:
            toks = set(re.findall(r"\w+", s.lower()))
            vecs.append([1.0 if w in toks else 0.0 for w in self.VOCAB])
        return vecs


def test_fallback_nunca_faz_auto_match():
    fb = FallbackMatcher(encoder=FakeEncoder())
    raw = [RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce")]
    [res] = fb.match_batch(raw, CAND)

    assert res.source == "fallback"
    # Alta similaridade, mas o fallback é conservador: no máximo revisão.
    assert res.decision is MatchDecision.NEEDS_REVIEW
    assert res.confidence < 0.85


def test_fallback_sem_candidatos_cria_novo():
    fb = FallbackMatcher(encoder=FakeEncoder())
    raw = [RawProduct("Produto Desconhecido", 1.0, "aldi")]
    [res] = fb.match_batch(raw, [])
    assert res.decision is MatchDecision.NEW_PRODUCT
    assert res.product_id is None


def test_extract_weight():
    assert _extract_weight("Doritos 150g") == "150g"
    assert _extract_weight("Leite 1L") == "1000ml"
    assert _extract_weight("Iogurte 1,5 L") == "1500ml"
    assert _extract_weight("Atum 3 x 56 g") == "56g"
    assert _extract_weight("Sem peso") is None


def test_cosine_similarity():
    assert cosine_similarity([1, 0], [1, 0]) == pytest.approx(1.0)
    assert cosine_similarity([1, 0], [0, 1]) == pytest.approx(0.0)
    assert cosine_similarity([0, 0], [1, 1]) == 0.0
