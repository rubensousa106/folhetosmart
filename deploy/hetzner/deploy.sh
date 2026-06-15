#!/bin/bash
# =====================================================================
# FolhetoSmart — atualização do servidor (corre quando há código novo).
#   ssh deploy@IP && cd /opt/folhetosmart && bash deploy/hetzner/deploy.sh
# =====================================================================
set -euo pipefail
cd /opt/folhetosmart

echo "==> Puxar código novo"
git pull origin main

echo "==> Reconstruir e recriar backend + scraper"
docker compose build backend scraper
docker compose up -d --force-recreate backend scraper

echo "==> Limpar imagens antigas"
docker image prune -f

echo "==> Estado dos serviços"
docker compose ps
echo "Deploy concluído."
