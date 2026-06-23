# Produtor de folhetos (scraper)

Extrai os produtos dos folhetos PDF com a Claude e publica o feed normalizado no
Cloudflare R2. Modelo **"processa 1×/semana, lê N×"** — sem Playwright/OCR/Tesseract.

## Ficheiros

| Ficheiro | Função |
|----------|--------|
| `drive_producer.py` | Lê os PDFs do R2, extrai com `pdf_extractor` (Claude, página a página) e faz `POST /api/v1/admin/products`. **Cache por folheto** (não reextrai → não gasta IA); salta se o folheto já foi analisado (`FOLHETO_FORCE=1` força). |
| `normalize_products.py` | Pede à Claude um **nome canónico + marca** por produto, junta as lojas, constrói o feed e publica `produtos_AAAA-MM-DD.json` no R2 + `POST /admin/feed-url`. `--publish` republica sem IA. |
| `rotate_r2.py` | Limpeza semanal do bucket: folhetos antigos → `backup/`; apaga de `backup/` o que lá está há > 6 dias. `--dry-run` mostra sem mexer. |
| `pdf_extractor.py` | Extrator (pdfplumber + Claude). ⚠️ **Não mexer sem testar** (`test_pdf.py`). |
| `scrapers/aldi.py` | Folheto do Aldi (requests; 7 regiões, fallback nacional). |
| `scrapers/pingodoce.py` | Folheto do Pingo Doce ("Poupe Esta Semana"); mesmo mecanismo `…/GetPDF.ashx`. |
| `scrapers/continente.py` | Folheto Semanal do Continente (escolhe o principal; `…/GetPDF.ashx`). |
| `scrapers/_flyer_common.py` | Helpers partilhados pelos scrapers (sessão, download, R2). |
| `report_flyers.py` | Relatório semanal: que supermercados têm PDF no R2 / em falta (escreve `relatorios/ultimo.txt`). |

> **Lidl** (viewer Schwarz) e **Intermarché** (anti-bot 403) não têm scraper automático —
> entram por upload manual pela app. Ver a automação n8n em [`deploy/n8n/`](../deploy/n8n/).
| `storage/r2_storage.py` | Cliente R2 (boto3, lazy). |
| `notifications/` | FCM ("há folhetos novos"). `db.py` — Postgres (só tokens FCM). |

## Correr

```bash
pip install -r requirements.txt
# .env na RAIZ do projeto (folhetosmart/.env) com ANTHROPIC_API_KEY, R2_*, FOLHETO_*
python drive_producer.py                 # todos (ou: python drive_producer.py Continente)
python normalize_products.py             # normaliza + publica o feed
python rotate_r2.py --dry-run            # ver a limpeza sem mexer
python scrapers/aldi.py --url-only --region norte   # testar o link do Aldi
```

Redes com inspeção TLS (dev): `FOLHETO_INSECURE_TLS=1` e `PYTHONIOENCODING=utf-8`.
