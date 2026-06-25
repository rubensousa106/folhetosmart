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

# Fallback por VISÃO (folhetos só-imagem, sem camada de texto que o pdfplumber leia).
VISAO_MIN_PRODUTOS_POR_PAGINA = 1.0  # abaixo disto, o nº de produtos "não se adequa" às páginas
VISAO_ESCALA = 2.0                   # escala de render do PDF (≈144 DPI)
VISAO_MAX_LADO = 1568                # px no lado maior (limite útil da visão do Claude)

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

        text = self.extract_text_from_pdf(pdf_path) or ""

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

        logger.info(f"✅ Total (texto): {len(todos_produtos)} produtos extraídos do {supermarket}")

        # Fallback VISÃO (condicional): o texto deu 0 OU o nº de produtos não se
        # adequa ao nº de páginas (sinal de folheto só-imagem). Lê as páginas como
        # imagem. Fica com o melhor dos dois.
        n_total = self._contar_paginas(pdf_path)
        if n_total and (len(todos_produtos) == 0
                        or len(todos_produtos) < n_total * VISAO_MIN_PRODUTOS_POR_PAGINA):
            logger.warning(
                "🔍 %s: %d produtos para %d páginas — insuficiente; a tentar leitura por VISÃO…",
                supermarket, len(todos_produtos), n_total,
            )
            por_visao = self.extract_structured_data_por_visao(pdf_path, supermarket)
            if len(por_visao) > len(todos_produtos):
                logger.info("✅ VISÃO melhor: %d produtos (texto dava %d)",
                            len(por_visao), len(todos_produtos))
                return por_visao
            logger.info("ℹ️ VISÃO não melhorou (%d vs texto %d) — mantenho o texto",
                        len(por_visao), len(todos_produtos))

        return todos_produtos

    def _contar_paginas(self, pdf_path):
        """Nº total de páginas do PDF (0 se não abrir)."""
        try:
            with pdfplumber.open(pdf_path) as pdf:
                return len(pdf.pages)
        except Exception:
            return 0

    def _prompt_visao(self, supermarket):
        return f"""Esta imagem é uma página do folheto do supermercado {supermarket}.
        Extrai TODOS os produtos e preços VISÍVEIS na imagem.

        Regras:
        1. Nome do produto: INCLUI a quantidade/embalagem quando aparecer (ex.: "1L",
           "500g", "6x1L", "Pack 6") — serve para distinguir individual de pack.
        2. Preço (apenas o número): o PREÇO UNITÁRIO que o cliente paga pela embalagem
           mostrada. Se houver vários, usa o preço de VENDA em destaque (promocional) —
           NÃO o preço por kg/litro, NÃO o preço barrado/anterior. Só usa o preço por kg
           se o produto for mesmo vendido a peso.
        3. Se houver versão individual E pack, devolve as DUAS (com a quantidade no nome).
        4. Devolve APENAS um array JSON válido (lista vazia se a página não tiver produtos).

        Exemplo: [{{"produto": "Leite UHT Agros Meio Gordo 1L", "preco": 0.88}}]

        Responde APENAS com o JSON, sem mais texto.
        """

    def _claude_produtos(self, content, page_label):
        """Pede ao Claude os produtos de UMA página (content = blocos texto, ou
        imagem+texto) e devolve [{produto, preco}] (sem dedup — o chamador trata)."""
        try:
            response = self.client.messages.create(
                model=self.model, max_tokens=4096, temperature=0.1,
                messages=[{"role": "user", "content": content}],
            )
            result = response.content[0].text.strip()
        except Exception as e:
            logger.error(f"❌ {page_label}: {e}")
            return []

        if result.startswith("```json"):
            result = result[7:]
        elif result.startswith("```"):
            result = result[3:]
        if result.endswith("```"):
            result = result[:-3]
        result = result.strip()

        try:
            data = json.loads(result)
        except json.JSONDecodeError:
            logger.warning(f"⚠️ {page_label}: JSON inválido, a tentar reparar...")
            m = re.search(r"\[\s*\{.*\}\s*\]", result, re.DOTALL)
            try:
                data = json.loads(m.group()) if m else []
            except Exception:
                data = []
        if isinstance(data, dict) and "produtos" in data:
            data = data["produtos"]
        if not isinstance(data, list):
            return []
        return [p for p in data if isinstance(p, dict) and "produto" in p and "preco" in p]

    @staticmethod
    def _dedup_add(produtos, todos, vistos):
        """Acrescenta a `todos` os produtos ainda não vistos (chave nome+preço)."""
        for p in produtos:
            try:
                key = f"{str(p['produto'])[:20]}_{float(p['preco']):.2f}"
            except (TypeError, ValueError, KeyError):
                continue
            if key not in vistos:
                vistos.add(key)
                todos.append(p)

    def extract_structured_data_por_visao(self, pdf_path, supermarket):
        """Lê o folheto como IMAGENS (folhetos só-imagem que o texto não lê).
        Renderiza cada página com pypdfium2 e pede os produtos ao Claude (visão)."""
        if not self.client:
            return []
        try:
            import pypdfium2 as pdfium
        except ImportError:
            logger.error("VISÃO indisponível: instala pypdfium2 (pip install pypdfium2).")
            return []
        import base64
        import io

        try:
            pdf = pdfium.PdfDocument(pdf_path)
        except Exception as e:
            logger.error(f"VISÃO: não consegui abrir o PDF ({e})")
            return []

        todos_produtos = []
        produtos_vistos = set()
        prompt = self._prompt_visao(supermarket)
        n = len(pdf)
        logger.info(f"🖼️ VISÃO: {n} páginas a renderizar para {supermarket}")
        try:
            for i in range(n):
                if i == n - 1:  # última página = info legal
                    continue
                try:
                    pil = pdf[i].render(scale=VISAO_ESCALA).to_pil().convert("RGB")
                except Exception as e:
                    logger.warning(f"VISÃO: página {i+1} não renderizou ({e})")
                    continue
                pil.thumbnail((VISAO_MAX_LADO, VISAO_MAX_LADO))
                buf = io.BytesIO()
                pil.save(buf, format="PNG")
                b64 = base64.standard_b64encode(buf.getvalue()).decode()
                content = [
                    {"type": "image", "source": {
                        "type": "base64", "media_type": "image/png", "data": b64}},
                    {"type": "text", "text": prompt},
                ]
                produtos = self._claude_produtos(content, f"Página {i+1} (visão)")
                self._dedup_add(produtos, todos_produtos, produtos_vistos)
                logger.info(f"🖼️ Página {i+1} (visão): {len(produtos)} produtos")
        finally:
            pdf.close()

        logger.info(f"✅ Total (visão): {len(todos_produtos)} produtos extraídos do {supermarket}")
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


