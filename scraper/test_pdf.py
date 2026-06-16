# test_pdf.py - VERSÃO COMPLETA E CORRIGIDA
import os
import sys
import json
import re
from datetime import datetime

# Adiciona o diretório atual ao path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Importa as funções helper
from pdf_extractor import extract_products_from_pdf, extract_from_pdf


def detectar_supermercado_do_nome_ficheiro(pdf_path):
    """Tenta extrair o nome do supermercado a partir do nome do ficheiro"""
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

    return None


def testar_com_pdf_exemplo():
    """Função principal de teste"""
    # CAMINHO DO PDF
    pdf_path = "C:/Users/Ruben/Desktop/Projects/folhetosmart/Download.pdf"

    if not os.path.exists(pdf_path):
        print(f"❌ ERRO: PDF não encontrado em: {pdf_path}")
        return

    print(f"\n📄 A processar: {pdf_path}")
    print("=" * 60)

    # ============================================================
    # EXTRAI PRODUTOS (usa IA página a página)
    # ============================================================
    produtos = extract_products_from_pdf(pdf_path)

    if produtos:
        print(f"\n✅ Encontrados {len(produtos)} produtos:")
        print("-" * 60)
        for i, p in enumerate(produtos[:10], 1):
            nome = p.get('produto', 'Desconhecido')[:45]
            preco = p.get('preco', 0)
            print(f"{i:2d}. {nome:<45} €{preco:.2f}")

        if len(produtos) > 10:
            print(f"\n... e mais {len(produtos) - 10} produtos")

        # ============================================================
        # DETECTA O SUPERMERCADO
        # ============================================================
        # Tenta extrair do texto
        texto_completo = extract_from_pdf(pdf_path)
        supermercado = None

        if texto_completo:
            texto_lower = texto_completo.lower()
            supermercados_map = {
                "continente": "Continente",
                "pingo doce": "Pingo Doce",
                "pingodoce": "Pingo Doce",
                "lidl": "Lidl",
                "aldi": "Aldi",
                "intermarche": "Intermarché",
                "intermarché": "Intermarché"
            }
            for chave, nome in supermercados_map.items():
                if chave in texto_lower:
                    supermercado = nome
                    break

        # Se não encontrar, tenta pelo nome do ficheiro
        if not supermercado:
            supermercado = detectar_supermercado_do_nome_ficheiro(pdf_path)

        # Fallback: "Desconhecido"
        if not supermercado:
            supermercado = "Desconhecido"

        # ============================================================
        # GUARDA O JSON COM NOME DINÂMICO
        # ============================================================
        data_atual = datetime.now().strftime("%d%m%y")
        nome_json = f"{supermercado}_{data_atual}.json"

        # Prepara os dados para guardar
        dados_para_guardar = {
            "supermercado": supermercado,
            "data_extracao": datetime.now().isoformat(),
            "total_produtos": len(produtos),
            "produtos": produtos
        }

        with open(nome_json, 'w', encoding='utf-8') as f:
            json.dump(dados_para_guardar, f, indent=2, ensure_ascii=False)

        print(f"\n💾 Dados guardados em '{nome_json}'")
        print(f"   📊 Supermercado: {supermercado}")
        print(f"   📅 Data: {data_atual}")
        print(f"   📦 Total: {len(produtos)} produtos")

    else:
        print("\n❌ Nenhum produto encontrado!")
        print("\n📌 A tentar extrair apenas texto para diagnóstico...")

        # Tenta extrair texto puro para diagnóstico
        texto = extract_from_pdf(pdf_path)

        if texto:
            print(f"\n✅ Texto extraído com sucesso (primeiros 500 caracteres):")
            print("-" * 60)
            print(texto[:500])
            print("-" * 60)
            print(f"\n📊 Total de caracteres extraídos: {len(texto)}")
        else:
            print("\n❌ Falha completa na extração de texto.")


def processar_pdf_para_json(pdf_path, supermercado=None):
    """
    Função para processar um PDF e guardar o JSON.
    Pode ser chamada pelo sync_manager.py
    """
    if not os.path.exists(pdf_path):
        return {"error": "PDF não encontrado"}

    # Extrai produtos
    produtos = extract_products_from_pdf(pdf_path, supermercado)

    if not produtos:
        return {"error": "Nenhum produto encontrado"}

    # Detecta supermercado se não for fornecido
    if not supermercado:
        texto = extract_from_pdf(pdf_path)
        if texto:
            texto_lower = texto.lower()
            supermercados_map = {
                "continente": "Continente",
                "pingo doce": "Pingo Doce",
                "pingodoce": "Pingo Doce",
                "lidl": "Lidl",
                "aldi": "Aldi",
                "intermarche": "Intermarché",
                "intermarché": "Intermarché"
            }
            for chave, nome in supermercados_map.items():
                if chave in texto_lower:
                    supermercado = nome
                    break

        if not supermercado:
            supermercado = detectar_supermercado_do_nome_ficheiro(pdf_path)

        if not supermercado:
            supermercado = "Desconhecido"

    # Gera nome do ficheiro
    data_atual = datetime.now().strftime("%d%m%y")
    nome_json = f"{supermercado}_{data_atual}.json"

    # Guarda
    dados = {
        "supermercado": supermercado,
        "data_extracao": datetime.now().isoformat(),
        "total_produtos": len(produtos),
        "produtos": produtos
    }

    with open(nome_json, 'w', encoding='utf-8') as f:
        json.dump(dados, f, indent=2, ensure_ascii=False)

    return {
        "success": True,
        "json_file": nome_json,
        "total_produtos": len(produtos),
        "supermercado": supermercado
    }


if __name__ == "__main__":
    print("🧪 TESTE DE EXTRAÇÃO DE PDFS")
    print("=" * 60)
    testar_com_pdf_exemplo()
