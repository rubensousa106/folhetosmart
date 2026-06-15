"""OCR de folhetos: agrupamento espacial em blocos de produto + texto (compat)."""
from .pdf_extractor import (
    extract_blocks_from_image,
    extract_blocks_from_images,
    extract_blocks_from_pdf,
    extract_text_from_image,
    extract_text_from_images,
    extract_text_from_pdf,
)

__all__ = [
    "extract_blocks_from_pdf",
    "extract_blocks_from_images",
    "extract_blocks_from_image",
    "extract_text_from_pdf",
    "extract_text_from_image",
    "extract_text_from_images",
]
