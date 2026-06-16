import os
import logging
import tempfile
from pathlib import Path
import pdfplumber
import pytesseract
from pdf2image import convert_from_path
from PIL import Image
import re

# Configurar logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class PDFExtractor:
    """Classe para extrair texto de PDFs (texto ou imagem)"""

    def __init__(self, tesseract_cmd=None):
        """
        Inicializa o extrator de PDFs

        Args:
            tesseract_cmd: Caminho para o executável do Tesseract (opcional)
        """
        if tesseract_cmd:
            pytesseract.pytesseract.tesseract_cmd = tesseract_cmd
        self.temp_dir = tempfile.gettempdir()

    def extract_text(self, pdf_path):
        """
        Extrai texto de um PDF, usando OCR se necessário

        Args:
            pdf_path: Caminho para o ficheiro PDF

        Returns:
            str: Texto extraído ou None se falhar
        """
        if not os.path.exists(pdf_path):
            logger.error(f"Ficheiro não encontrado: {pdf_path}")
            return None

        try:
            logger.info(f"A processar: {pdf_path}")

            # Primeiro, tenta extrair texto diretamente
            with pdfplumber.open(pdf_path) as pdf:
                text = ""
                for page_num, page in enumerate(pdf.pages, 1):
                    page_text = page.extract_text()
                    if page_text and page_text.strip():
                        text += f"\n--- Página {page_num} ---\n"
                        text += page_text
                        logger.info(f"Página {page_num}: Texto extraído ({len(page_text)} caracteres)")
                    else:
                        logger.warning(f"Página {page_num}: Sem texto - a usar OCR")
                        # Se não houver texto, usa OCR
                        ocr_text = self._extract_with_ocr(pdf_path, page_num - 1)
                        if ocr_text:
                            text += f"\n--- Página {page_num} (OCR) ---\n"
                            text += ocr_text

                if not text.strip():
                    logger.warning("Nenhum texto encontrado no PDF")
                    return None

                return text

        except Exception as e:
            logger.error(f"Erro ao extrair texto: {e}")
            return None

    def _extract_with_ocr(self, pdf_path, page_num):
        """
        Extrai texto de uma página usando OCR

        Args:
            pdf_path: Caminho para o PDF
            page_num: Número da página (0-indexed)

        Returns:
            str: Texto extraído ou None
        """
        try:
            logger.info(f"A converter página {page_num+1} para imagem...")
            images = convert_from_path(
                pdf_path,
                first_page=page_num+1,
                last_page=page_num+1,
                dpi=300
            )

            if not images:
                logger.error("Falha ao converter página para imagem")
                return None

            logger.info("A executar OCR...")
            # Configurar Tesseract para português
            custom_config = r'--oem 3 --psm 6 -l por'
            text = pytesseract.image_to_string(images[0], config=custom_config)

            logger.info(f"OCR concluído: {len(text)} caracteres extraídos")
            return text

        except Exception as e:
            logger.error(f"Erro no OCR: {e}")
            return None

    def extract_structured_data(self, pdf_path):
        """
        Extrai dados estruturados (produtos e preços) do PDF

        Args:
            pdf_path: Caminho para o PDF

        Returns:
            list: Lista de dicionários com produto e preço
        """
        text = self.extract_text(pdf_path)
        if not text:
            return []

        # Padrões para diferentes formatos de preço
        patterns = [
            # Padrão: "produto 5,99€"
            r'([A-Za-zçãõáéíóúÀ-Ú\s\-\.]+?)\s+([\d,]+)\s*[€]',
            # Padrão: "5,99€ produto"
            r'([\d,]+)\s*[€]\s+([A-Za-zçãõáéíóúÀ-Ú\s\-\.]+)',
            # Padrão: "produto €5,99"
            r'([A-Za-zçãõáéíóúÀ-Ú\s\-\.]+?)\s*[€]\s*([\d,]+)',
            # Padrão: "produto 5.99"
            r'([A-Za-zçãõáéíóúÀ-Ú\s\-\.]+?)\s+([\d]+\.[\d]{2})',
        ]

        products = []
        lines = text.split('\n')

        for line in lines:
            line = line.strip()
            if not line:
                continue

            for pattern in patterns:
                matches = re.findall(pattern, line, re.IGNORECASE)
                for match in matches:
                    if len(match) == 2:
                        # Determina qual é o produto e qual é o preço
                        if re.match(r'^[\d,\.]+$', match[0]):
                            price, product = match
                        else:
                            product, price = match

                        # Limpa e converte o preço
                        price = price.replace(',', '.').replace('€', '').strip()
                        try:
                            price_float = float(price)
                            products.append({
                                'produto': product.strip(),
                                'preco': price_float,
                                'raw_line': line
                            })
                        except ValueError:
                            continue

        return products

# Função helper para uso rápido
def extract_from_pdf(pdf_path):
    """Função simples para extrair texto de um PDF"""
    extractor = PDFExtractor()
    return extractor.extract_text(pdf_path)

def extract_products_from_pdf(pdf_path):
    """Função simples para extrair produtos e preços"""
    extractor = PDFExtractor()
    return extractor.extract_structured_data(pdf_path)
