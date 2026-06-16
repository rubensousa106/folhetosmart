"""Camada de persistência do worker (psycopg 3).

O backend Spring Boot é dono do ciclo de vida de `sync_runs` e da
disponibilidade de folhetos; este módulo trata apenas das escritas que o
pipeline de scraping/IA produz: produtos canónicos, aliases e preços semanais,
além de atualizar os contadores de progresso de um `sync_run`.
"""
from __future__ import annotations

import datetime as dt
import logging
from contextlib import contextmanager
from typing import Iterable, Optional

import psycopg
from psycopg.rows import dict_row

from ai_matcher.models import CandidateProduct, MatchDecision, MatchResult
from config.settings import settings

logger = logging.getLogger(__name__)


@contextmanager
def connect():
    conn = psycopg.connect(settings.database_url, row_factory=dict_row)
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def fetch_candidates(conn, *, limit: int = 500) -> list[CandidateProduct]:
    """Produtos canónicos existentes, para a IA usar como candidatos."""
    rows = conn.execute(
        """
        SELECT id, canonical_name, display_name, brand, category, weight_grams
        FROM products
        ORDER BY created_at DESC
        LIMIT %s
        """,
        (limit,),
    ).fetchall()
    return [
        CandidateProduct(
            product_id=str(r["id"]),
            canonical_name=r["canonical_name"],
            display_name=r["display_name"],
            brand=r["brand"],
            category=r["category"],
            weight_grams=r["weight_grams"],
        )
        for r in rows
    ]


def supermarket_id(conn, slug: str) -> Optional[int]:
    row = conn.execute(
        "SELECT id FROM supermarkets WHERE slug = %s", (slug,)
    ).fetchone()
    return row["id"] if row else None


def week_avg_savings_pct(conn) -> Optional[float]:
    """Poupança média (%) das promoções ativas hoje, para a notificação."""
    row = conn.execute(
        """
        SELECT AVG((original_price - price) / original_price * 100) AS avg
        FROM weekly_prices
        WHERE is_promotion = true AND original_price IS NOT NULL AND original_price > 0
          AND valid_from <= CURRENT_DATE AND valid_until >= CURRENT_DATE
        """
    ).fetchone()
    return float(row["avg"]) if row and row["avg"] is not None else None


def set_flyer_available(conn, slug: str, available: bool) -> None:
    """Marca a (in)disponibilidade do folheto — ex.: Aldi sem loja na cidade."""
    conn.execute(
        """
        UPDATE supermarkets
        SET flyer_available = %s,
            flyer_available_since = CASE WHEN %s THEN now() ELSE NULL END
        WHERE slug = %s
        """,
        (available, available, slug),
    )


def set_supermarket_sync(
    conn,
    slug: str,
    status: str,
    *,
    products_imported: Optional[int] = None,
    error_message: Optional[str] = None,
    source: Optional[str] = None,
) -> None:
    """Estado de sincronização por supermercado (ecrã Sincronizar — 4 estados).

    - status="running": início do scraper (source: site | drive);
    - status="success": preenche products_imported e synced_at=now();
    - status="error": preenche error_message (e mostra o botão de upload na app).
    `source` (site | drive | upload) é gravado quando fornecido.
    """
    if status == "success":
        conn.execute(
            """
            UPDATE supermarkets
            SET sync_status = 'success',
                products_imported = %s,
                synced_at = now(),
                error_message = NULL,
                sync_source = COALESCE(%s, sync_source)
            WHERE slug = %s
            """,
            (products_imported or 0, source, slug),
        )
    elif status == "error":
        conn.execute(
            """
            UPDATE supermarkets
            SET sync_status = 'error',
                error_message = %s,
                sync_source = COALESCE(%s, sync_source)
            WHERE slug = %s
            """,
            ((error_message or "Erro desconhecido")[:500], source, slug),
        )
    else:  # pending | running
        conn.execute(
            """
            UPDATE supermarkets
            SET sync_status = %s, sync_source = COALESCE(%s, sync_source)
            WHERE slug = %s
            """,
            (status, source, slug),
        )


