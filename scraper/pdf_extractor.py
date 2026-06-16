# list_drive.py - Versão modificada
import os
from pdf_extractor import extract_products_from_pdf  # Importa o novo

def processar_pdf(caminho_pdf, supermercado):
    """
    Processa um PDF baixado do Drive
    """
    print(f"📄 A processar: {caminho_pdf}")

    # Usa o novo extrator
    produtos = extract_products_from_pdf(caminho_pdf)

    if not produtos:
        print("❌ Nenhum produto encontrado")
        return []

    # Adiciona o supermercado a cada produto
    for produto in produtos:
        produto['supermercado'] = supermercado

    print(f"✅ Encontrados {len(produtos)} produtos")
    return produtos

# O resto do seu código list_drive.py continua igual
# ...
