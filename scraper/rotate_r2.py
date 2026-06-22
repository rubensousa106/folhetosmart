"""Rotação/limpeza semanal do Cloudflare R2 (correr à quinta, DEPOIS de normalizar).

Regra:
  - MANTÉM no root: os PDFs ainda válidos (validade não terminou) + o feed JSON
    mais recente (produtos_AAAA-MM-DD.json).
  - MOVE para `backup/`: tudo o resto do root (folhetos expirados, feeds antigos,
    ficheiros soltos).
  - APAGA de `backup/`: o que já lá estava há mais de 6 dias (a semana-2).

É idempotente e seguro: re-correr no mesmo dia NÃO perde o backup acabado de criar
(o apagar do backup usa a antiguidade > 6 dias). Usa `--dry-run` para ver o que
faria sem mexer em nada.

Uso:
    python rotate_r2.py --dry-run
    python rotate_r2.py
"""
from __future__ import annotations

try:
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

import logging
import os
import re
import sys
from datetime import date, datetime, timedelta, timezone


def _load_env_file() -> None:
    for path in (
        os.path.join(os.path.dirname(__file__), ".env"),
        os.path.join(os.path.dirname(__file__), "..", ".env"),
        ".env",
    ):
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        key, value = line.split("=", 1)
                        os.environ.setdefault(key.strip(), value.strip())
            return


_load_env_file()

from storage.r2_storage import r2_storage  # noqa: E402

logger = logging.getLogger(__name__)

_DATE = re.compile(r"(\d{1,2})[-/](\d{1,2})[-/](\d{4})")
_FEED = re.compile(r"produtos_(\d{4}-\d{2}-\d{2})\.json$")
BACKUP_KEEP_DAYS = 6


def _valid_until(key: str) -> date | None:
    """Última data DD-MM-AAAA no nome do PDF (= a validade 'até')."""
    matches = _DATE.findall(key)
    if not matches:
        return None
    d, m, y = matches[-1]
    try:
        return date(int(y), int(m), int(d))
    except ValueError:
        return None


def rotate(dry_run: bool = False) -> None:
    if not r2_storage.is_configured():
        logger.error("❌ R2 não configurado (R2_* em falta).")
        return

    today = date.today()
    objs = r2_storage.list_all()
    root = [o for o in objs if not o["key"].startswith("backup/")]
    backup = [o for o in objs if o["key"].startswith("backup/")]

    # Feed JSON mais recente (por data no nome) — fica no root (a app usa-o).
    feeds = [(o["key"], _FEED.search(o["key"]).group(1)) for o in root if _FEED.search(o["key"])]
    latest_feed = max(feeds, key=lambda f: f[1])[0] if feeds else None

    keep: set[str] = set()
    if latest_feed:
        keep.add(latest_feed)
    for o in root:
        k = o["key"]
        if k.lower().endswith(".pdf"):
            vu = _valid_until(k)
            if vu is None or vu >= today:  # válido (ou sem data legível) → mantém
                keep.add(k)

    to_move = [o["key"] for o in root if o["key"] not in keep]
    cutoff = datetime.now(timezone.utc) - timedelta(days=BACKUP_KEEP_DAYS)
    to_delete = [o["key"] for o in backup if o.get("modified") is None or o["modified"] < cutoff]

    prefix = "[DRY-RUN] " if dry_run else ""
    logger.info("%sManter no root (%d): %s", prefix, len(keep), sorted(keep))
    logger.info("%sMover para backup/ (%d): %s", prefix, len(to_move), to_move)
    logger.info("%sApagar do backup (%d): %s", prefix, len(to_delete), to_delete)

    if dry_run:
        logger.info("[DRY-RUN] Nada foi alterado.")
        return

    for k in to_delete:
        r2_storage.delete(k)
    for k in to_move:
        r2_storage.copy(k, "backup/" + os.path.basename(k))
        r2_storage.delete(k)
    logger.info("✅ Rotação concluída: mantidos %d, movidos %d, apagados %d.",
                len(keep), len(to_move), len(to_delete))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    rotate(dry_run="--dry-run" in sys.argv)
