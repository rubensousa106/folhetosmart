# pdf_extractor.py - VERSÃO COM IA (SUBSTITUI O ANTIGO)
import os
import json
import logging
import pdfplumber
from anthropic import Anthropic

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class PDFExtractor:
    """Extrator de produtos usando IA (Claude) para PDFs complexos"""

    def __init__(self, api_key=None):
        self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
        if not self.api_key:
            logger.warning("ANTHROPIC_API_KEY não configurada - a usar fallback")
        self.client = Anthropic(api_key=self.api_key) if self.api_key else None

    def extract_text_from_pdf(self, pdf_path):
        """Extrai texto bruto do PDF"""
        if not os.path.exists(pdf_path):
            logger.error(f"Ficheiro não encontrado: {pdf_path}")
            return None

        try:
            full_text = ""
            with pdfplumber.open(pdf_path) as pdf:
                for page_num, page in enumerate(pdf.pages, 1):
                    text = page.extract_text()
                    if text:
                        full_text += f"\n=== PÁGINA {page_num} ===\n{text}"

            return full_text
        except Exception as e:
            logger.error(f"Erro ao extrair texto: {e}")
            return None

    def extract_structured_data(self, pdf_path, supermarket="Continente"):
        """Extrai produtos usando IA"""
        if not self.client:
            logger.error("Claude API não disponível")
            return self._fallback_extraction(pdf_path)

        text = self.extract_text_from_pdf(pdf_path)
        if not text:
            return []

        # Pega apenas as páginas com produtos (ajuste conforme necessário)
        pages = text.split("=== PÁGINA")
        product_pages = pages[2:-2] if len(pages) > 4 else pages
        limited_text = "".join(product_pages)[:8000]

        prompt = f"""
        Extrai todos os produtos e preços deste folheto do supermercado {supermarket}.

        Regras:
        1. Identifica o nome do produto (pode estar em várias linhas)
        2. Identifica o preço (pode ser "5,99€", "5.99", "5,99")
        3. Ignora códigos de barras, percentagens, descrições longas
        4. Devolve apenas uma lista JSON com: {{"produto": "nome", "preco": 5.99}}
        5. Se o preço for por KG, converte para o preço normal

        Texto do folheto:
        {limited_text}

        Responde APENAS com um JSON válido, sem mais texto.
        """

        try:
            response = self.client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=4096,
                temperature=0.1,
                messages=[{"role": "user", "content": prompt}]
            )

            result = response.content[0].text.strip()
            # Limpa markdown
            if result.startswith("```json"):
                result = result[7:]
            if result.endswith("```"):
                result = result[:-3]

            products = json.loads(result)

            if isinstance(products, dict) and "produtos" in products:
                products = products["produtos"]
            elif isinstance(products, dict):
                products = [products]

            logger.info(f"✅ Extraídos {len(products)} produtos com IA")
            return products

        except Exception as e:
            logger.error(f"Erro na IA: {e}")
            return self._fallback_extraction(pdf_path)

    def _fallback_extraction(self, pdf_path):
        """Fallback: extração básica sem IA"""
        logger.warning("A usar fallback (extração básica)")
        text = self.extract_text_from_pdf(pdf_path)
        if not text:
            return []

        # Padrão simples para encontrar preços
        import re
        products = []
        lines = text.split('\n')
        for line in lines:
            match = re.search(r'([\d,]+)\s*[€]', line)
            if match:
                price = float(match.group(1).replace(',', '.'))
                # Tenta encontrar o nome do produto antes do preço
                parts = line.split(match.group(1))
                if parts:
                    product = parts[0].strip()
                    if product and len(product) > 3:
                        products.append({
                            'produto': product[:50],
                            'preco': price
                        })

        return products


# Funções helper (mantêm a compatibilidade com o test_pdf.py)
def extract_from_pdf(pdf_path):
    """Função simples para extrair texto de um PDF"""
    extractor = PDFExtractor()
    return extractor.extract_text_from_pdf(pdf_path)

def extract_products_from_pdf(pdf_path, supermarket="Continente"):
    """Função simples para extrair produtos e preços"""
    extractor = PDFExtractor()
    return extractor.extract_structured_data(pdf_path, supermarket)
