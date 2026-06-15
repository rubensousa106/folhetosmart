"""Contrato comum a todos os scrapers de supermercado.

Cada supermercado tem o seu scraper que herda de `BaseScraper` e implementa
`fetch()` (obter o HTML/PDF do folheto) e `parse()` (extrair `RawProduct`s).
A separação fetch/parse permite testar o `parse` com fixtures, sem rede.
"""
from __future__ import annotations

import abc
import datetime as dt
import logging
from dataclasses import dataclass, field
from typing import Optional

from ai_matcher.models import RawProduct

logger = logging.getLogger(__name__)


@dataclass
class FlyerWindow:
    """Período de validade do folheto desta semana."""

    valid_from: dt.date
    valid_until: dt.date

    @classmethod
    def current_week(cls, start: Optional[dt.date] = None) -> "FlyerWindow":
        """Quinta-feira a quarta-feira seguinte (ciclo típico em PT)."""
        today = start or dt.date.today()
        # recua até quinta-feira (weekday 3)
        thursday = today - dt.timedelta(days=(today.weekday() - 3) % 7)
        return cls(valid_from=thursday, valid_until=thursday + dt.timedelta(days=6))


class BaseScraper(abc.ABC):
    """Classe-base para um scraper de supermercado."""

    #: slug do supermercado (corresponde a supermarkets.slug na BD)
    slug: str = ""
    #: nome legível
    name: str = ""

    def __init__(self, window: Optional[FlyerWindow] = None) -> None:
        self.window = window or FlyerWindow.current_week()

    # -- fluxo: as subclasses ou implementam fetch()+parse(), ou override scrape()
    def fetch(self) -> str:
        """Obtém o conteúdo bruto do folheto (HTML). Pode usar Playwright."""
        raise NotImplementedError

    def parse(self, content: str) -> list[RawProduct]:
        """Extrai a lista de `RawProduct` a partir do conteúdo."""
        raise NotImplementedError

    def scrape(self) -> list[RawProduct]:
        logger.info("A obter folheto de %s (%s)", self.name, self.slug)
        content = self.fetch()
        products = self.parse(content)
        logger.info("%s: %d produtos extraídos", self.name, len(products))
        return products
