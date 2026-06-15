"""Cron do scraper — corre o pipeline às quintas de manhã (folhetos novos).

Em produção corre como serviço separado (ou como container com restart).
Localmente: `python scheduler.py` (corre já uma vez e depois agenda).

O scheduler é deliberadamente simples: o backend é a fonte de verdade para
saber QUE folhetos estão disponíveis; aqui apenas disparamos o pipeline para
os supermercados com scraper implementado.
"""
from __future__ import annotations

import logging
import time

import schedule  # leve; ver requirements (apscheduler é alternativa)

import pipeline
from scrapers import SCRAPERS

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


def job() -> None:
    slugs = list(SCRAPERS.keys())
    logger.info("Cron a disparar sync para: %s", slugs)
    summary = pipeline.run_sync(slugs)
    logger.info(
        "Cron concluído: %d matched, %d por rever",
        summary.products_matched,
        summary.products_unmatched,
    )


def main() -> None:
    # Quinta-feira às 08:00 (após a publicação dos folhetos).
    schedule.every().thursday.at("08:00").do(job)
    logger.info("Scheduler ativo. Próxima execução: quinta 08:00.")
    while True:
        schedule.run_pending()
        time.sleep(60)


if __name__ == "__main__":
    main()
