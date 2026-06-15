# Scraper + IA + worker HTTP
FROM python:3.12-slim

# Dependências de sistema: Tesseract (OCR PT) + Poppler (pdf2image) +
# libgomp1/libglib (runtime do OpenCV headless) + libs do Playwright
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-por \
        poppler-utils \
        libgomp1 \
        libglib2.0-0 \
        wget \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
# --trusted-host: tolera redes com inspeção TLS — sem efeito em redes normais.
RUN pip install --no-cache-dir \
        --trusted-host pypi.org --trusted-host files.pythonhosted.org \
        -r requirements.txt

# Browser do Playwright (Chromium) em dois passos:
# 1) dependências de sistema via apt; 2) download do browser.
# NODE_TLS_REJECT_UNAUTHORIZED=0 tolera redes com inspeção TLS no download
# (sem efeito em redes normais; apenas em build).
RUN python -m playwright install-deps chromium
RUN NODE_TLS_REJECT_UNAUTHORIZED=0 python -m playwright install chromium

COPY . .

# Garante que os pacotes do projeto (ai_matcher, scrapers, config, ...) são
# importáveis a partir de qualquer diretório/script dentro do container —
# ex.: correr scrapers/lidl.py diretamente a partir de /app/scrapers.
ENV PYTHONPATH=/app

ENV WORKER_PORT=8090
EXPOSE 8090

# Worker HTTP por omissão; o scheduler corre noutro processo/container.
CMD ["uvicorn", "worker:app", "--host", "0.0.0.0", "--port", "8090"]
