# list_drive.py - Versão completa adaptada
import os
import io
import json
import requests
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload
from pdf_extractor import extract_products_from_pdf  # NOVO

class DriveDownloader:
    def __init__(self, credentials_path='credentials.json'):
        self.creds = Credentials.from_authorized_user_file(credentials_path)
        self.service = build('drive', 'v3', credentials=self.creds)

    def list_pdfs(self, folder_id):
        """Lista todos os PDFs numa pasta do Google Drive"""
        query = f"'{folder_id}' in parents and mimeType='application/pdf'"
        results = self.service.files().list(
            q=query,
            pageSize=100,
            fields="files(id, name)"
        ).execute()
        return results.get('files', [])

    def download_pdf(self, file_id, file_name):
        """Descarrega um PDF do Google Drive"""
        request = self.service.files().get_media(fileId=file_id)
        file_path = os.path.join('/tmp', file_name)  # Usa /tmp no Render

        with open(file_path, 'wb') as f:
            downloader = MediaIoBaseDownload(f, request)
            done = False
            while not done:
                status, done = downloader.next_chunk()
                print(f"Download: {int(status.progress() * 100)}%")

        return file_path

    def processar_supermercado(self, folder_id, supermercado):
        """
        Processa todos os PDFs de um supermercado
        """
        pdfs = self.list_pdfs(folder_id)
        print(f"📁 Encontrados {len(pdfs)} PDFs para {supermercado}")

        todos_produtos = []

        for pdf in pdfs:
            try:
                # Descarrega o PDF
                caminho_pdf = self.download_pdf(pdf['id'], pdf['name'])

                # Extrai produtos usando o NOVO extrator
                produtos = extract_products_from_pdf(caminho_pdf)

                if produtos:
                    for p in produtos:
                        p['supermercado'] = supermercado
                        p['origem'] = pdf['name']
                    todos_produtos.extend(produtos)
                    print(f"✅ {pdf['name']}: {len(produtos)} produtos")
                else:
                    print(f"⚠️ {pdf['name']}: sem produtos")

                # Remove o ficheiro temporário
                os.remove(caminho_pdf)

            except Exception as e:
                print(f"❌ Erro em {pdf['name']}: {e}")

        return todos_produtos

# Exemplo de uso
if __name__ == "__main__":
    downloader = DriveDownloader()

    # Mapeamento dos IDs das pastas no Drive
    SUPERMERCADOS = {
        'pingo_doce': 'ID_DA_PASTA_PINGO_DOCE',
        'continente': 'ID_DA_PASTA_CONTINENTE',
        'lidl': 'ID_DA_PASTA_LIDL'
    }

    for nome, folder_id in SUPERMERCADOS.items():
        produtos = downloader.processar_supermercado(folder_id, nome)
        print(f"\n📊 {nome}: {len(produtos)} produtos extraídos")

        # Opcional: Envia para o backend
        # requests.post('http://backend:8080/api/v1/products/batch', json=produtos)
