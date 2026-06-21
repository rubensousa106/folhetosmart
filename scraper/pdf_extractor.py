# scraper/pdf_extractor.py - VERSÃO COMPLETA UNIFICADA
import os
import json
import logging
import re
import pdfplumber
from anthropic import Anthropic

# ============================================================
# CARREGAR .env
# ============================================================
def load_env_file():
    possible_paths = [
        os.path.join(os.path.dirname(__file__), '.env'),
        os.path.join(os.path.dirname(__file__), '..', '.env'),
        os.path.join(os.getcwd(), '.env'),
        '.env',
    ]
    for env_path in possible_paths:
        if os.path.exists(env_path):
            print(f"✅ .env encontrado em: {env_path}")
            with open(env_path, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#') and '=' in line:
                        key, value = line.split('=', 1)
                        os.environ[key.strip()] = value.strip()
            return True
    print("❌ .env NÃO encontrado")
    return False

load_env_file()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class PDFExtractor:
    def __init__(self, api_key=None, model=None):
        self.api_key = api_key or os.getenv("ANTHROPIC_API_KEY")
        self.model = model or os.getenv("ANTHROPIC_MODEL")

        if not self.model:
            raise ValueError("❌ ANTHROPIC_MODEL não definido no .env!")

        print(f"🔑 API Key: {'✅' if self.api_key else '❌'}")
        print(f"📦 Modelo: {self.model}")

        if self.api_key:
            self.client = Anthropic(api_key=self.api_key)
            logger.info(f"✅ Claude API configurada (global) - modelo: {self.model}")
        else:
            self.client = None
            logger.warning("⚠️ ANTHROPIC_API_KEY não configurada")

    def detectar_supermercado(self, pdf_path):
        """Deteta o supermercado a partir do texto do PDF ou nome do ficheiro"""
        text = self.extract_text_from_pdf(pdf_path)
        if text:
            texto_lower = text.lower()
            supermercados = {
                "continente": "Continente",
                "pingo doce": "Pingo Doce",
                "pingodoce": "Pingo Doce",
                "lidl": "Lidl",
                "aldi": "Aldi",
                "intermarche": "Intermarché",
                "intermarché": "Intermarché"
            }
            for chave, nome in supermercados.items():
                if chave in texto_lower:
                    return nome

        nome_ficheiro = os.path.basename(pdf_path).lower()
        supermercados = {
            "continente": "Continente",
            "pingo": "Pingo Doce",
            "lidl": "Lidl",
            "aldi": "Aldi",
            "intermarche": "Intermarché",
            "intermarché": "Intermarché"
        }
        for chave, nome in supermercados.items():
            if chave in nome_ficheiro:
                return nome

        return "Desconhecido"

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
            logger.error(f"Erro: {e}")
            return None

    def extract_structured_data_por_paginas(self, pdf_path, supermarket=None):
        """Extrai produtos página a página com IA - processa TODAS as páginas com conteúdo"""
        if supermarket is None:
            supermarket = self.detectar_supermercado(pdf_path)
            logger.info(f"🛒 Supermercado detetado: {supermarket}")

        if not self.client:
            logger.error("Claude API não disponível")
            return self._fallback_extraction(pdf_path)

        text = self.extract_text_from_pdf(pdf_path)
        if not text:
            return []

        pages = text.split("=== PÁGINA")
        todos_produtos = []
        produtos_vistos = set()

        # Processa TODAS as páginas com conteúdo
        paginas_para_processar = []
        for i, page_text in enumerate(pages):
            if not page_text.strip() or len(page_text.strip()) < 50:
                continue
            if i == len(pages) - 1:  # Ignora a última página (info legal)
                continue
            paginas_para_processar.append((i, page_text))

        logger.info(f"📄 A processar {len(paginas_para_processar)} páginas com conteúdo")

        for i, page_text in paginas_para_processar:
            logger.info(f"📄 A processar página {i+1}...")
            page_text_limited = page_text[:8000]

            prompt = f"""
            Extrai todos os produtos e preços desta página do folheto do supermercado {supermarket}.

            Regras:
            1. Nome do produto: junta linhas se necessário e INCLUI a quantidade/embalagem
               quando aparecer (ex.: "1L", "500g", "6x1L", "Pack 6") — serve para distinguir
               individual de pack.
            2. Preço (apenas o número): devolve o PREÇO UNITÁRIO que o cliente paga pela
               embalagem mostrada. Se houver vários preços, usa o preço de VENDA em destaque
               (promocional) — NÃO o preço por kg/litro, NÃO o preço barrado/anterior. Só usa
               o preço por kg se o produto for mesmo vendido a peso.
            3. Se o mesmo produto tiver versão individual E pack, devolve as DUAS como produtos
               separados (com a quantidade no nome).
            4. Devolve APENAS um array JSON válido.

            Exemplo: [{{"produto": "Leite UHT Agros Meio Gordo 1L", "preco": 0.88}}, {{"produto": "Bacalhau Graúdo 1ª", "preco": 17.99}}]

            Texto da página:
            {page_text_limited}

            Responde APENAS com o JSON, sem mais texto.
            """

            try:
                response = self.client.messages.create(
                    model=self.model,
                    max_tokens=4096,
                    temperature=0.1,
                    messages=[{"role": "user", "content": prompt}]
                )

                result = response.content[0].text.strip()

                if result.startswith("```json"):
                    result = result[7:]
                if result.endswith("```"):
                    result = result[:-3]
                if result.startswith("```"):
                    result = result[3:]

                try:
                    produtos_pagina = json.loads(result)
                except json.JSONDecodeError:
                    logger.warning(f"⚠️ Página {i+1}: JSON inválido, a tentar reparar...")
                    json_match = re.search(r'\[\s*\{.*\}\s*\]', result, re.DOTALL)
                    if json_match:
                        try:
                            produtos_pagina = json.loads(json_match.group())
                        except:
                            produtos_pagina = []
                    else:
                        produtos_pagina = []

                if isinstance(produtos_pagina, list):
                    for p in produtos_pagina:
                        if isinstance(p, dict) and "produto" in p and "preco" in p:
                            key = f"{p['produto'][:20]}_{p['preco']:.2f}"
                            if key not in produtos_vistos:
                                produtos_vistos.add(key)
                                todos_produtos.append(p)
                    logger.info(f"✅ Página {i+1}: {len(produtos_pagina)} produtos")
                elif isinstance(produtos_pagina, dict) and "produtos" in produtos_pagina:
                    for p in produtos_pagina["produtos"]:
                        if isinstance(p, dict) and "produto" in p and "preco" in p:
                            key = f"{p['produto'][:20]}_{p['preco']:.2f}"
                            if key not in produtos_vistos:
                                produtos_vistos.add(key)
                                todos_produtos.append(p)
                    logger.info(f"✅ Página {i+1}: {len(produtos_pagina['produtos'])} produtos")
                else:
                    logger.warning(f"⚠️ Página {i+1}: formato inesperado")

            except Exception as e:
                logger.error(f"❌ Página {i+1}: {e}")

        logger.info(f"✅ Total: {len(todos_produtos)} produtos extraídos do {supermarket}")
        return todos_produtos

    def _fallback_extraction(self, pdf_path):
        """Fallback: extração básica sem IA"""
        logger.warning("A usar fallback (extração básica)")
        text = self.extract_text_from_pdf(pdf_path)
        if not text:
            return []

        products = []
        lines = text.split('\n')
        produtos_vistos = set()

        price_pattern = re.compile(r'(\d+[,\.]\d{2})\s*[€]?')

        for line in lines:
            line = line.strip()
            if not line or len(line) < 5:
                continue

            matches = price_pattern.findall(line)
            for price_str in matches:
                try:
                    price_clean = price_str.replace(',', '.').strip()
                    if re.match(r'^\d+\.\d{2}$', price_clean):
                        price = float(price_clean)
                        if 0.01 <= price <= 999.99:
                            parts = line.split(price_str)
                            if parts:
                                product = parts[0].strip()
                                product = re.sub(r'^[\d\s\-\.]+', '', product)
                                product = re.sub(r'[^A-Za-zçãõáéíóúÀ-Ú\s\-\.]', '', product)
                                if len(product) > 2:
                                    key = f"{product[:20]}_{price:.2f}"
                                    if key not in produtos_vistos:
                                        produtos_vistos.add(key)
                                        products.append({
                                            'produto': product[:60],
                                            'preco': price
                                        })
                except ValueError:
                    continue

        logger.info(f"✅ Fallback: {len(products)} produtos extraídos")
        return products


# ============================================================
# FUNÇÕES HELPER (FORA DA CLASSE)
# ============================================================
def extract_from_pdf(pdf_path):
    """Extrai texto do PDF"""
    extractor = PDFExtractor()
    return extractor.extract_text_from_pdf(pdf_path)

def extract_products_from_pdf(pdf_path, supermarket=None):
    """Extrai produtos do PDF usando IA (página a página)"""
    extractor = PDFExtractor()
    return extractor.extract_structured_data_por_paginas(pdf_path, supermarket)


# ============================================================
# FUNÇÕES OCR PARA EXTRAÇÃO DE BLOCOS DE IMAGEM
# ============================================================
def extract_blocks_from_image(image_path):
    """
    Extrai blocos de texto de uma imagem usando OCR.

    Args:
        image_path: Caminho da imagem (ou objeto PIL Image)

    Returns:
        list: Lista de blocos com texto e coordenadas
    """
    try:
        import pytesseract
        from PIL import Image
    except ImportError:
        logging.error("pytesseract ou PIL não instalados")
        return []

    if isinstance(image_path, str):
        image = Image.open(image_path)
    else:
        image = image_path

    custom_config = r'--oem 3 --psm 6 -l por'
    data = pytesseract.image_to_data(image, config=custom_config, output_type=pytesseract.Output.DICT)

    blocks = []
    n_boxes = len(data['level'])

    for i in range(n_boxes):
        if data['text'][i].strip() and int(data['conf'][i]) > 30:
            blocks.append({
                'text': data['text'][i],
                'left': data['left'][i],
                'top': data['top'][i],
                'width': data['width'][i],
                'height': data['height'][i],
                'conf': data['conf'][i]
            })

    return blocks

def extract_blocks_from_images(image_paths):
    """
    Extrai blocos de texto de múltiplas imagens.
    """
    all_blocks = []
    for image_path in image_paths:
        blocks = extract_blocks_from_image(image_path)
        all_blocks.extend(blocks)
    return all_blocks

def extract_blocks_from_pdf(pdf_path, dpi=300):
    """
    Extrai blocos de texto de todas as páginas de um PDF.
    """
    try:
        from pdf2image import convert_from_path
    except ImportError:
        logging.error("pdf2image não instalado")
        return []

    try:
        images = convert_from_path(pdf_path, dpi=dpi)
        all_blocks = []
        for i, image in enumerate(images):
            blocks = extract_blocks_from_image(image)
            for block in blocks:
                block['page'] = i + 1
            all_blocks.extend(blocks)
        return all_blocks
    except Exception as e:
        logging.error(f"Erro ao processar PDF: {e}")
        return []

def extract_text_from_image(image_path):
    """
    Extrai texto simples de uma imagem.
    """
    blocks = extract_blocks_from_image(image_path)
    return "\n".join([block['text'] for block in blocks])

def extract_text_from_images(image_paths):
    """
    Extrai texto de múltiplas imagens.
    """
    all_text = []
    for image_path in image_paths:
        text = extract_text_from_image(image_path)
        all_text.append(text)
    return "\n".join(all_text)

def extract_text_from_pdf(pdf_path, dpi=300):
    """
    Extrai texto de todas as páginas de um PDF.
    """
    try:
        from pdf2image import convert_from_path
    except ImportError:
        logging.error("pdf2image não instalado")
        return ""

    try:
        images = convert_from_path(pdf_path, dpi=dpi)
        all_text = []
        for i, image in enumerate(images):
            text = extract_text_from_image(image)
            all_text.append(f"--- PÁGINA {i+1} ---\n{text}")
        return "\n".join(all_text)
    except Exception as e:
        logging.error(f"Erro ao processar PDF: {e}")
        return ""
