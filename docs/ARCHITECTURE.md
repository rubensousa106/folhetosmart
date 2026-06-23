# Arquitetura do FolhetoSmart

## Princípio: "processa 1× por semana, lê N×"

A extração de produtos com IA é o passo caro. Corre **uma vez por semana, no
servidor** (GitHub Actions / Cowork), e o resultado é lido por todos os
utilizadores. Custo de IA fixo (~1 chamada/folheto/semana), 0€ por utilizador.

## Fluxo de dados

1. **Upload do folheto** — o ADMIN escolhe o supermercado + datas na app e
   carrega o PDF. A app pede ao backend um **link assinado** do R2
   (`POST /admin/flyer-upload-url`) e faz **PUT direto app → R2** (o PDF não passa
   pelo Render). Nome: `"{Supermercado} DD-MM-AAAA - DD-MM-AAAA.pdf"`.
2. **Extração** — o `drive_producer.py` lista os PDFs do R2, extrai os produtos
   com `pdf_extractor` (pdfplumber → Claude, página a página) e faz
   `POST /admin/products`. Uma **flag durável** (`source_flyer`) evita reanalisar
   o mesmo folheto.
3. **Normalização** — o `normalize_products.py` pede à Claude um **nome canónico**
   (sem a marca da loja, no singular) + a **marca nacional** por produto, junta o
   mesmo produto entre lojas, e constrói o feed
   `[{produto, preco, supermercado, validade, original, marca}]`.
4. **Publicação** — o feed dated (`produtos_AAAA-MM-DD.json`) é carregado no R2;
   o produtor envia o link assinado ao backend (`POST /admin/feed-url`).
5. **Leitura** — a app chama `GET /api/v1/products/all`; o backend **redireciona
   (302)** para o link assinado do R2. A app descarrega 1× e usa **cache offline**
   (Room, 12h). O cabeçalho `Authorization` cai no cross-host, por isso o R2 usa
   assinatura na query string.
6. **Notificação** — após publicar, `notify_new_flyers` (FCM) avisa os
   utilizadores de que "os produtos da semana já estão disponíveis".
7. **Rotação** — `rotate_r2.py` (quinta, após o normalize) move folhetos antigos
   para `backup/` e apaga o que lá está há > 6 dias.

## Privacidade (é um negócio)

Os dados são privados. Uma app pública **não pode** aceder diretamente a um
bucket/Drive privado (ficheiro público = raspável; credenciais embebidas =
extraíveis). Por isso:

- Os endpoints de dados (`GET /products/**`) exigem **JWT** (`SecurityConfig`).
- O feed vive num bucket **privado**; o backend emite **URLs assinados**
  temporários (o `/all` redireciona para lá).

## Aldi (folheto regional)

O Aldi varia por região. O `scrapers/aldi.py` resolve a região a partir do
distrito (registado pelo utilizador em `PUT /users/me`), monta o URL da semana
(`esta-semana-{slug}.html`), segue o link `GetPDF.ashx` e descarrega o PDF.
Atualmente o site só publica um folheto **nacional**; o código cai nele quando a
região não tem folheto próprio.

## Deploy

| Peça | Onde |
|------|------|
| Backend + PostgreSQL | Render (plano grátis) |
| Extração semanal | GitHub Actions `produce-flyers.yml` (ou rotina Cowork) |
| Folhetos PDF + feed JSON | Cloudflare R2 |
| App | APK (Play Store: `pt.folhetosmart.app`) |

## Notas de manutenção

- **Não** apagar migrações Flyway (histórico imutável). Tabelas antigas
  (`products`, `weekly_prices`) podem ficar inertes.
- `pdf_extractor.py` é o extrator-chave (≈550 produtos no Continente). Testar
  sempre com `test_pdf.py` antes de mexer.
- Render free: a BD é apagada ~30 dias após inatividade; considerar BD persistente.
