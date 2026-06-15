#!/bin/bash
# =====================================================================
# Monitorização básica — reinicia o backend se cair.
# Cron (como deploy): */5 * * * * /opt/folhetosmart/deploy/hetzner/healthcheck.sh
# =====================================================================
set -uo pipefail
cd /opt/folhetosmart || exit 1

BACKEND_URL="http://localhost:8080/actuator/health"
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$BACKEND_URL" || echo "000")

if [ "$code" != "200" ]; then
    echo "$(date -Is) Backend não saudável (HTTP $code) — a reiniciar"
    docker compose restart backend
    # Alerta ao admin: o worker já envia FCM de folhetos em falta; para alertas
    # de infraestrutura, integrar aqui email/FCM se necessário.
else
    echo "$(date -Is) Backend OK"
fi
