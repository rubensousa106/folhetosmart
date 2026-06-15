# 🛒 FolhetoSmart

Comparador semanal de preços de supermercados portugueses — **Pingo Doce,
Continente, Lidl, Intermarché e Aldi**. Os folhetos saem à quinta-feira; o
FolhetoSmart agrega-os, normaliza os produtos com IA e mostra onde é mais
barato comprar.

## Componentes

| Componente | Stack | Pasta |
|------------|-------|-------|
| Scraper + IA | Python 3.12 · Playwright · Scrapy · Tesseract · Claude API | [`scraper/`](scraper/) |
| Backend | Java 21 · Spring Boot 3 · Spring Security (JWT + RBAC) | [`backend/`](backend/) |
| App | Kotlin · Jetpack Compose · Material 3 · Retrofit2 | [`android/`](android/) |
| Base de dados | PostgreSQL 16 | [`db/init.sql`](db/init.sql) |

## O núcleo: normalização com IA

O problema central é que `"Doritos Chilli 150g"` (Pingo Doce) e
`"Doritos Tortilla Spicy 150g"` (Continente) são **o mesmo produto** para
efeitos de comparação, mas o texto difere. O
[`claude_matcher.py`](scraper/ai_matcher/claude_matcher.py) usa a Claude API
para raciocinar semanticamente sobre os nomes e atribuir um *score* de
confiança:

- confiança **≥ 0.85** → match automático;
- confiança **0.60–0.84** → fila de revisão humana;
- confiança **< 0.60** → cria novo produto canónico.

Se a Claude API falhar, há *fallback* para `sentence-transformers` local.

## Arranque rápido

```bash
# edita o .env e preenche ANTHROPIC_API_KEY e FOLHETO_JWT_SECRET
docker compose up -d postgres # arranca a base de dados
docker compose up --build     # backend + scraper
```

| Serviço | URL |
|---------|-----|
| Backend (REST) | http://localhost:8080 |
| Scraper worker | http://localhost:8090 |
| pgAdmin (opcional) | http://localhost:5050 — `docker compose --profile tools up pgadmin` |

### Testar só o AI matcher (recomendado começar por aqui)

```bash
cd scraper
python -m venv .venv && . .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
pytest tests/ -v                                # corre sem rede (Claude mockado)
python -m ai_matcher.demo                        # demo end-to-end real
```

## Ordem de desenvolvimento

1. Schema PostgreSQL + Docker Compose ✅
2. AI Matcher (`claude_matcher.py`) — núcleo, testado primeiro ✅
3. Scraper Lidl como prova de conceito ponta-a-ponta ✅
4. Backend Spring Boot — endpoints de sync e comparação ✅
5. App Android — tema, navegação, ecrã Sincronizar e Comparar ✅

## Endpoints principais

```
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/products?search=doritos
GET  /api/v1/products/{id}/prices
GET  /api/v1/products/{id}/price-history
GET  /api/v1/promotions?supermarket=lidl
POST /api/v1/compare
POST /api/v1/shopping-list/optimize
GET  /api/v1/sync/status
POST /api/v1/sync/trigger
GET  /api/v1/sync/runs/{id}            -- polling do progresso
PUT  /api/v1/sync/flyers/{slug}        (ADMIN)
GET  /api/v1/alerts                    (JWT)
POST /api/v1/alerts                    (JWT)
DELETE /api/v1/alerts/{id}             (JWT)
```

A API serializa em **snake_case** (`product_ids`, `total_otimizado`, …).

## App Android

Abre a pasta [`android/`](android/) no Android Studio ou compila por linha de
comandos com o wrapper (Gradle 8.9, JDK 17/21):

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

O emulador fala com o backend local em `http://10.0.2.2:8080/` (configurável
em `app/build.gradle.kts` → `API_BASE_URL`).

- 4 separadores: **Comparar · Lista · Sincronizar · Alertas**
- Offline-first: estado de sincronização, últimos preços consultados e a
  última otimização da lista ficam em cache (Room)
- FCM: para ativar notificações push, adiciona o `google-services.json` do teu
  projeto Firebase em `android/app/` e aplica o plugin
  `com.google.gms.google-services` no Gradle.
- Em redes com inspeção TLS: `gradle.properties` já inclui
  `-Djavax.net.ssl.trustStoreType=Windows-ROOT`.

## Licença

Projeto privado — uso interno.
