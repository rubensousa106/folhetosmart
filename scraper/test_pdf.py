# testar_pdf.py
import os
from pdf_extractor import extract_products_from_pdf

# Testa com um PDF que sabe que existe
caminho_teste = "/tmp/folheto_teste.pdf"

if os.path.exists(caminho_teste):
    produtos = extract_products_from_pdf(caminho_teste)
    print(f"Encontrados {len(produtos)} produtos")
    for p in produtos[:5]:
        print(f"- {p['produto']}: €{p['preco']}")
else:
    print(f"PDF não encontrado em {caminho_teste}")
    print("Coloque um PDF em /tmp/folheto_teste.pdf para testar")
