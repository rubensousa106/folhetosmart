# Automações n8n do FolhetoSmart

Dois workflows — importa cada `.json` no teu n8n (`https://rubensousa106.app.n8n.cloud`):
- **`sync-folhetos-semanal.json`** — relatório semanal dos folhetos por email.
- **`recuperar-password.json`** — envia a palavra-passe temporária de recuperação por email.

Ambos são **Webhook → Gmail** (sem credenciais de R2/GitHub; só ligas o teu Gmail OAuth2).

---

## 1) Relatório semanal — `sync-folhetos-semanal.json`

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

---

## 2) Recuperação de palavra-passe — `recuperar-password.json`

Quando um utilizador usa **"Esqueceste-te da palavra-passe?"** na app, o **backend** gera
uma palavra-passe temporária, grava-a, e faz um `POST` a este webhook com o corpo
`{ email, name, temp_password, validity_minutes }`. O n8n envia o email; o utilizador
entra com essa palavra-passe (válida **1 hora**) e a app obriga-o a definir uma nova.

### Configurar (5 passos)

1. **Importa** [`recuperar-password.json`](recuperar-password.json) no teu n8n.
2. No nó **"Enviar palavra-passe (Gmail)"**, liga a tua credencial **Gmail (OAuth2)**.
3. **Ativa** o workflow.
4. Copia o **Production URL** do nó Webhook
   (`https://rubensousa106.app.n8n.cloud/webhook/folhetosmart-password-reset`).
5. No **Render** (backend), define a variável de ambiente
   **`N8N_PASSWORD_RESET_WEBHOOK`** com esse URL
   (opcional: `PASSWORD_RESET_TTL_MIN`, por omissão `60`). **Sem** esta variável, o
   backend **regista a palavra-passe no log** em vez de a enviar (modo dev).

### O que o email contém

```
Assunto: FolhetoSmart — a tua palavra-passe temporária

Olá <nome>,
Pediste para recuperar a tua palavra-passe no FolhetoSmart.
Palavra-passe temporária: <gerada>
Entra na app com este email e esta palavra-passe nos próximos 60 minutos e
define logo uma nova palavra-passe à tua escolha.
```

### Testar agora

- Na app: **Login → "Esqueceste-te da palavra-passe?"** → indica o email.
- Ou direto ao webhook:
  `curl -X POST "<webhook>" -H "Content-Type: application/json" -d '{"email":"tu@exemplo.pt","name":"Tu","temp_password":"ABC23xyzKM","validity_minutes":60}'`

---

## Notas

- O **`report_flyers.py`** também guarda o relatório no R2 (`relatorios/ultimo.txt`),
  caso prefiras lê-lo por lá.
- Se quiseres que o **n8n** dispare o pipeline (em vez do cron do GitHub Actions),
  acrescenta antes do Webhook um **Schedule Trigger** (quinta) + um **HTTP Request**
  que faça `POST https://api.github.com/repos/<OWNER>/folhetosmart/actions/workflows/produce-flyers.yml/dispatches`
  com header `Authorization: Bearer <PAT actions:write>` e body `{"ref":"main"}`.
