"""Matching semântico de produtos via Claude API.

Usa o SDK oficial `anthropic`. Recebe um batch de `RawProduct` + candidatos,
pede ao Claude um raciocínio estruturado e devolve `MatchResult` por produto.

Se a Claude API falhar, levanta `ClaudeUnavailable` para que o
`BatchProcessor` possa cair para o matcher local (sentence-transformers).
"""
from __future__ import annotations

import json
import logging
import os
import re
from typing import Optional, Sequence

import anthropic

from .models import (
    CandidateProduct,
    ExtractedFields,
    MatchResult,
    RawProduct,
    classify,
)
from .prompts import SYSTEM_PROMPT, build_batch_user_message

logger = logging.getLogger(__name__)

# O modelo original do projeto (claude-sonnet-4-20250514) foi reformado em
# junho de 2026 — o sucessor é o claude-sonnet-4-6. Configurável por env var.
DEFAULT_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-6")


class ClaudeUnavailable(RuntimeError):
    """A Claude API falhou de forma irrecuperável para este batch."""


class ClaudeMatcher:
    """Encapsula uma chamada de matching à Claude API."""

    def __init__(
        self,
        *,
        api_key: Optional[str] = None,
        model: str = DEFAULT_MODEL,
        max_tokens: int = 4096,
        client: Optional[anthropic.Anthropic] = None,
        insecure_tls: bool = False,
    ) -> None:
        # `client` injetável facilita os testes (mock).
        if client is None:
            http_client = None
            if insecure_tls:
                # Redes com inspeção TLS (cert intercetado): desliga a
                # verificação APENAS quando explicitamente pedido por env.
                import httpx  # transitivo do SDK anthropic

                http_client = httpx.Client(verify=False, timeout=120.0)
            client = anthropic.Anthropic(
                api_key=api_key or os.getenv("ANTHROPIC_API_KEY"),
                http_client=http_client,
            )
        self._client = client
        self.model = model
        self.max_tokens = max_tokens

    # -- API pública --------------------------------------------------------
    def match_batch(
        self,
        raw_products: Sequence[RawProduct],
        candidates: Sequence[CandidateProduct],
    ) -> list[MatchResult]:
        """Faz o matching de um batch (idealmente <= 20 produtos)."""
        if not raw_products:
            return []

        user_message = build_batch_user_message(raw_products, candidates)
        text = self._call_api(user_message)
        parsed = self._parse_response(text, expected=len(raw_products))
        return self._to_results(raw_products, candidates, parsed)

    # -- Chamada à API ------------------------------------------------------
    def _call_api(self, user_message: str) -> str:
        try:
            message = self._client.messages.create(
                model=self.model,
                max_tokens=self.max_tokens,
                system=SYSTEM_PROMPT,
                messages=[{"role": "user", "content": user_message}],
            )
        except (anthropic.APIError, anthropic.APIConnectionError) as exc:
            # Inclui rate limit, overload, erros de servidor e de rede.
            logger.warning("Claude API indisponível: %s", exc)
            raise ClaudeUnavailable(str(exc)) from exc

        # A resposta é uma lista de blocos; juntamos o texto.
        return "".join(
            block.text for block in message.content if block.type == "text"
        )

    # -- Parsing defensivo de JSON -----------------------------------------
    @staticmethod
    def _parse_response(text: str, *, expected: int) -> list[dict]:
        payload = _extract_json(text)
        if payload is None:
            raise ClaudeUnavailable("Resposta do Claude não continha JSON válido")

        results = payload.get("results")
        if not isinstance(results, list):
            raise ClaudeUnavailable("JSON do Claude sem campo 'results'")

        if len(results) != expected:
            logger.warning(
                "Claude devolveu %d resultados, esperados %d",
                len(results),
                expected,
            )
        return results

    def _to_results(
        self,
        raw_products: Sequence[RawProduct],
        candidates: Sequence[CandidateProduct],
        parsed: list[dict],
    ) -> list[MatchResult]:
        valid_ids = {c.product_id for c in candidates}
        by_index = {item.get("index", i): item for i, item in enumerate(parsed)}

        results: list[MatchResult] = []
        for i, rp in enumerate(raw_products):
            item = by_index.get(i)
            if item is None:
                # A IA não devolveu este índice -> revisão humana.
                results.append(_unmatched_result(rp, "Sem resposta da IA"))
                continue
            results.append(self._build_one(rp, item, valid_ids))
        return results

    @staticmethod
    def _build_one(
        rp: RawProduct, item: dict, valid_ids: set[str]
    ) -> MatchResult:
        extracted = item.get("extracted") or {}
        match = item.get("match") or {}

        product_id = match.get("product_id")
        # Defesa: a IA por vezes inventa product_id; só aceitamos os reais.
        if product_id is not None and product_id not in valid_ids:
            logger.debug("product_id desconhecido ignorado: %s", product_id)
            product_id = None

        try:
            confidence = float(match.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        confidence = max(0.0, min(1.0, confidence))

        decision = classify(product_id, confidence)

        return MatchResult(
            raw_product=rp,
            extracted=ExtractedFields(
                brand=extracted.get("brand"),
                base_name=extracted.get("base_name"),
                weight=extracted.get("weight"),
                variant=extracted.get("variant"),
            ),
            product_id=product_id,
            canonical_name=match.get("canonical_name") or _slug(rp.raw_name),
            display_name=match.get("display_name") or rp.raw_name,
            confidence=confidence,
            decision=decision,
            reasoning=match.get("reasoning", ""),
            source="claude",
        )


# --- Helpers ----------------------------------------------------------------
_JSON_FENCE = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL)


def _extract_json(text: str) -> Optional[dict]:
    """Extrai um objeto JSON de uma resposta, tolerante a ```fences``` e ruído."""
    if not text:
        return None

    # 1) Bloco em fence markdown.
    fence = _JSON_FENCE.search(text)
    candidate = fence.group(1) if fence else text

    # 2) Tentativa direta.
    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        pass

    # 3) Recorta do primeiro '{' ao último '}' e tenta de novo.
    start = candidate.find("{")
    end = candidate.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(candidate[start : end + 1])
        except json.JSONDecodeError:
            return None
    return None


def _slug(name: str) -> str:
    s = name.lower().strip()
    s = re.sub(r"[^\w\s-]", "", s)
    s = re.sub(r"[\s-]+", "_", s)
    return s[:200] or "produto"


def _unmatched_result(rp: RawProduct, reason: str) -> MatchResult:
    return MatchResult(
        raw_product=rp,
        extracted=ExtractedFields(),
        product_id=None,
        canonical_name=_slug(rp.raw_name),
        display_name=rp.raw_name,
        confidence=0.0,
        decision=classify(None, 0.0),
        reasoning=reason,
        source="claude",
        error=reason,
    )
