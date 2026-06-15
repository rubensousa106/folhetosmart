"""OCR de folhetos com agrupamento espacial em blocos de produto.

O problema do `image_to_string` é colapsar o layout multi-coluna do folheto:
num folheto de 3-4 colunas o texto fica intercalado/partido e a extração
linha-a-linha falha. A abordagem correta usa `image_to_data` (palavras com
coordenadas + confiança) e reconstrói os blocos de produto por proximidade
espacial, respeitando as colunas (block/par do Tesseract).

Saída: lista de "blocos" (cada bloco = texto multi-linha de um candidato a
produto), que o `flyer_parser` transforma em RawProduct.

Requisitos de sistema (Dockerfile): tesseract-ocr + tesseract-ocr-por,
poppler-utils, libgomp1/libglib (OpenCV headless).
"""
from __future__ import annotations

import logging
from collections import OrderedDict
from pathlib import Path
from typing import Sequence, Union

logger = logging.getLogger(__name__)

OCR_LANG = "por"
# psm 3 = segmentação automática (deteta colunas/blocos); oem 3 = LSTM.
OCR_CONFIG = "--oem 3 --psm 3"
DEFAULT_DPI = 300
MIN_CONF = 60          # confiança mínima por palavra (0-100)
BLOCK_GAP_FACTOR = 1.8  # gap vertical > 1.8x altura de linha -> novo bloco


# --- API pública (blocos) ---------------------------------------------------
def extract_blocks_from_pdf(pdf_path: Union[str, Path], *, dpi: int = DEFAULT_DPI) -> list[str]:
    """Converte o PDF (300 DPI) e devolve os blocos de produto de todas as páginas."""
    from pdf2image import convert_from_path  # lazy

    pages = convert_from_path(str(pdf_path), dpi=dpi, fmt="png", thread_count=4)
    logger.info("OCR de %s — %d páginas a %d DPI", pdf_path, len(pages), dpi)

    blocks: list[str] = []
    for i, page in enumerate(pages, start=1):
        page_blocks = extract_blocks_from_image(page)
        logger.info("Página %d: %d blocos de texto", i, len(page_blocks))
        blocks.extend(page_blocks)
    return blocks


def extract_blocks_from_images(image_paths: Sequence[Union[str, Path]]) -> list[str]:
    """Blocos de produto a partir de imagens (screenshots de viewers)."""
    from PIL import Image  # lazy

    blocks: list[str] = []
    for i, path in enumerate(image_paths, start=1):
        page_blocks = extract_blocks_from_image(Image.open(path))
        logger.info("Imagem %d (%s): %d blocos", i, Path(path).name, len(page_blocks))
        blocks.extend(page_blocks)
    return blocks


def extract_blocks_from_image(pil_image) -> list[str]:
    """Pré-processa, corre OCR com coordenadas e agrupa em blocos espaciais."""
    words = _words_from_image(pil_image)
    lines = _lines_from_words(words)
    return _blocks_from_lines(lines)


# --- API pública (texto — compatibilidade) ----------------------------------
def extract_text_from_pdf(pdf_path: Union[str, Path], *, dpi: int = DEFAULT_DPI) -> str:
    return "\n\n".join(extract_blocks_from_pdf(pdf_path, dpi=dpi))


def extract_text_from_images(image_paths: Sequence[Union[str, Path]]) -> str:
    return "\n\n".join(extract_blocks_from_images(image_paths))


def extract_text_from_image(image_path: Union[str, Path]) -> str:
    from PIL import Image  # lazy

    return "\n\n".join(extract_blocks_from_image(Image.open(image_path)))


# --- Núcleo: OCR com coordenadas + agrupamento espacial ---------------------
def _words_from_image(pil_image) -> list[dict]:
    """Palavras com posição e (block, par, line) do Tesseract, filtradas por conf."""
    import pytesseract  # lazy
    from pytesseract import Output  # lazy

    processed = _preprocess(pil_image)
    data = pytesseract.image_to_data(
        processed, lang=OCR_LANG, config=OCR_CONFIG, output_type=Output.DICT
    )

    words: list[dict] = []
    for i in range(len(data["text"])):
        text = (data["text"][i] or "").strip()
        if not text:
            continue
        try:
            conf = float(data["conf"][i])
        except (TypeError, ValueError):
            conf = -1.0
        if conf < MIN_CONF:
            continue
        words.append({
            "text": text,
            "left": int(data["left"][i]),
            "top": int(data["top"][i]),
            "height": int(data["height"][i]),
            "block": int(data["block_num"][i]),
            "par": int(data["par_num"][i]),
            "line": int(data["line_num"][i]),
            "word": int(data["word_num"][i]),
        })
    return words


def _lines_from_words(words: list[dict]) -> list[dict]:
    """Junta palavras na mesma linha (chave block/par/line do Tesseract)."""
    groups: "OrderedDict[tuple, list]" = OrderedDict()
    for w in words:
        groups.setdefault((w["block"], w["par"], w["line"]), []).append(w)

    lines: list[dict] = []
    for (block, par, _line), ws in groups.items():
        ws.sort(key=lambda w: w["word"])
        lines.append({
            "text": " ".join(w["text"] for w in ws),
            "top": min(w["top"] for w in ws),
            "bottom": max(w["top"] + w["height"] for w in ws),
            "block": block,
            "par": par,
        })
    # Ordena por coluna/região e depois verticalmente.
    lines.sort(key=lambda ln: (ln["block"], ln["par"], ln["top"]))
    return lines


def _blocks_from_lines(lines: list[dict]) -> list[str]:
    """Agrupa linhas em blocos: mesma região (block/par) e gap vertical pequeno.

    Um parágrafo do Tesseract corresponde tipicamente a um produto; ainda assim
    dividimos por gap vertical grande, caso dois produtos caiam no mesmo par.
    """
    if not lines:
        return []

    heights = sorted(ln["bottom"] - ln["top"] for ln in lines)
    median_h = heights[len(heights) // 2] or 30
    block_gap = median_h * BLOCK_GAP_FACTOR

    blocks: list[list[str]] = []
    current: list[str] = [lines[0]["text"]]
    for prev, line in zip(lines, lines[1:]):
        same_region = (line["block"], line["par"]) == (prev["block"], prev["par"])
        gap = line["top"] - prev["bottom"]
        if same_region and gap < block_gap:
            current.append(line["text"])
        else:
            blocks.append(current)
            current = [line["text"]]
    blocks.append(current)

    return ["\n".join(b) for b in blocks]


def _preprocess(pil_image):
    """Cinzento -> CLAHE (contraste local) -> denoise. Melhora muito o OCR."""
    import cv2  # lazy
    import numpy as np  # lazy

    img = np.array(pil_image.convert("RGB"))
    gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
    enhanced = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    return cv2.fastNlMeansDenoising(enhanced, h=10)
