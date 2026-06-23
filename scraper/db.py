"""Ligação à base de dados (psycopg 3).

O pipeline atual (drive_producer/normalize) fala com o backend por HTTP; o único
uso direto da BD é a leitura dos tokens FCM em `notifications/`. Mantém-se apenas
o gestor de contexto `connect()` (faz commit no fim, rollback em exceção).
"""
from __future__ import annotations

from contextlib import contextmanager

import psycopg
from psycopg.rows import dict_row

from config.settings import settings


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
