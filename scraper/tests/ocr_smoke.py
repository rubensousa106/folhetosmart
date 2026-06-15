"""Smoke-test do pipeline OCR (corre dentro do container, NÃO no pytest).

Com um PDF real:
    docker exec folhetosmart-scraper python tests/ocr_smoke.py /tmp/continente.pdf
Sem argumento, gera um folheto sintético e testa esse.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

from ocr.extractor import extract_flyer_products_pdf

PRODUTOS = [
    ("Iogurte Activia Natural 4x125g", "0,99", "1,29"),
    ("Leite Mimosa Meio Gordo 1L", "0,95", None),
    ("Azeite Gallo Virgem Extra 750ml", "6,89", "8,99"),
    ("Cafe Delta Lote Chavena 250g", "3,79", "4,29"),
    ("Arroz Cigala Agulha 1kg", "1,15", None),
    ("Esparguete Milaneza 500g", "0,89", "0,99"),
    ("Atum Bom Petisco Azeite 120g", "1,29", "1,55"),
    ("Bolachas Maria Nacional 200g", "0,79", None),
    ("Cerveja Super Bock 6x33cl", "3,49", "3,99"),
    ("Agua Luso 6x1,5L", "2,39", None),
    ("Detergente Skip 30 Capsulas", "8,99", "11,99"),
    ("Papel Higienico Renova 12 Rolos", "4,99", "5,99"),
    ("Manteiga Mimosa 250g", "1,89", "2,19"),
    ("Cereais Nestle Estrelitas 300g", "2,49", "2,99"),
    ("Sumo Compal Laranja 1L", "1,39", None),
]


def _build_synthetic_pdf(path: Path) -> None:
    from PIL import Image, ImageDraw, ImageFont

    W, H = 3508, 2480  # A4 landscape ~300 DPI
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)

    def font(sz):
        try:
            return ImageFont.truetype("DejaVuSans-Bold.ttf", sz)
        except Exception:
            return ImageFont.load_default(size=sz)

    d.text((120, 80), "Promocoes validas 11 a 17 de junho de 2026", fill="black", font=font(70))
    cols, col_w, row_h = 3, W // 3, 460
    x0, y0 = 120, 260
    for i, (name, price, original) in enumerate(PRODUTOS):
        cx, cy = x0 + (i % cols) * col_w, y0 + (i // cols) * row_h
        if original:
            d.text((cx, cy), "Desconto Direto", fill="black", font=font(48))
        d.text((cx, cy + 70), name, fill="black", font=font(54))
        d.text((cx, cy + 170), f"{price} EUR", fill="black", font=font(96))
        if original:
            d.text((cx, cy + 300), f"{original} EUR", fill="black", font=font(54))
    img.save(str(path), "PDF", resolution=300.0)


def _report(pdf: str, expected: int | None) -> None:
    # Usa a orquestradora real: visão (Claude) com fallback OCR Tesseract.
    products = extract_flyer_products_pdf(pdf, supermarket="continente")

    print(f"\n=== {len(products)} produtos extraídos ===")
    for p in products[:10]:
        orig = f"  (era {p.original_price}€)" if p.original_price else ""
        promo = "  [promo]" if p.is_promotion else ""
        print(f"  • {p.raw_name} — {p.price}€{orig}{promo}")

    if expected:
        print(f"\nTaxa: {len(products)}/{expected} = {len(products)/expected:.0%}")


def main() -> None:
    if len(sys.argv) > 1 and Path(sys.argv[1]).exists():
        print(f"A testar PDF real: {sys.argv[1]}")
        _report(sys.argv[1], expected=45)
    else:
        with tempfile.TemporaryDirectory() as tmp:
            pdf = Path(tmp) / "folheto_teste.pdf"
            _build_synthetic_pdf(pdf)
            print(f"PDF sintético: {len(PRODUTOS)} produtos esperados")
            _report(str(pdf), expected=len(PRODUTOS))


if __name__ == "__main__":
    main()