def _create_product(conn, result: MatchResult) -> str:
    """Cria um produto canónico novo e devolve o id."""
    row = conn.execute(
        """
        INSERT INTO products (canonical_name, display_name, brand)
        VALUES (%s, %s, %s)
        ON CONFLICT (canonical_name) DO UPDATE SET display_name = EXCLUDED.display_name
        RETURNING id
        """,
        (result.canonical_name, result.display_name, result.extracted.brand),
    ).fetchone()
    return str(row["id"])


def _upsert_alias(conn, product_id: str, result: MatchResult, sm_id: int) -> None:
    conn.execute(
        """
        INSERT INTO product_aliases (product_id, alias, supermarket_id, ai_confidence)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (alias, supermarket_id) DO UPDATE
            SET ai_confidence = EXCLUDED.ai_confidence
        """,
        (product_id, result.raw_product.raw_name, sm_id, result.confidence),
    )


def persist_result(
    conn,
    result: MatchResult,
    window,  # FlyerWindow
) -> Optional[str]:
    """Grava um `MatchResult`: cria/associa o produto e insere o preço semanal.

    Resultados em `NEEDS_REVIEW` são gravados como alias (para a fila de
    revisão) mas NÃO geram preço associado a um canónico incerto.
    Devolve o product_id efetivo, ou None se ficou só para revisão.
    """
    rp = result.raw_product
    sm_id = supermarket_id(conn, rp.supermarket)
    if sm_id is None:
        logger.warning("Supermercado desconhecido: %s", rp.supermarket)
        return None

    if result.decision is MatchDecision.AUTO_MATCH and result.product_id:
        product_id = result.product_id
    elif result.decision is MatchDecision.NEW_PRODUCT:
        product_id = _create_product(conn, result)
    else:  # NEEDS_REVIEW
        # Guarda o alias por rever; sem preço canónico até validação humana.
        if result.product_id:
            _upsert_alias(conn, result.product_id, result, sm_id)
        return None

    _upsert_alias(conn, product_id, result, sm_id)

    conn.execute(
        """
        INSERT INTO weekly_prices (
            product_id, supermarket_id, price, original_price,
            is_promotion, promotion_label, valid_from, valid_until,
            raw_product_name, source_url
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (product_id, supermarket_id, valid_from) DO UPDATE SET
            price = EXCLUDED.price,
            original_price = EXCLUDED.original_price,
            is_promotion = EXCLUDED.is_promotion,
            promotion_label = EXCLUDED.promotion_label
        """,
        (
            product_id,
            sm_id,
            rp.price,
            rp.original_price,
            rp.is_promotion,
            rp.promotion_label,
            # Validade lida do folheto (Lidl) tem prioridade sobre a janela.
            rp.valid_from or window.valid_from,
            rp.valid_until or window.valid_until,
            rp.raw_name,
            rp.source_url,
        ),
    )
    return product_id


def update_sync_run(
    conn,
    sync_run_id: str,
    *,
    status: Optional[str] = None,
    matched: Optional[int] = None,
    unmatched: Optional[int] = None,
    error_message: Optional[str] = None,
    finished: bool = False,
) -> None:
    sets, params = [], []
    if status is not None:
        sets.append("status = %s")
        params.append(status)
    if matched is not None:
        sets.append("products_matched = %s")
        params.append(matched)
    if unmatched is not None:
        sets.append("products_unmatched = %s")
        params.append(unmatched)
    if error_message is not None:
        sets.append("error_message = %s")
        params.append(error_message)
    if finished:
        sets.append("finished_at = %s")
        params.append(dt.datetime.now(dt.timezone.utc))
    if not sets:
        return
    params.append(sync_run_id)
    conn.execute(
        f"UPDATE sync_runs SET {', '.join(sets)} WHERE id = %s", params
    )


# --- Processamento automático de PDFs do Drive (scheduler_drive.py) ----------
def get_processed_file_ids() -> set[str]:
    """IDs dos ficheiros do Drive já processados automaticamente."""
    with connect() as conn:
        rows = conn.execute(
            "SELECT drive_file_id FROM drive_processed_files"
        ).fetchall()
    return {r["drive_file_id"] for r in rows}


def mark_file_processed(file_id: str, filename: str, slug: str) -> None:
    """Marca um ficheiro do Drive como processado (idempotente)."""
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO drive_processed_files
                (drive_file_id, filename, supermarket_slug)
            VALUES (%s, %s, %s)
            ON CONFLICT (drive_file_id) DO NOTHING
            """,
            (file_id, filename, slug),
        )
