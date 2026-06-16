# test_pdf.py - com o caminho corrigido
import os
import sys

# Adiciona o diretório atual ao path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# Importa as funções helper
from pdf_extractor import extract_products_from_pdf, extract_from_pdf

def testar_com_pdf_exemplo():
    # CAMINHO CORRIGIDO - usa o Download.pdf que já existe
    pdf_path = "C:/Users/Ruben/Desktop/Projects/folhetosmart/Download.pdf"

    if not os.path.exists(pdf_path):
        print(f"❌ ERRO: PDF não encontrado em: {pdf_path}")
        return

    print(f"\n📄 A processar: {pdf_path}")
    print("=" * 60)

    # Tenta extrair produtos e preços
    produtos = extract_products_from_pdf(pdf_path)

    if produtos:
        print(f"\n✅ Encontrados {len(produtos)} produtos:")
        print("-" * 60)
        for i, p in enumerate(produtos[:10], 1):
            nome = p['produto'][:45]
            print(f"{i:2d}. {nome:<45} €{p['preco']:.2f}")

        if len(produtos) > 10:
            print(f"\n... e mais {len(produtos) - 10} produtos")

        # Guarda num ficheiro JSON
        import json
        with open('produtos_extraidos.json', 'w', encoding='utf-8') as f:
            json.dump(produtos, f, indent=2, ensure_ascii=False)
        print(f"\n💾 Dados guardados em 'produtos_extraidos.json'")

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
            print("Verifique se o Tesseract está instalado ou se o PDF é uma imagem.")

if __name__ == "__main__":
    print("🧪 TESTE DE EXTRAÇÃO DE PDFS")
    print("=" * 60)
    testar_com_pdf_exemplo()
