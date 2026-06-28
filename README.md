# 🛒 FolhetoSmart

Comparador semanal de preços de supermercados portugueses. Os folhetos saem à
quinta-feira; o FolhetoSmart extrai os produtos com IA, **normaliza-os** (junta o
mesmo produto entre lojas e separa a marca) e mostra na app onde é mais barato.

> Projeto privado (negócio). Os dados são acedidos **só com autenticação**.

## Arquitetura — "processa 1× por semana, lê N×"

A extração com IA é cara, por isso corre **uma vez por semana no servidor** e
todos os utilizadores leem o resultado já preparado:

```
   Admin (app)                Produtor (GitHub Actions / Cowork)         App (utilizadores)
   upload PDF  ─►  Cloudflare R2  ─►  drive_producer.py (Claude)  ─►  backend  ─►  GET /products/all
                     (folhetos)        normalize_products.py     (Postgres)      (302 → R2, cache offline)
                                       publica o feed JSON ─► R2 ◄───────────────────┘
```

- **App** (Android): lê o feed `GET /api/v1/products/all`, que o backend
  **redireciona** (302) para um link assinado do R2 — download rápido, privado e
  com cache offline (Room). O utilizador pesquisa, vê a oferta mais barata em
  destaque e adiciona à lista de compras (local).
- **Backend** (Spring Boot, Render): autenticação JWT + RBAC, guarda o último
  feed/produtos por loja, assina URLs de upload/leitura do R2. Não corre IA.
- **Produtor** (Python, GitHub Actions/Cowork): lê os PDFs do R2, extrai os
  produtos com a Claude (`pdf_extractor`), faz POST ao backend, normaliza e
  publica o feed normalizado de volta no R2.
- **Cloudflare R2** (S3-compatível): guarda os folhetos PDF e o feed JSON.

## Componentes

| Componente | Stack | Pasta |
|------------|-------|-------|
| Produtor + IA | Python 3.12 · pdfplumber · Claude API · boto3 (R2) | [`scraper/`](scraper/) |
| Backend | Java 21 · Spring Boot 3 · Spring Security (JWT + RBAC) · Flyway | [`backend/`](backend/) |
| App | Kotlin · Jetpack Compose · Material 3 · Retrofit2 · Room | [`android/`](android/) |
| Web | Next.js 14 · TypeScript · Tailwind CSS (SSG + SEO) | [`web/`](web/) |
| Base de dados | PostgreSQL 16 | [`db/init.sql`](db/init.sql) |
| Armazenamento | Cloudflare R2 (folhetos PDF + feed JSON) | — |

> **Web** (`web/`): mesmo backend e mesma conta da app. Páginas públicas em HTML
> estático (otimizadas para SEO) e área autenticada (`/app`) no cliente. A **Lista
> de compras** é agora guardada no servidor (`/api/v1/shopping`), por isso sincroniza
> entre a web e o telemóvel. Ver [`web/README.md`](web/README.md).

## Arranque rápido (dev local)

```bash
# 1) Preenche o .env (ANTHROPIC_API_KEY, FOLHETO_JWT_SECRET, R2_*, ADMIN_*)
docker compose up -d postgres            # base de dados
docker compose up --build backend        # API em http://localhost:8080

# 2) Correr o produtor à mão (extrai do R2 e publica o feed):
docker compose --profile tools run --rm scraper python drive_producer.py
docker compose --profile tools run --rm scraper python normalize_products.py
```

| Serviço | URL |
|---------|-----|
| Backend (REST) | http://localhost:8080 |
| pgAdmin (opcional) | http://localhost:5050 — `docker compose --profile tools up pgadmin` |

## Variáveis de ambiente (ver [`.env.example`](.env.example))

| Variável | Onde | Para quê |
|----------|------|----------|
| `ANTHROPIC_API_KEY` | produtor | extração de produtos com a Claude |
| `R2_ENDPOINT` / `R2_BUCKET` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | produtor **e** backend | folhetos PDF + feed JSON |
| `FOLHETO_BACKEND_URL` / `FOLHETO_ADMIN_EMAIL` / `FOLHETO_ADMIN_PASSWORD` | produtor | POST dos produtos/feed |
| `FOLHETO_JWT_SECRET` | backend | assinatura dos JWT |
| `DATABASE_URL` / `FCM_PROJECT_ID` / `FCM_CREDENTIALS_JSON` | produtor | notificação FCM "há folhetos novos" |
| `FOLHETO_INSECURE_TLS=1` | produtor (dev) | redes com inspeção TLS |

## Endpoints principais (API, snake_case)

```
POST   /api/v1/auth/register | /auth/login
GET    /api/v1/products/all              -- feed normalizado (302 → R2)   (JWT)
GET    /api/v1/users/me  ·  PUT /users/me   -- perfil + distrito/cidade   (JWT)
GET    /api/v1/alerts  ·  POST /alerts  ·  DELETE /alerts/{id}            (JWT)
GET    /api/v1/shopping ·  POST /shopping  -- lista de compras (sincroniza)  (JWT)
PATCH  /api/v1/shopping/{id}/quantity  ·  DELETE /shopping/{id}           (JWT)
GET    /api/v1/privacy/my-data  ·  DELETE /privacy/my-account  (RGPD)     (JWT)
POST   /api/v1/admin/flyer-upload-url    -- link assinado p/ upload ao R2 (ADMIN)
POST   /api/v1/admin/products  ·  POST /admin/feed-url                    (ADMIN/produtor)
GET    /api/v1/sync/status               -- estado dos folhetos (leitura)
```

## Produção

- **Backend + PostgreSQL:** Render (plano grátis). Ver [`deploy/render/`](deploy/render/).
- **Extração semanal:** GitHub Actions [`produce-flyers.yml`](.github/workflows/produce-flyers.yml)
  (quinta 09:00 UTC) ou a rotina Cowork. Corre produtor → normalize → rotate.
- **Limpeza do R2:** `rotate_r2.py` move folhetos antigos para `backup/` e apaga
  o que lá está há mais de 6 dias.

Mais detalhes: [`scraper/README.md`](scraper/README.md) · [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Licença

Projeto privado — uso interno.
