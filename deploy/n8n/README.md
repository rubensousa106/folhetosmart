# Automação n8n — relatório semanal de folhetos por email

**Desenho simples e fiável (2 nós):** o pipeline já corre sozinho às quintas no
GitHub Actions (`produce-flyers.yml`, cron quinta 09:00 UTC) — descarrega os
folhetos automáticos (**Aldi, Pingo Doce, Continente**) para o Cloudflare R2,
extrai/normaliza, e no fim o `report_flyers.py` **envia o relatório para um
webhook do n8n**. O n8n recebe-o e envia-o por email para **rubensousa106@gmail.com**.

Assim o n8n não precisa de credenciais do R2 nem do GitHub — só **Webhook → Gmail**.

## Configurar (5 passos)

1. **Importa** [`sync-folhetos-semanal.json`](sync-folhetos-semanal.json) no teu n8n
   (`https://rubensousa106.app.n8n.cloud`).
2. No nó **"Enviar email (Gmail)"**, liga a tua credencial **Gmail (OAuth2)**.
3. **Ativa** o workflow (canto superior — *Active*), para o webhook de produção ficar a ouvir.
4. Copia o **Production URL** do nó Webhook
   (algo como `https://rubensousa106.app.n8n.cloud/webhook/folhetosmart-relatorio`).
5. No GitHub, em *Settings → Secrets and variables → Actions*, cria o secret
   **`N8N_REPORT_WEBHOOK`** com esse URL.

Pronto. À quinta, o pipeline corre e o relatório chega ao teu email automaticamente.

## O que o email contém

```
📋 FolhetoSmart — relatório semanal de folhetos (DD/MM/AAAA)

✅ No Cloudflare (3/5):
   • Continente — Continente 23-06-2026 - 29-06-2026.pdf
   • Pingo Doce — Pingo Doce 22-06-2026 - 28-06-2026.pdf
   • Aldi — Aldi Norte 18-06-2026 - 24-06-2026.pdf

❌ Em falta (2) — fazer upload manual pela app:
   • Lidl
   • Intermarché
```

## Testar agora (sem esperar pela quinta)

- No n8n, abre o workflow e usa **"Listen for test event"** no nó Webhook.
- Corre localmente: `cd scraper && N8N_REPORT_WEBHOOK="<o teu webhook de teste>" python report_flyers.py`
  (em Windows define a variável antes do comando). O email deve chegar.

## Notas

- O **`report_flyers.py`** também guarda o relatório no R2 (`relatorios/ultimo.txt`),
  caso prefiras lê-lo por lá.
- Se quiseres que o **n8n** dispare o pipeline (em vez do cron do GitHub Actions),
  acrescenta antes do Webhook um **Schedule Trigger** (quinta) + um **HTTP Request**
  que faça `POST https://api.github.com/repos/<OWNER>/folhetosmart/actions/workflows/produce-flyers.yml/dispatches`
  com header `Authorization: Bearer <PAT actions:write>` e body `{"ref":"main"}`.