_MESES_PT = {
    "janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4, "maio": 5,
    "junho": 6, "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10,
    "novembro": 11, "dezembro": 12,
}


def _parse_date_range(text):
    """(início, fim) datetime.date a partir de um segmento com um intervalo PT."""
    import datetime as _dt
    # "DD [de MÊS] a DD de MÊS [de AAAA]"
    m = re.search(
        r"(\d{1,2})(?:\s+de\s+([a-zç]+))?\s+(?:a|at[ée])\s+(\d{1,2})\s+de\s+([a-zç]+)(?:\s+de\s+(\d{4}))?",
        text,
    )
    if m:
        d1, mes1, d2, mes2, ano = m.groups()
        mes2n = _MESES_PT.get(mes2)
        mes1n = _MESES_PT.get(mes1) if mes1 else mes2n
        ano = int(ano) if ano else _dt.date.today().year
        if mes1n and mes2n:
            try:
                ini = _dt.date(ano, mes1n, int(d1))
                fim = _dt.date(ano, mes2n, int(d2))
                if fim < ini:                       # intervalo a virar o ano
                    fim = _dt.date(ano + 1, mes2n, int(d2))
                return ini, fim
            except ValueError:
                pass
    # numérico "DD/MM[/AA(AA)] a DD/MM/AA(AA)" (também . - –)
    m = re.search(
        r"(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{2,4}))?\s*(?:a|at[ée]|-|–)\s*(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})",
        text,
    )
    if m:
        d1, mo1, y1, d2, mo2, y2 = m.groups()

        def _ano(s):
            v = int(s)
            return v + 2000 if v < 100 else v

        try:
            yy2 = _ano(y2)
            yy1 = _ano(y1) if y1 else yy2
            return _dt.date(yy1, int(mo1), int(d1)), _dt.date(yy2, int(mo2), int(d2))
        except ValueError:
            pass
    return None, None


def extract_validity_from_pdf(pdf_path, max_pages=3):
    """(início, fim) datetime.date a partir do texto das 1ªs páginas do folheto.
    Prefere o intervalo logo a seguir a "válid…" (a validade principal); senão usa
    o primeiro intervalo encontrado. (None, None) se não encontrar. Padrões PT:
      'Promoção válida de 23 a 29 de junho de 2026'
      'de 30 de junho a 6 de julho de 2026' · 'válida de 23/06 a 29/06/2026'
    """
    try:
        with pdfplumber.open(pdf_path) as pdf:
            n = min(max_pages, len(pdf.pages))
            text = " ".join((pdf.pages[i].extract_text() or "") for i in range(n))
    except Exception as e:  # noqa: BLE001
        logging.warning("Validade: não consegui ler o PDF (%s)", e)
        return None, None
    low = re.sub(r"\s+", " ", text).lower()

    # 1) Preferir o intervalo logo a seguir a "válid…" (ex.: "Promoção válida de …").
    vm = re.search(r"v[aá]lid\w*\s+(?:de\s+)?(.{0,70})", low)
    if vm:
        ini, fim = _parse_date_range(vm.group(1))
        if ini and fim:
            return ini, fim

    # 2) Caso contrário, o primeiro intervalo de datas no texto.
    return _parse_date_range(low)
