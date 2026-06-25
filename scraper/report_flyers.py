"""Relatório semanal de folhetos: que supermercados têm PDF no Cloudflare R2
(descarregado) e quais faltam (precisam de upload manual pela app).

Corre no fim do pipeline (produce-flyers.yml). Imprime o relatório e — se o R2
estiver configurado — guarda-o em `relatorios/relatorio_AAAA-MM-DD.txt` e mostra
um link assinado, para a automação (n8n) o ir buscar e enviar por email.
"""
from __future__ import annotations

import datetime as dt
import logging
import os
import sys

_ROOT = os.path.dirname(os.path.abspath(__file__))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)


def _load_env_file() -> None:
    """Carrega o .env ANTES de importar r2_storage (que lê as R2_* no import).
    Em Docker/Actions as variáveis já vêm do ambiente (setdefault não as sobrepõe)."""
    for path in (os.path.join(_ROOT, ".env"), os.path.join(_ROOT, "..", ".env"), ".env"):
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        os.environ.setdefault(k.strip(), v.strip())
            return


_load_env_file()

os.environ.pop("SSLKEYLOGFILE", None)  # proxy de inspeção TLS injeta-o; o truststore rebenta
try:  # redes com inspeção TLS
    import truststore as _truststore
    _truststore.inject_into_ssl()
except Exception:
    pass

from storage.r2_storage import r2_storage  # noqa: E402

logger = logging.getLogger(__name__)

# Supermercados esperados + como são detetados no nome do PDF.
ALIASES: dict[str, tuple[str, ...]] = {
    "Continente": ("continente",),
    "Pingo Doce": ("pingo doce", "pingodoce"),
    "Lidl": ("lidl",),
    "Aldi": ("aldi",),
    "Intermarché": ("intermarche", "intermarché"),
}


def build_report() -> tuple[str, dict]:
    """Texto do relatório + dados {feitos, em_falta}."""
    nomes: list[str] = []
    if r2_storage.is_configured():
        try:
            # Só os folhetos ATUAIS (no root) — ignora os arquivados em backup/.
            nomes = [p["name"] for p in r2_storage.list_pdfs()
                     if not p["name"].startswith("backup/")]
        except Exception as exc:  # noqa: BLE001
            logger.warning("Não consegui listar o R2: %s", exc)

    feitos: dict[str, str] = {}
    em_falta: list[str] = []
    for loja, alias in ALIASES.items():
        match = next((n for n in nomes if any(a in n.lower() for a in alias)), None)
        if match:
            feitos[loja] = match
        else:
            em_falta.append(loja)

    hoje = dt.date.today().strftime("%d/%m/%Y")
    linhas = [f"📋 FolhetoSmart — relatório semanal de folhetos ({hoje})", ""]
    linhas.append(f"✅ No Cloudflare ({len(feitos)}/{len(ALIASES)}):")
    for loja, nome in feitos.items():
        linhas.append(f"   • {loja} — {nome}")
    if not feitos:
        linhas.append("   (nenhum)")
    linhas.append("")
    linhas.append(f"❌ Em falta ({len(em_falta)}) — fazer upload manual pela app:")
    for loja in em_falta:
        linhas.append(f"   • {loja}")
    if not em_falta:
        linhas.append("   (nenhum — estão todos!)")

    return "\n".join(linhas), {"feitos": list(feitos), "em_falta": em_falta}


def _send_to_n8n(report: str, data: dict) -> None:
    """Envia o relatório para um webhook do n8n (que depois o envia por email).
    Best-effort: só se N8N_REPORT_WEBHOOK estiver definido."""
    webhook = os.getenv("N8N_REPORT_WEBHOOK", "").strip()
    if not webhook:
        return
    try:
        import requests  # lazy
        verify = os.getenv("FOLHETO_INSECURE_TLS", "0") != "1"
        requests.post(webhook, json={"report": report, **data}, timeout=30, verify=verify)
        print("\n📤 Relatório enviado para o webhook do n8n.")
    except Exception as exc:  # noqa: BLE001
        print(f"\n(⚠️ não consegui enviar o relatório ao n8n: {exc})")


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    report, data = build_report()
    print(report)
    _send_to_n8n(report, data)

    if r2_storage.is_configured():
        try:
            body = report.encode("utf-8")
            # Dated (histórico) + chave estável "ultimo.txt" (a automação/n8n lê sempre esta).
            for key in (f"relatorios/relatorio_{dt.date.today().isoformat()}.txt",
                        "relatorios/ultimo.txt"):
                r2_storage.client.put_object(
                    Bucket=r2_storage.bucket, Key=key, Body=body,
                    ContentType="text/plain; charset=utf-8",
                )
            link = r2_storage.presign_get("relatorios/ultimo.txt")
            print(f"\n📎 Relatório no R2 (relatorios/ultimo.txt):\n{link}")
        except Exception as exc:  # noqa: BLE001
            print(f"\n(⚠️ não consegui guardar o relatório no R2: {exc})")


if __name__ == "__main__":
    main()
