"""Demo end-to-end do AI matcher.

Uso:
    export ANTHROPIC_API_KEY=sk-ant-...
    python -m ai_matcher.demo

Mostra o caso central do projeto: "Doritos Chilli 150g" (Pingo Doce) e
"Doritos Tortilla Spicy 150g" (Continente) devem normalizar para o mesmo
produto canónico.
"""
from __future__ import annotations

import logging
import os
import sys

from .batch_processor import BatchProcessor
from .claude_matcher import ClaudeMatcher
from .fallback_matcher import FallbackMatcher
from .models import CandidateProduct, RawProduct

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")


CANDIDATES = [
    CandidateProduct(
        product_id="11111111-1111-1111-1111-111111111111",
        canonical_name="doritos_150g",
        display_name="Doritos 150g",
        brand="Doritos",
        weight_grams=150,
    ),
    CandidateProduct(
        product_id="22222222-2222-2222-2222-222222222222",
        canonical_name="leite_meio_gordo_mimosa_1l",
        display_name="Leite Meio-Gordo Mimosa 1L",
        brand="Mimosa",
        weight_grams=1000,
    ),
]

RAW_PRODUCTS = [
    RawProduct("Doritos Chilli 150g", 1.39, "pingo-doce", original_price=1.99,
               is_promotion=True),
    RawProduct("Doritos Tortilla Spicy 150g", 1.49, "continente"),
    RawProduct("Leite Mimosa Meio Gordo 1 Litro", 0.89, "lidl"),
    RawProduct("Bolachas Maria Pingo Doce 200g", 0.45, "pingo-doce"),  # novo
]


def main() -> int:
    if not os.getenv("ANTHROPIC_API_KEY"):
        print("Define ANTHROPIC_API_KEY primeiro.", file=sys.stderr)
        return 1

    processor = BatchProcessor(
        primary=ClaudeMatcher(),
        fallback=FallbackMatcher(),  # só usado se a Claude API falhar
    )

    results = processor.process(RAW_PRODUCTS, CANDIDATES)

    print("\n=== Resultados de matching ===\n")
    for r in results:
        link = r.product_id or "(novo)"
        print(f"• {r.raw_product.raw_name!r} [{r.raw_product.supermarket}]")
        print(f"    -> {r.display_name}  conf={r.confidence:.2f}  "
              f"decisão={r.decision.value}  produto={link}")
        if r.reasoning:
            print(f"    razão: {r.reasoning}")
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
