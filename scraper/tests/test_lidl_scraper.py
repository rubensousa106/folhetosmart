"""Testa o parser do scraper do Lidl com uma fixture (sem rede)."""
from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("parsel")  # skip limpo se o parsel não estiver instalado

from scrapers.lidl import LidlScraper  # noqa: E402

FIXTURE = Path(__file__).parent / "fixtures" / "lidl_flyer.html"


@pytest.fixture
def html() -> str:
    return FIXTURE.read_text(encoding="utf-8")


def test_parse_extrai_produtos(html):
    products = LidlScraper().parse(html)
    # 3 com preço; o cartão sem preço é ignorado.
    assert len(products) == 3
    names = {p.raw_name for p in products}
    assert "Doritos Tortilla Chips 150g" in names
    assert all(p.supermarket == "lidl" for p in products)


def test_parse_deteta_promocao(html):
    products = {p.raw_name: p for p in LidlScraper().parse(html)}

    doritos = products["Doritos Tortilla Chips 150g"]
    assert doritos.price == 1.39
    assert doritos.original_price == 1.99
    assert doritos.is_promotion is True
    assert doritos.promotion_label == "-30%"

    leite = products["Leite Meio-Gordo Mimosa 1L"]
    assert leite.price == 0.89
    assert leite.original_price is None
    assert leite.is_promotion is False
