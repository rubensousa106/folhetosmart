"""Notificações push (FCM).

- `notify_missing_flyers(names)` → avisa os ADMINS de folhetos em falta;
- `notify_new_flyers(products, markets, savings)` → avisa TODOS os utilizadores
  de que há folhetos novos processados (Fix 4: a app atualiza-se sozinha).

Best effort: regista sempre nos logs; o envio FCM só ocorre se as credenciais
Firebase estiverem configuradas (FCM_PROJECT_ID + service account). Usa a FCM
HTTP v1 via google-auth (já dependência do Drive) — não exige firebase-admin.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Optional

import db
from config.settings import settings

logger = logging.getLogger(__name__)

FCM_SCOPES = ["https://www.googleapis.com/auth/firebase.messaging"]


def notify_missing_flyers(missing_names: list[str]) -> None:
    """Avisa os admins de cada folheto em falta."""
    for name in missing_names:
        body = f"⚠️ Folheto em falta: {name} — faz upload no Drive"
        logger.warning("ADMIN: %s", body)
        _send("Folheto em falta", body, _fcm_tokens(admin_only=True))


def notify_new_flyers(products: int, markets: int, savings_pct: Optional[float]) -> None:
    """Avisa TODOS os utilizadores de que há folhetos novos esta semana.

    Leva `data.type=new_products` + `route=sync` para a app, ao receber, mostrar o
    aviso no ecrã Alertas e poder reencaminhar para o Sincronizar.
    """
    savings = f" · Poupa até {savings_pct:.0f}% esta semana" if savings_pct else ""
    body = f"{products} produtos de {markets} supermercados{savings}"
    logger.info("Broadcast a utilizadores: %s", body)
    _send(
        "🛒 Novos produtos disponíveis!", body, _fcm_tokens(admin_only=False),
        data={"type": "new_products", "route": "sync"},
    )


# --- envio partilhado -------------------------------------------------------
def _send(title: str, body: str, tokens: list[str], data: Optional[dict] = None) -> None:
    creds = _load_fcm_credentials()
    if not creds or not settings.fcm_project_id:
        return  # FCM não configurado: fica só o log
    if not tokens:
        logger.info("FCM: sem tokens registados para '%s'", title)
        return
    try:
        access_token = _access_token(creds)
        _post_messages(access_token, tokens, title, body, data)
    except Exception as exc:  # noqa: BLE001 — notificação não trava a sync
        logger.warning("FCM: falha ao enviar (%s)", exc)


def _load_fcm_credentials() -> Optional[dict]:
    if settings.fcm_credentials_json.strip():
        try:
            return json.loads(settings.fcm_credentials_json)
        except json.JSONDecodeError:
            logger.warning("FCM_CREDENTIALS_JSON inválido")
    path = settings.fcm_credentials_path.strip()
    if path and Path(path).exists():
        try:
            return json.loads(Path(path).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            logger.warning("Credenciais FCM ilegíveis em %s", path)
    return None


def _fcm_tokens(*, admin_only: bool) -> list[str]:
    sql = "SELECT fcm_token FROM users WHERE fcm_token IS NOT NULL"
    if admin_only:
        sql += " AND role = 'ADMIN'"
    try:
        with db.connect() as conn:
            rows = conn.execute(sql).fetchall()
        return [r["fcm_token"] for r in rows if r.get("fcm_token")]
    except Exception:  # noqa: BLE001
        logger.exception("FCM: falha ao obter tokens")
        return []


def _access_token(creds_info: dict) -> str:
    from google.oauth2 import service_account  # lazy
    from google.auth.transport.requests import Request  # lazy

    credentials = service_account.Credentials.from_service_account_info(
        creds_info, scopes=FCM_SCOPES
    )
    credentials.refresh(Request())
    return credentials.token


def _post_messages(access_token: str, tokens: list[str], title: str, body: str,
                   data: Optional[dict] = None) -> None:
    import httpx  # lazy

    url = (
        f"https://fcm.googleapis.com/v1/projects/"
        f"{settings.fcm_project_id}/messages:send"
    )
    headers = {"Authorization": f"Bearer {access_token}"}
    verify = not settings.insecure_tls
    with httpx.Client(verify=verify, timeout=15.0) as client:
        for token in tokens:
            message = {
                "token": token,
                "notification": {"title": title, "body": body},
            }
            if data:  # valores do data têm de ser strings (requisito do FCM)
                message["data"] = {k: str(v) for k, v in data.items()}
            resp = client.post(url, headers=headers, json={"message": message})
            if resp.status_code >= 400:
                logger.warning("FCM: %s -> %s", resp.status_code, resp.text[:200])
