"""Pipeline de sincronização: scraping -> IA matching -> persistência.

Dois pontos de entrada:
- `run_sync(slugs)` — scraping automático (disparado pelo backend/cron);
- `run_pdf_sync(slug, pdf_path)` — PDF carregado manualmente na app (Fix 3),
  que entra diretamente no OCR + AI matcher.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional, Sequence

from ai_matcher import BatchProcessor, ClaudeMatcher, FallbackMatcher
from ai_matcher.batch_processor import MatchCache
from ai_matcher.models import MatchDecision, MatchResult, RawProduct
from config.settings import settings
from scrapers import SCRAPERS
from scrapers.base import FlyerWindow
import db

logger = logging.getLogger(__name__)


@dataclass
class SyncSummary:
    products_matched: int = 0
    products_unmatched: int = 0
    promotions_found: int = 0
    per_supermarket: dict[str, int] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)
    # Supermercados pedidos mas ainda sem scraper implementado — não é erro.
    skipped: list[str] = field(default_factory=list)


def _build_processor() -> BatchProcessor:
    cache_path = Path(settings.data_dir) / "match_cache.json"
    return BatchProcessor(
        primary=ClaudeMatcher(
            api_key=settings.anthropic_api_key or None,
            model=settings.anthropic_model,
            insecure_tls=settings.insecure_tls,
        ),
        fallback=FallbackMatcher(),
        cache=MatchCache(cache_path),
    )


def scrape_supermarkets(
    slugs: Sequence[str], window: FlyerWindow
) -> tuple[list[RawProduct], list[str], list[str]]:
    """Corre os scrapers dos supermercados pedidos.

    Devolve (produtos, erros, ignorados). Um supermercado sem scraper
    implementado é IGNORADO (estado conhecido do projeto), não é uma falha.
    """
    from scrapers import NoAldiStoreError  # import local evita ciclo
    from storage import drive_storage

    raw: list[RawProduct] = []
    errors: list[str] = []
    skipped: list[str] = []
    for slug in slugs:
        _mark_sync(slug, "running", source="site")
        scraper_cls = SCRAPERS.get(slug)
        if scraper_cls is None:
            logger.info("Sem scraper implementado para '%s' — ignorado.", slug)
            skipped.append(slug)
            _mark_sync(slug, "error", error_message="Sem scraper implementado")
            continue

        try:
            products, source, error = _scrape_with_fallback(
                scraper_cls, slug, window, drive_storage
            )
        except NoAldiStoreError as exc:
            # Sem loja Aldi na cidade configurada: regista flyer_available=false.
            logger.warning("%s", exc)
            skipped.append(slug)
            try:
                with db.connect() as conn:
                    db.set_flyer_available(conn, slug, False)
            except Exception:  # noqa: BLE001
                logger.exception("Falha ao marcar flyer_available=false (%s)", slug)
            _mark_sync(slug, "error", error_message=str(exc))
            continue

        if products:
            raw.extend(products)
            _mark_sync(slug, "success", products_imported=len(products), source=source)
        else:
            errors.append(f"{slug}: {error}")
            _mark_sync(slug, "error", error_message=error)
    return raw, errors, skipped


def _scrape_with_fallback(
    scraper_cls, slug: str, window: FlyerWindow, drive
) -> tuple[list[RawProduct], Optional[str], Optional[str]]:
    """Lógica de prioridade por supermercado: site -> Drive.

    1.º download automático do site; em caso de sucesso guarda cópia no Drive.
    2.º se o site falhar/não extrair, procura no Drive um PDF desta semana.
    Devolve (produtos, fonte, mensagem_de_erro).
    """
    from ocr.extractor import extract_flyer_products_pdf

    scraper = scraper_cls(window=window)
    name = scraper.name
    error: Optional[str] = None

    # 1.º — site
    try:
        products = scraper.scrape()
    except Exception as exc:  # noqa: BLE001 — propaga NoAldiStoreError? não: trata todos menos esse
        from scrapers import NoAldiStoreError

        if isinstance(exc, NoAldiStoreError):
            raise
        logger.warning("%s: scraper do site falhou: %s", name, exc)
        products, error = [], str(exc)

    if products:
        # Guarda cópia do PDF no Drive (backup), se disponível.
        pdf = getattr(scraper, "last_pdf_path", None)
        if drive.is_configured() and pdf:
            drive.upload_pdf(pdf, drive.pdf_filename_for(name, window))
        return products, "site", None

    # 2.º — Google Drive (PDF carregado manualmente esta semana)
    if drive.is_configured():
        match = drive.find_pdf_for(name, window)
        if match:
            logger.info("%s: PDF encontrado no Drive — a processar", name)
            _mark_sync(slug, "running", source="drive")  # app mostra "📁 Drive..."
            try:
                local = drive.download_pdf(match["id"], match["name"])
                products = extract_flyer_products_pdf(
                    local, supermarket=slug, source_url=f"drive:{match['name']}"
                )
                if products:
                    return products, "drive", None
            except Exception as exc:  # noqa: BLE001
                logger.warning("%s: falha ao processar PDF do Drive: %s", name, exc)
                error = str(exc)

    return [], None, (
        error or "Nenhum produto extraído — carrega o folheto em PDF ou no Drive"
    )


def _supermarket_name(slug: str) -> str:
    cls = SCRAPERS.get(slug)
    return cls.name if cls else slug.replace("-", " ").title()


def _backup_to_drive(slug: str, pdf_path: str, window: FlyerWindow) -> None:
    """Guarda uma cópia do PDF no Google Drive (best effort)."""
    from storage import drive_storage

    if not drive_storage.is_configured():
        return
    try:
        name = drive_storage.pdf_filename_for(_supermarket_name(slug), window)
        drive_storage.upload_pdf(pdf_path, name)
    except Exception:  # noqa: BLE001 — backup não trava a sync
        logger.exception("Falha no backup do PDF para o Drive (%s)", slug)


def _mark_sync(slug: str, status: str, *, source: Optional[str] = None, **kwargs) -> None:
    """Escreve o estado de sincronização do supermercado (best effort)."""
    try:
        with db.connect() as conn:
            db.set_supermarket_sync(conn, slug, status, source=source, **kwargs)
    except Exception:  # noqa: BLE001 — estado é cosmético; não parar a sync
        logger.exception("Falha ao gravar sync_status de %s", slug)


def _match_and_persist(
    raw_products: Sequence[RawProduct],
    summary: SyncSummary,
    *,
    sync_run_id: Optional[str],
    window: FlyerWindow,
) -> None:
    """Caminho comum: IA matching + persistência + fecho do sync_run."""
    processor = _build_processor()

    with db.connect() as conn:
        if sync_run_id:
            db.update_sync_run(conn, sync_run_id, status="running")

        candidates = db.fetch_candidates(conn)
        try:
            results: list[MatchResult] = processor.process(raw_products, candidates)
        except Exception as exc:  # noqa: BLE001 — Claude E fallback falharam
            logger.exception("Matcher indisponível — a fechar o run com erro")
            summary.errors.append(f"matcher: {exc}")
            if sync_run_id:
                db.update_sync_run(
                    conn,
                    sync_run_id,
                    status="error",
                    error_message=f"IA de matching indisponível: {exc}"[:500],
                    finished=True,
                )
            return

        for result in results:
            if result.raw_product.is_promotion:
                summary.promotions_found += 1
            try:
                persisted = db.persist_result(conn, result, window)
            except Exception as exc:  # noqa: BLE001
                logger.exception("Falha ao persistir %s", result.raw_product.raw_name)
                summary.errors.append(str(exc))
                continue

            if persisted and result.decision is not MatchDecision.NEEDS_REVIEW:
                summary.products_matched += 1
            else:
                summary.products_unmatched += 1

        if sync_run_id:
            db.update_sync_run(
                conn,
                sync_run_id,
                status="done" if not summary.errors else "error",
                matched=summary.products_matched,
                unmatched=summary.products_unmatched,
                error_message="; ".join(summary.errors[:5]) or None,
                finished=True,
            )


def run_sync(
    slugs: Sequence[str],
    *,
    sync_run_id: Optional[str] = None,
    window: Optional[FlyerWindow] = None,
) -> SyncSummary:
    """Executa o pipeline completo de scraping e devolve um resumo."""
    window = window or FlyerWindow.current_week()
    summary = SyncSummary()

    raw_products, scrape_errors, skipped = scrape_supermarkets(slugs, window)
    summary.errors.extend(scrape_errors)
    summary.skipped.extend(skipped)
    for rp in raw_products:
        summary.per_supermarket[rp.supermarket] = (
            summary.per_supermarket.get(rp.supermarket, 0) + 1
        )

    _match_and_persist(raw_products, summary, sync_run_id=sync_run_id, window=window)

    # Notificações FCM (Fix 4): avisa o admin dos folhetos em falta e TODOS os
    # utilizadores de que há folhetos novos — a app atualiza-se sozinha.
    try:
        from notifications import notify_missing_flyers, notify_new_flyers

        failed = {e.split(":", 1)[0].strip() for e in scrape_errors} | set(skipped)
        if failed:
            notify_missing_flyers(sorted(_supermarket_name(s) for s in failed))

        if summary.products_matched > 0:
            with db.connect() as conn:
                savings = db.week_avg_savings_pct(conn)
            notify_new_flyers(
                products=summary.products_matched,
                markets=len(summary.per_supermarket),
                savings_pct=savings,
            )
    except Exception:  # noqa: BLE001
        logger.exception("Falha nas notificações FCM")

    logger.info(
        "Sync concluído: %d matched, %d por rever, %d promoções",
        summary.products_matched,
        summary.products_unmatched,
        summary.promotions_found,
    )
    return summary


def run_pdf_sync(
    supermarket_slug: str,
    pdf_path: str,
    *,
    sync_run_id: Optional[str] = None,
    window: Optional[FlyerWindow] = None,
) -> SyncSummary:
    """Processa um PDF carregado manualmente (Fix 3): visão/OCR -> matcher -> BD."""
    from ocr.extractor import extract_flyer_products_pdf

    window = window or FlyerWindow.current_week()
    summary = SyncSummary()
    _mark_sync(supermarket_slug, "running", source="upload")

    try:
        raw_products = extract_flyer_products_pdf(
            pdf_path,
            supermarket=supermarket_slug,
            source_url=f"upload:{Path(pdf_path).name}",
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("Falha no OCR do PDF %s", pdf_path)
        summary.errors.append(f"OCR: {exc}")
        _mark_sync(supermarket_slug, "error", error_message=f"OCR falhou: {exc}")
        if sync_run_id:
            with db.connect() as conn:
                db.update_sync_run(
                    conn,
                    sync_run_id,
                    status="error",
                    error_message=f"OCR falhou: {exc}"[:500],
                    finished=True,
                )
        return summary

    summary.per_supermarket[supermarket_slug] = len(raw_products)
    _match_and_persist(raw_products, summary, sync_run_id=sync_run_id, window=window)

    # PDF manual conta como folheto disponível para este supermercado.
    if raw_products and not summary.errors:
        try:
            with db.connect() as conn:
                db.set_flyer_available(conn, supermarket_slug, True)
        except Exception:  # noqa: BLE001
            logger.exception("Falha ao marcar flyer_available=true (%s)", supermarket_slug)
        # Guarda cópia do PDF carregado também no Drive (backup).
        _backup_to_drive(supermarket_slug, pdf_path, window)
        _mark_sync(supermarket_slug, "success",
                   products_imported=len(raw_products), source="upload")
    elif summary.errors:
        _mark_sync(
            supermarket_slug, "error",
            error_message="; ".join(summary.errors[:3]),
        )
    else:
        _mark_sync(
            supermarket_slug, "error",
            error_message="Nenhum produto extraído do PDF",
        )

    logger.info(
        "PDF de %s processado: %d matched, %d por rever",
        supermarket_slug,
        summary.products_matched,
        summary.products_unmatched,
    )
    return summary
