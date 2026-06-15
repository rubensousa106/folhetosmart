#!/bin/bash
# =====================================================================
# FolhetoSmart — instalação de raiz num servidor Hetzner (Ubuntu 24.04).
# Corre UMA vez, como root, num servidor limpo:
#   bash <(curl -s https://raw.githubusercontent.com/SEU_USER/folhetosmart/main/deploy/hetzner/setup.sh)
# =====================================================================
set -euo pipefail

echo "==> Atualizar o sistema"
apt-get update && apt-get upgrade -y

echo "==> Instalar Docker + Compose plugin"
curl -fsSL https://get.docker.com | sh
apt-get install -y docker-compose-plugin

echo "==> Instalar ferramentas (git, firewall, fail2ban, certbot)"
apt-get install -y git ufw fail2ban certbot python3-certbot-nginx

echo "==> Configurar firewall (SSH, HTTP, HTTPS)"
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> Criar utilizador 'deploy' (não usar root para o dia-a-dia)"
if ! id deploy >/dev/null 2>&1; then
    useradd -m -s /bin/bash deploy
    usermod -aG docker deploy
fi

echo "==> Criar pasta do projeto"
mkdir -p /opt/folhetosmart
chown deploy:deploy /opt/folhetosmart

cat <<'EOF'

==============================================================
Servidor pronto. Continua como utilizador 'deploy':

  su - deploy
  cd /opt/folhetosmart
  git clone https://github.com/SEU_USER/folhetosmart.git .
  cp .env.example .env && nano .env     # preenche os segredos
  docker compose up -d

HTTPS (depois de o DNS de api.folhetosmart.pt apontar para este IP):
  certbot --nginx -d api.folhetosmart.pt --non-interactive \
    --agree-tos --email admin@folhetosmart.pt
==============================================================
EOF
