# Produtor de folhetos FolhetoSmart (modelo Cloudflare R2).
# Extrai os PDFs com a Claude (pdf_extractor), normaliza e publica o feed.
# Sem Playwright/OCR/Tesseract: o folheto vem do R2 (upload do admin) ou do
# site do Aldi (requests + BeautifulSoup) — nada disto precisa de browser.
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
# --trusted-host: tolera redes com inspeção TLS — sem efeito em redes normais.
RUN pip install --no-cache-dir \
        --trusted-host pypi.org --trusted-host files.pythonhosted.org \
        -r requirements.txt

COPY . .

# Torna os pacotes do projeto (scrapers, storage, config, notifications)
# importáveis a partir de qualquer script dentro do container.
ENV PYTHONPATH=/app

# Por omissão corre o produtor; o workflow/Cowork pode sobrepor o comando
# (ex.: python normalize_products.py, python scrapers/aldi.py --all-regions).
CMD ["python", "drive_producer.py"]
