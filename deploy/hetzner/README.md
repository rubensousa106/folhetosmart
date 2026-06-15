# Deploy no Hetzner

Servidor recomendado: **CX22** (2 vCPU, 4 GB RAM, 40 GB SSD) — ~4,35 €/mês,
Ubuntu 24.04, região Helsínquia ou Nuremberga (perto de PT).

## Arquitetura de custos

O processamento (scraping + **Claude API**) corre **1× por semana no
servidor** (cron de quinta às 10:00). Os utilizadores apenas **leem** dados já
processados. Por isso o custo é fixo, independente do nº de utilizadores:

| Item | Custo |
|---|---|
| Claude API | ~0,10 € × 5 supermercados = **~0,50 €/semana** |
| Servidor Hetzner CX22 | ~4,35 €/mês |
| FCM (notificações) | grátis até 1M/mês |
| **Por utilizador** | **0 €** |

## Primeiro deploy

1. Cria o servidor em https://hetzner.com (CX22, Ubuntu 24.04, adiciona a tua
   chave SSH pública).
2. Aponta o DNS de `api.folhetosmart.pt` para o IP do servidor (registo A).
3. Liga e corre a instalação de raiz:
   ```bash
   ssh root@IP_DO_SERVIDOR
   bash <(curl -s https://raw.githubusercontent.com/SEU_USER/folhetosmart/main/deploy/hetzner/setup.sh)
   ```
4. Clona o projeto e configura o ambiente:
   ```bash
   su - deploy
   cd /opt/folhetosmart
   git clone https://github.com/SEU_USER/folhetosmart.git .
   cp .env.example .env
   nano .env        # preenche todos os segredos (ver abaixo)
   ```
5. Arranca os serviços e ativa HTTPS:
   ```bash
   docker compose up -d
   sudo certbot --nginx -d api.folhetosmart.pt --non-interactive \
     --agree-tos --email admin@folhetosmart.pt
   ```
6. Verifica:
   ```bash
   docker compose ps
   curl https://api.folhetosmart.pt/actuator/health   # {"status":"UP"}
   ```

## Atualizações

```bash
ssh deploy@IP_DO_SERVIDOR
cd /opt/folhetosmart && bash deploy/hetzner/deploy.sh
```

## Monitorização

Agenda o healthcheck (como `deploy`): `crontab -e`
```
*/5 * * * * /opt/folhetosmart/deploy/hetzner/healthcheck.sh >> /tmp/healthcheck.log 2>&1
```

## Variáveis obrigatórias no `.env`

```
FOLHETO_JWT_SECRET=            # openssl rand -base64 48
POSTGRES_PASSWORD=            # password forte
ANTHROPIC_API_KEY=           # sk-ant-...
FOLHETO_REQUIRE_HTTPS=true   # produção: rejeita HTTP
GOOGLE_DRIVE_FOLDER_ID=      # id da pasta partilhada com a service account
GOOGLE_DRIVE_CREDENTIALS_PATH=credentials/gdrive_credentials.json
# FCM (notificar utilizadores de folhetos novos):
FCM_PROJECT_ID=
FCM_CREDENTIALS_PATH=credentials/fcm_credentials.json
```

> As credenciais (`scraper/credentials/`) **não** vão para o git — copia-as
> para o servidor manualmente (`scp`) e monta-as via volume (já configurado no
> `docker-compose.yml`).

## Cron de processamento

O backend tem o cron embutido (`SyncSchedulerJob`, quinta 10:00 Europe/Lisbon)
— não é preciso configurar cron de sistema para o processamento semanal. Em
cada execução: tenta o site, depois o Google Drive, e notifica o admin dos
folhetos em falta e todos os utilizadores dos folhetos novos.
