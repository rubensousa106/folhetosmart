"""Rotação/limpeza semanal do Cloudflare R2 (correr à quinta, DEPOIS de normalizar).

Regra (os PDFs de folhetos E os feeds JSON normalizados têm a validade no nome):
  - MANTÉM no root: os ativos ainda VÁLIDOS (data de fim >= hoje) ou sem data
    legível. VÁRIOS feeds válidos coexistem — a app consome todos e esconde só os
    produtos já expirados.
  - MOVE para `backup/`: os ativos cuja validade já EXPIROU (fim < hoje) + ficheiros
    soltos/legados.
  - APAGA de `backup/`: os ativos cuja validade expirou há mais de 2 SEMANAS (14
    dias, pela data no nome); os ficheiros sem data legível, pela antiguidade.

É idempotente e seguro: re-correr no mesmo dia não remove o backup acabado de criar.
Usa `--dry-run` para ver o que faria sem mexer em nada.

Uso:
    python rotate_r2.py --dry-run
    python rotate_r2.py
"""
from __future__ import annotations

import os  # noqa: E402

os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
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

# Datas no nome: DD-MM-AAAA, DD/MM/AAAA, DD_MM_AAAA, DDMMAAAA (8) ou DDMMAA (6).
_DATE_TOKEN = re.compile(r"\d{1,2}[-/_]\d{1,2}[-/_]\d{4}|\d{8}|\d{6}")
DELETE_AFTER_EXPIRY_DAYS = 14  # apaga do backup 2 semanas DEPOIS de a validade expirar


def _is_dated_asset(key: str) -> bool:
    """Ativo com validade no nome: PDF de folheto ou feed JSON normalizado
    (produtos_DD-MM-AAAA_DD-MM-AAAA.json)."""
    base = os.path.basename(key).lower()
    return base.endswith(".pdf") or base.startswith("produtos")


def _to_date(token: str) -> date | None:
    digits = re.sub(r"\D", "", token)
    try:
        if len(digits) == 8:   # DDMMAAAA
            return date(int(digits[4:8]), int(digits[2:4]), int(digits[0:2]))
        if len(digits) == 6:   # DDMMAA
            return date(2000 + int(digits[4:6]), int(digits[2:4]), int(digits[0:2]))
    except ValueError:
        return None
    return None


def _valid_until(key: str) -> date | None:
    """Data de FIM da validade = a data mais tarde no nome do folheto. Aceita os
    separadores -, /, _ e os formatos sem separador (uploads manuais antigos)."""
    dates = [d for d in (_to_date(t) for t in _DATE_TOKEN.findall(key)) if d]
    return max(dates) if dates else None


def rotate(dry_run: bool = False) -> None:
    if not r2_storage.is_configured():
        logger.error("❌ R2 não configurado (R2_* em falta).")
        return

    today = date.today()
    objs = r2_storage.list_all()
    # `relatorios/` (relatórios para a automação/n8n) e `backup/` ficam intactos.
    root = [o for o in objs if not o["key"].startswith(("backup/", "relatorios/"))]
    backup = [o for o in objs if o["key"].startswith("backup/")]

    # ROOT: mantém os ativos (PDF ou feed) AINDA VÁLIDOS — fim de validade >= hoje,
    # ou sem data legível. Move para backup/ os já expirados. Vários feeds válidos
    # ficam no root ao mesmo tempo (a app consome todos e esconde só os expirados).
    keep: set[str] = set()
    to_move: list[str] = []
    for o in root:
        k = o["key"]
        if _is_dated_asset(k):
            vu = _valid_until(k)
            if vu is None or vu >= today:
                keep.add(k)
            else:
                to_move.append(k)
        else:
            to_move.append(k)  # ficheiro solto/legado → arquiva

    # BACKUP: apaga 2 semanas DEPOIS de a validade expirar (data no nome). Sem data
    # legível, recorre à antiguidade do ficheiro no backup.
    cutoff_modified = datetime.now(timezone.utc) - timedelta(days=DELETE_AFTER_EXPIRY_DAYS)
    to_delete: list[str] = []
    for o in backup:
        k = o["key"]
        vu = _valid_until(k)
        if vu is not None:
            if today > vu + timedelta(days=DELETE_AFTER_EXPIRY_DAYS):
                to_delete.append(k)
        elif o.get("modified") is None or o["modified"] < cutoff_modified:
            to_delete.append(k)

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
