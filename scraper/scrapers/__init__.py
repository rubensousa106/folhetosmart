"""Scrapers de folhetos.

Atualmente só o **Aldi** tem scraper próprio (folheto regional/nacional descarregado
do site). Os outros supermercados entram via upload manual de PDF para o R2 (admin),
que o `drive_producer` depois processa com o mesmo extrator.
"""
from .aldi import (
    NoAldiStoreError,
    download_all_regions_to_r2,
    download_region_to_r2,
    fetch_products,
    region_for,
)

__all__ = [
    "NoAldiStoreError",
    "download_all_regions_to_r2",
    "download_region_to_r2",
    "fetch_products",
    "region_for",
]
