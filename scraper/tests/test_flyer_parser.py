"""Testa o parser de texto OCR de folhetos (sem precisar de OCR real)."""
from __future__ import annotations

import datetime as dt

from ocr.flyer_parser import parse_flyer_text

# Texto como sairia do OCR de um folheto (nome, preço promocional, preço
# original riscado, label de promoção, validade no topo).
FOLHETO_OCR = """\
Promoções válidas 11 a 17 de junho de 2026

33% Desconto Direto
Iogurte Natural Activia 4x125g
0,99 €
1,29 €

Leite Mimosa Meio Gordo 1L
0,95 €

Azeite Gallo Virgem Extra 750ml
6,89 €
8,99 €

Bolachas Maria Nacional 200g
0,79 €
"""


def _by_name(products):
    return {p.raw_name: p for p in products}


def test_extrai_multiplos_produtos():
    products = parse_flyer_text(FOLHETO_OCR, supermarket="continente")
    # 4 produtos reais; "8,99 €" (preço riscado do azeite) NÃO vira produto.
    assert len(products) == 4
    assert all(p.supermarket == "continente" for p in products)


def test_precos_e_promocoes():
    p = _by_name(parse_flyer_text(FOLHETO_OCR, supermarket="continente"))

    iogurte = p["Iogurte Natural Activia 4x125g"]
    assert iogurte.price == 0.99
    assert iogurte.original_price == 1.29
    assert iogurte.is_promotion is True
    assert iogurte.promotion_label is not None  # "33% Desconto Direto"

    azeite = p["Azeite Gallo Virgem Extra 750ml"]
    assert azeite.price == 6.89
    assert azeite.original_price == 8.99
    assert azeite.is_promotion is True

    leite = p["Leite Mimosa Meio Gordo 1L"]
    assert leite.price == 0.95
    assert leite.original_price is None


def test_validade_global_extraida():
    products = parse_flyer_text(FOLHETO_OCR, supermarket="continente")
    # Todos os produtos herdam a validade do cabeçalho do folheto.
    assert all(p.valid_from == dt.date(2026, 6, 11) for p in products)
    assert all(p.valid_until == dt.date(2026, 6, 17) for p in products)


def test_preco_riscado_nao_vira_produto():
    names = {p.raw_name for p in parse_flyer_text(FOLHETO_OCR, supermarket="continente")}
    # "8,99 €" é o preço original do azeite, não um produto autónomo.
    assert not any(n.strip() in {"", "€", "8,99"} for n in names)
