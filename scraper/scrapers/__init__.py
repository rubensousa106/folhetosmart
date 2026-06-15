"""Scrapers por supermercado."""
from .aldi import AldiScraper, NoAldiStoreError
from .base import BaseScraper, FlyerWindow
from .continente import ContinenteScraper
from .intermarche import IntermarcheScraper
from .lidl import LidlScraper
from .pingodoce import PingoDoceScraper

# Registo slug -> classe de scraper (os 5 supermercados suportados).
SCRAPERS: dict[str, type[BaseScraper]] = {
    LidlScraper.slug: LidlScraper,
    ContinenteScraper.slug: ContinenteScraper,
    PingoDoceScraper.slug: PingoDoceScraper,
    IntermarcheScraper.slug: IntermarcheScraper,
    AldiScraper.slug: AldiScraper,
}

__all__ = [
    "AldiScraper",
    "BaseScraper",
    "ContinenteScraper",
    "FlyerWindow",
    "IntermarcheScraper",
    "LidlScraper",
    "NoAldiStoreError",
    "PingoDoceScraper",
    "SCRAPERS",
]
