# scraper/ocr/__init__.py
"""OCR de folhetos: agrupamento espacial em blocos de produto + texto (compat)."""

import sys
import os

# Adiciona a pasta raiz do scraper ao path para importar do ficheiro principal
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Importa do ficheiro principal (raiz)
from pdf_extractor import extract_blocks_from_image
from pdf_extractor import extract_blocks_from_images
from pdf_extractor import extract_blocks_from_pdf
from pdf_extractor import extract_text_from_image
from pdf_extractor import extract_text_from_images
from pdf_extractor import extract_text_from_pdf

__all__ = [
    "extract_blocks_from_pdf",
    "extract_blocks_from_images",
    "extract_blocks_from_image",
    "extract_text_from_pdf",
    "extract_text_from_image",
    "extract_text_from_images",
]
