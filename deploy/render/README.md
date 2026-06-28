# Deploy no Render (plano gratuito)

Topologia pensada para **custo zero**:

```
┌─────────────────────────┐        ┌──────────────────────────┐
│ Render (grátis)         │        │ GitHub Actions (grátis)  │
│                         │        │                          │
│  backend (Spring Boot)  │  lê    │  weekly-sync.yml         │
│  PostgreSQL 16          │◄───────│  scraping + OCR/visão    │
│                         │ escreve│  (Playwright/Tesseract)  │
└───────────▲─────────────┘        └──────────────────────────┘
            │ HTTPS (só leitura)
       App Android
```

- O **backend** (API de leitura) e o **PostgreSQL** ficam no Render.
- O **processamento semanal** (pesado) corre no **GitHub Actions** e escreve
  diretamente na base de dados do Render. Mantém-se o modelo
  **processar 1×/semana, ler N× pelos utilizadores** (a Claude API só é chamada
  uma vez por folheto por semana, no CI — nunca pelo utilizador final).

> Porque não pôr o scraper no Render? O worker Playwright/Tesseract não cabe nos
> **512 MB** do plano grátis e expô-lo publicamente seria inseguro. O CI tem RAM
> de sobra e é efémero.

---

## 1. Subir o backend + base de dados (Blueprint)

1. Faz push deste repositório para o GitHub.
2. No Render: **New → Blueprint** e aponta para o repositório. O Render lê o
   [`render.yaml`](../../render.yaml) na raiz e cria dois recursos:
   `folhetosmart-db` (PostgreSQL grátis) e `folhetosmart-backend` (web, Docker).
3. Preenche as variáveis marcadas como `sync: false` (pedidas no painel):

   | Variável | Valor |
   |----------|-------|
   | `FOLHETO_JWT_SECRET` | gera com `openssl rand -base64 48` |
   | `FOLHETO_ADMIN_EMAIL` | o teu email de administrador |
   | `FOLHETO_ADMIN_PASSWORD` | uma palavra-passe forte |

   As variáveis da BD (`DB_HOST`, `DB_PORT`, …) são injetadas automaticamente
   pelo Render a partir da base de dados.

4. **Deploy**. No 1.º arranque, o `AdminBootstrap` cria a conta ADMIN com o
   email/password acima. As migrações Flyway criam o schema.

O backend fica em `https://folhetosmart-backend.onrender.com` (ajusta o nome).
Healthcheck: `GET /actuator/health`.

> **Redeploy (web + Lista sincronizada).** Para ativar o suporte à web basta
> voltar a fazer deploy do backend (no painel: **Manual Deploy → Deploy latest
> commit**, ou automaticamente no push). No arranque, o Flyway aplica a migração
> `V11__shopping_items.sql` (tabela da lista de compras) e o `SecurityConfig`
> passa a responder com os cabeçalhos **CORS** para `folhetosmart.pt`. Não é
> preciso adicionar variáveis de ambiente novas — as origens permitidas estão no
> próprio `SecurityConfig`. Ver também [`deploy/cloudflare/`](../cloudflare/) para
> o CORS do bucket R2 (necessário para o ecrã *Comparar* na web).

## 2. Configurar o processamento semanal (GitHub Actions)

O workflow [`weekly-sync.yml`](../../.github/workflows/weekly-sync.yml) corre às
**quintas 09:00 UTC** (≈10:00 em Lisboa) e também pode ser disparado à mão
(**Actions → Sincronização semanal → Run workflow**).

Em **Settings → Secrets and variables → Actions**, define:

**Secrets** (obrigatórios):

| Secret | Valor |
|--------|-------|
| `DATABASE_URL` | a **External Database URL** da BD no Render (inclui `?sslmode=require`) |
| `ANTHROPIC_API_KEY` | a tua chave da Claude API |

**Secrets** (opcionais — salvaguarda no Drive e notificações):

| Secret | Valor |
|--------|-------|
| `GOOGLE_DRIVE_FOLDER_ID` | id da pasta do Drive |
| `GOOGLE_DRIVE_CREDENTIALS_JSON` | conteúdo do JSON da service account |
| `FCM_PROJECT_ID` | id do projeto Firebase |
| `FCM_CREDENTIALS_JSON` | conteúdo do JSON da service account FCM |

**Variables** (opcionais, têm defaults): `ANTHROPIC_MODEL` (`claude-sonnet-4-6`),
`ALDI_DISTRICT` (`Lisboa`), `ALDI_CITY` (`Lisboa`).

## 3. Apontar a app Android

Em `android/app/build.gradle.kts`, o `API_BASE_URL` do `release` deve apontar
para o URL do backend no Render (ex.: `https://folhetosmart-backend.onrender.com/`).

---

## Limitações do plano gratuito (e como conviver com elas)

- **O backend adormece** após ~15 min sem tráfego; o 1.º pedido a seguir demora
  ~30 s a acordar. Aumenta os timeouts do OkHttp na app, ou usa um ping externo
  (ex.: cron-job.org a bater em `/actuator/health`) para o manter acordado.
- **A base de dados grátis é apagada ~30 dias** após ser criada. Para algo
  duradouro, migra para o plano pago da BD (o resto continua grátis) ou recria-a
  e volta a correr o `weekly-sync` para repovoar.
- **Upload manual de PDF na app** (admin) depende do worker, que aqui não está
  no Render — nesta topologia faz-se pelo `workflow_dispatch` do GitHub Actions
  ou localmente via `docker compose`. Se precisares do upload in-app, sobe o
  scraper como serviço pago (Private Service) e define `FOLHETO_SCRAPER_URL` no
  backend.

## Alternativa: tudo no Render (pago)

Se quiseres o worker sempre ligado (upload in-app, scheduler interno):
sobe o `scraper` como **Private Service** (pago), define no backend
`FOLHETO_SCRAPER_URL=http://folhetosmart-scraper:8090` e
`FOLHETO_SCHEDULER_ENABLED=true`, e desliga o `weekly-sync.yml`.
