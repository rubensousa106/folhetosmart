# scrapers/lidl.py
import os
import requests
from bs4 import BeautifulSoup
from datetime import datetime
from storage.r2_storage import r2_storage
import logging
import re

logger = logging.getLogger("Lidl")

def run(region="pt"):
    logger.info("🛒 A iniciar scraper do Lidl...")

    # URL da página do folheto (atualizar semanalmente)
    # Exemplo: https://www.lidl.pt/l/pt/folhetos/novidades-a-partir-de-22-06/view/menu/page/1?lf=HHZ
    base_url = "https://www.lidl.pt/l/pt/folhetos"
    # Para esta semana, usamos o URL específico:
    page_url = "https://www.lidl.pt/l/pt/folhetos/novidades-a-partir-de-22-06/view/menu/page/1?lf=HHZ"

    logger.info(f"📄 A aceder a: {page_url}")

    try:
        # 1. Acede à página do folheto
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "pt-PT,pt;q=0.9,en;q=0.8",
        }
        response = requests.get(page_url, headers=headers, timeout=30)
        response.raise_for_status()

        # 2. Procura o link do PDF na página
        soup = BeautifulSoup(response.text, 'html.parser')

        # Procura por um link que contenha ".pdf" ou que seja o botão "Descarregar PDF"
        pdf_link = soup.find('a', href=re.compile(r'\.pdf$'))
        if not pdf_link:
            # Tenta encontrar o botão de download
            pdf_link = soup.find('a', string=re.compile(r'Descarregar|Download', re.I))
            if pdf_link:
                pdf_link = soup.find('a', href=True, string=re.compile(r'Descarregar|Download', re.I))

        if pdf_link:
            pdf_url = pdf_link.get('href')
            # Se o URL for relativo, constrói o URL absoluto
            if pdf_url.startswith('/'):
                pdf_url = f"https://www.lidl.pt{pdf_url}"
            logger.info(f"📄 PDF encontrado: {pdf_url}")
        else:
            # Fallback: tenta encontrar qualquer link com .pdf no href
            all_links = soup.find_all('a', href=True)
            for link in all_links:
                href = link.get('href')
                if href and '.pdf' in href.lower():
                    pdf_url = href if href.startswith('http') else f"https://www.lidl.pt{href}"
                    logger.info(f"📄 PDF encontrado (fallback): {pdf_url}")
                    break
            else:
                logger.error("❌ Não foi possível encontrar o link do PDF na página")
                return

        # 3. Descarrega o PDF
        logger.info(f"📥 A descarregar PDF...")
        pdf_response = requests.get(pdf_url, headers=headers, timeout=60)
        pdf_response.raise_for_status()

        # 4. Guarda no R2
        data = datetime.now().strftime("%d-%m-%Y")
        filename = f"Lidl_{data}.pdf"
        r2_storage.upload_bytes(pdf_response.content, filename)
        logger.info(f"✅ {filename} carregado para o R2 ({len(pdf_response.content)} bytes)")
        print(f"R2 key: {filename}")

    except Exception as e:
        logger.error(f"❌ Erro: {e}")

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    run()
