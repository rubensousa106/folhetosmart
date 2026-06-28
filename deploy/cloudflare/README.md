# Cloudflare — deploy da Web + CORS do R2

A web (`web/`) é exportada estaticamente e alojada no **Cloudflare Pages**. O feed
dos folhetos é servido a partir do **Cloudflare R2** (links assinados), por isso o
bucket precisa de uma regra **CORS** que permita o domínio do site.

## 1. Cloudflare Pages (web)

### Opção A — ligar o repositório no painel (recomendado)
Cloudflare Dashboard → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**:

| Campo | Valor |
|-------|-------|
| Repository | `rubensousa106/folhetosmart` |
| Production branch | `master` |
| Root directory | `web` |
| Build command | `npm run build` |
| Build output directory | `out` |
| Variável de ambiente (Build) | `NEXT_PUBLIC_API_URL = https://folhetosmart.onrender.com` |

Depois: **Custom domains** → adicionar `folhetosmart.pt` e `www.folhetosmart.pt`.

### Opção B — deploy manual / CI (wrangler)
```bash
cd web
npm install
NEXT_PUBLIC_API_URL=https://folhetosmart.onrender.com npm run build
npx wrangler pages deploy out --project-name folhetosmart-web
```
O workflow [`.github/workflows/deploy-web.yml`](../../.github/workflows/deploy-web.yml)
faz isto automaticamente em cada push a `master` que toque em `web/` — precisa dos
secrets `CLOUDFLARE_API_TOKEN` e `CLOUDFLARE_ACCOUNT_ID` (e da variável
`NEXT_PUBLIC_API_URL`).

## 2. CORS do bucket R2 (necessário para o ecrã Comparar)

O ecrã **Comparar** lê o feed via `302 → link assinado do R2` (origem externa). Sem
CORS no bucket, o browser bloqueia a leitura. Aplica as regras de
[`r2-cors.json`](r2-cors.json) (permitem `GET/HEAD` de `folhetosmart.pt`, `www` e
`localhost:3000`).

### Com o AWS CLI (R2 é S3-compatível) — usa as credenciais R2 já existentes
```bash
export AWS_ACCESS_KEY_ID=$R2_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY=$R2_SECRET_ACCESS_KEY

aws s3api put-bucket-cors \
  --bucket folhetosmart \
  --cors-configuration file://deploy/cloudflare/r2-cors.json \
  --endpoint-url "$R2_ENDPOINT"

# verificar
aws s3api get-bucket-cors --bucket folhetosmart --endpoint-url "$R2_ENDPOINT"
```

### Com o wrangler
```bash
npx wrangler r2 bucket cors put folhetosmart --file deploy/cloudflare/r2-cors.json
npx wrangler r2 bucket cors list folhetosmart
```

> A app Android **não** precisa disto — não é um browser e ignora CORS. Isto é só
> para o cliente web.
