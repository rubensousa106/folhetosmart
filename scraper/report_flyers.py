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
import re
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

# Datas no nome do PDF ("Continente 23-06-2026 - 29-06-2026.pdf"): a maior é o fim
# da validade (a data de expiração). Aceita separadores - / . _ e ano 2 ou 4 díg.
_DATE_RE = re.compile(r"(\d{1,2})[-/._](\d{1,2})[-/._](\d{2,4})")


def _window(flyer_name: str) -> tuple[dt.date | None, dt.date | None]:
    """(início, fim) da validade a partir das datas no nome do ficheiro do folheto."""
    datas: list[dt.date] = []
    for d, mo, y in _DATE_RE.findall(flyer_name):
        year = int(y)
        year = year + 2000 if year < 100 else year
        try:
            datas.append(dt.date(year, int(mo), int(d)))
        except ValueError:
            pass
    return (min(datas), max(datas)) if datas else (None, None)


def build_report() -> tuple[str, dict]:
    """Relatório em bullets (supermercado · validade/expiração · ✅/❌) + dados estruturados."""
    nomes: list[str] = []
    r2_ok = r2_storage.is_configured()
    if r2_ok:
        try:
            # Só os folhetos ATUAIS (no root) — ignora os arquivados em backup/.
            nomes = [p["name"] for p in r2_storage.list_pdfs()
                     if not p["name"].startswith("backup/")]
        except Exception as exc:  # noqa: BLE001
            logger.warning("Não consegui listar o R2: %s", exc)
            r2_ok = False

    hoje = dt.date.today()
    linhas = [
        "📋 FolhetoSmart — relatório de folhetos",
        f"🗓️ {hoje:%d/%m/%Y} (semana {hoje.isocalendar()[1]})",
        "",
        "Estado por supermercado (✅ = upload feito · ❌ = em falta):",
    ]

    feitos: list[str] = []
    em_falta: list[str] = []
    a_expirar: list[str] = []
    expirados: list[str] = []
    detalhe: dict[str, dict] = {}

    for loja, alias in ALIASES.items():
        match = next((n for n in nomes if any(a in n.lower() for a in alias)), None)
        if not match:
            linhas.append(f"  • ❌ {loja} — sem folheto no Cloudflare (falta upload manual na app)")
            em_falta.append(loja)
            detalhe[loja] = {"upload": False}
            continue

        feitos.append(loja)
        ini, fim = _window(match)
        if fim:
            dias = (fim - hoje).days
            if dias < 0:
                nota = f"⛔ EXPIRADO há {abs(dias)} dia(s)"
                expirados.append(loja)
            elif dias == 0:
                nota = "⚠️ expira HOJE"
                a_expirar.append(loja)
            elif dias == 1:
                nota = "⚠️ expira amanhã"
                a_expirar.append(loja)
            elif dias <= 3:
                nota = f"⚠️ faltam {dias} dias"
                a_expirar.append(loja)
            else:
                nota = f"faltam {dias} dias"
            linhas.append(f"  • ✅ {loja} — válido até {fim:%d/%m/%Y} ({nota})")
            detalhe[loja] = {"upload": True, "expira": fim.isoformat(),
                             "inicio": ini.isoformat() if ini else None, "dias_para_expirar": dias}
        else:
            linhas.append(f"  • ✅ {loja} — no Cloudflare (sem data legível no nome)")
            detalhe[loja] = {"upload": True, "expira": None}

    linhas.append("")
    linhas.append(f"📦 Resumo: {len(feitos)}/{len(ALIASES)} supermercados com folheto.")
    if expirados or a_expirar:
        linhas.append(f"⏰ Atenção à validade: {', '.join(expirados + a_expirar)}.")
    if em_falta:
        linhas.append("📤 Falta upload manual (o Lidl e o Intermarché não têm scraper "
                      f"automático): {', '.join(em_falta)}.")
    else:
        linhas.append("🎉 Estão todos!")
    if not r2_ok:
        linhas += ["", "⚠️ Nota: não consegui ler o Cloudflare R2 — os estados podem estar incompletos."]

    return "\n".join(linhas), {
        "feitos": feitos, "em_falta": em_falta,
        "a_expirar": a_expirar, "expirados": expirados, "detalhe": detalhe,
    }


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
