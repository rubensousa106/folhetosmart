# processar_folheto.py - Processa e faz upload automático
import os
import sys

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from test_pdf import processar_pdf_para_json
from upload_to_drive import upload_json


def processar_folheto(pdf_path, supermercado=None):
    """
    Processa um folheto e guarda o JSON no Google Drive
    """
    print("=" * 60)
    print(f"📄 A processar: {pdf_path}")
    print("=" * 60)

    # 1. Extrai produtos e gera JSON
    result = processar_pdf_para_json(pdf_path, supermercado)

    if not result.get('success'):
        print(f"❌ Erro ao processar: {result.get('error')}")
        return result

    json_file = result.get('json_file')
    supermarket = result.get('supermercado')
    total_produtos = result.get('total_produtos')

    print(f"\n✅ JSON gerado: {json_file}")
    print(f"   📊 Supermercado: {supermarket}")
    print(f"   📦 Total produtos: {total_produtos}")

    # 2. Faz upload para o Drive
    print(f"\n📤 A fazer upload para o Google Drive...")
    upload_result = upload_json(json_file, supermarket)

    if upload_result.get('success'):
        print(f"\n✅ Upload concluído!")
        print(f"   🔗 Link: {upload_result.get('link')}")
    else:
        print(f"\n❌ Erro no upload: {upload_result.get('error')}")

    return {
        "success": True,
        "json_file": json_file,
        "supermercado": supermarket,
        "total_produtos": total_produtos,
        "upload": upload_result
    }


if __name__ == "__main__":
    pdf_path = "C:/Users/Ruben/Desktop/Projects/folhetosmart/Download.pdf"
    processar_folheto(pdf_path)
