# 🌐 FolhetoSmart — Web

Website e app web do FolhetoSmart. Mesmo backend e mesma conta da app Android:
o que fazes no site (ex.: adicionar produtos à Lista) fica disponível no telemóvel.

- **Stack:** Next.js 14 (App Router) · TypeScript · Tailwind CSS · Lucide.
- **Renderização:** páginas públicas em **HTML estático** (SSG → ótimo para SEO); a
  área autenticada (`/app/*`) corre no cliente e não é indexada.
- **Exportação estática** (`output: "export"`) → deploy simples no Cloudflare Pages.

## Desenvolvimento

```bash
cd web
cp .env.example .env.local        # aponta para o backend (local ou produção)
npm install
npm run dev                       # http://localhost:3000
```

O backend tem de permitir CORS para `http://localhost:3000` (já configurado em
`backend/.../config/SecurityConfig.java`).

## Build

```bash
npm run build      # gera o site estático em ./out
npm run icons      # (re)gera os ícones PNG/PWA a partir de assets/icon*.svg
```

## SEO

- Metadata por página, Open Graph/Twitter, canonical e `pt-PT` (`lib/seo.ts`).
- JSON-LD: `Organization` + `WebSite` (SearchAction) + `MobileApplication` no layout;
  `FAQPage` na landing e nas páginas de supermercado.
- `app/sitemap.ts` e `app/robots.ts` (bloqueia `/app/`). `/app/*` com `noindex`.
- Páginas-alvo de palavras-chave: `/supermercados/{lidl,continente,pingo-doce,intermarche,aldi}`.

## Ícones (compatíveis com todos os SO)

Conjunto único e coerente (Lucide para a UI). Os ícones de app/favicon são gerados
a partir de `assets/icon.svg` (e `assets/icon-maskable.svg`) para `public/`:
`favicon.svg`, `icon-32/192/512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`
e `manifest.webmanifest` (PWA, `theme-color` #2E7D32).

## Deploy — Cloudflare Pages

Liga o repositório e usa:

| Campo | Valor |
|-------|-------|
| Root directory | `web` |
| Build command | `npm run build` |
| Output directory | `out` |
| Variável de ambiente | `NEXT_PUBLIC_API_URL=https://folhetosmart.onrender.com` |

Depois liga o domínio `folhetosmart.pt` (e `www`).

### ⚠️ CORS do R2 (feed de produtos)

O ecrã **Comparar** lê o feed dos folhetos, que o backend serve via redirect
(`302`) para um link assinado do **Cloudflare R2** (origem externa). Para o browser
poder ler esse ficheiro, o bucket R2 precisa de uma regra **CORS** que permita o
domínio do site:

```json
[
  {
    "AllowedOrigins": ["https://folhetosmart.pt", "https://www.folhetosmart.pt", "http://localhost:3000"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "MaxAgeSeconds": 3600
  }
]
```

(A app Android não precisa disto — não é um browser e ignora CORS.)
