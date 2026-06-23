# Automação n8n — sincronização semanal de folhetos + relatório por email

**Fluxo:** às quintas, o n8n dispara o pipeline (GitHub Actions `produce-flyers.yml`),
que descarrega os folhetos automáticos (**Aldi, Pingo Doce, Continente**) para o
Cloudflare R2, extrai/normaliza, e gera o relatório `relatorios/ultimo.txt` no R2
(que supermercados foram descarregados e quais faltam). O n8n lê esse relatório e
envia-o por email para **rubensousa106@gmail.com**.

Importa [`sync-folhetos-semanal.json`](sync-folhetos-semanal.json) no teu n8n
(`https://rubensousa106.app.n8n.cloud`) e configura as credenciais abaixo.

## Nós do fluxo

1. **Schedule Trigger** — quinta-feira às 10:00 (cron `0 10 * * 4`).
2. **HTTP Request — Disparar pipeline** — `POST`
   `https://api.github.com/repos/<OWNER>/folhetosmart/actions/workflows/produce-flyers.yml/dispatches`
   - Header `Authorization: Bearer <GITHUB_PAT>` · `Accept: application/vnd.github+json`
   - Body JSON: `{"ref":"main"}`
3. **Wait** — 25 minutos (tempo do pipeline correr).
4. **S3 — Ler relatório (R2)** — *Download Object*: bucket `folhetosmart`,
   key `relatorios/ultimo.txt` (credencial S3 apontada ao R2 — ver abaixo).
5. **Gmail — Enviar relatório** — Para `rubensousa106@gmail.com`,
   Assunto `FolhetoSmart — relatório semanal de folhetos`,
   Texto = conteúdo do relatório (saída do nó S3).

## Credenciais a criar no n8n

| Credencial | Para quê | Valores |
|---|---|---|
| **GitHub** (Header Auth) | disparar o workflow | PAT fine-grained no repo `folhetosmart`, permissão **Actions: Read & Write** |
| **R2 (S3)** | ler o relatório | Endpoint `https://<accountid>.r2.cloudflarestorage.com`, região `auto`, Access Key ID + Secret do R2 (as mesmas `R2_*` do projeto) |
| **Gmail** (OAuth2) | enviar o email | a tua conta Google |

## Notas

- Substitui `<OWNER>` pelo teu utilizador/organização do GitHub no URL do passo 2.
- O `produce-flyers.yml` também tem um **cron próprio** (quinta 09:00 UTC). Para não
  correr 2×, podes **remover o `schedule:` do workflow** e deixar o disparo só ao n8n
  (assim controlas a hora e recebes o email logo a seguir).
- Os folhetos **sem scraper automático** (Lidl, Intermarché) aparecem no relatório
  como "em falta" — basta fazer o upload manual pela app (admin).
