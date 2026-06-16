# sync_manager.py
import os
import json
import hashlib
from datetime import datetime
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload, MediaFileUpload

class SyncManager:
    def __init__(self, credentials_path='credentials/gdrive_credentials.json'):
        self.creds = service_account.Credentials.from_service_account_file(
            credentials_path,
            scopes=['https://www.googleapis.com/auth/drive']
        )
        self.service = build('drive', 'v3', credentials=self.creds)
        self.admin_email = "rubensousa106@gmail.com"
        self.scraper_email = "folhetosmart-scraper@folhetosmart.iam.gserviceaccount.com"

    def get_file_hash(self, file_path):
        """Calcula o hash do ficheiro para verificar duplicados"""
        with open(file_path, 'rb') as f:
            return hashlib.md5(f.read()).hexdigest()

    def is_file_processed(self, file_hash, supermercado):
        """Verifica se o ficheiro já foi processado (usando um ficheiro de controlo)"""
        control_file = f"processed_{supermercado}.json"
        if os.path.exists(control_file):
            with open(control_file, 'r') as f:
                processed = json.load(f)
                return file_hash in processed.get('hashes', [])
        return False

    def mark_as_processed(self, file_hash, supermercado, nome_original):
        """Marca o ficheiro como processado"""
        control_file = f"processed_{supermercado}.json"
        data = {}
        if os.path.exists(control_file):
            with open(control_file, 'r') as f:
                data = json.load(f)

        if 'hashes' not in data:
            data['hashes'] = []
        if 'files' not in data:
            data['files'] = []

        if file_hash not in data['hashes']:
            data['hashes'].append(file_hash)
            data['files'].append({
                'hash': file_hash,
                'nome': nome_original,
                'data': datetime.now().isoformat()
            })

        with open(control_file, 'w') as f:
            json.dump(data, f, indent=2)

    def list_flyers_in_drive(self, folder_id):
        """Lista os folhetos no Google Drive"""
        query = f"'{folder_id}' in parents and mimeType='application/pdf'"
        results = self.service.files().list(
            q=query,
            fields="files(id, name, createdTime, modifiedTime)"
        ).execute()
        return results.get('files', [])

    def download_flyer(self, file_id, file_name):
        """Descarrega um folheto do Drive"""
        request = self.service.files().get_media(fileId=file_id)
        file_path = os.path.join('/tmp', file_name)

        with open(file_path, 'wb') as f:
            downloader = MediaIoBaseDownload(f, request)
            done = False
            while not done:
                status, done = downloader.next_chunk()
        return file_path

    def upload_flyer(self, file_path, folder_id, user_email):
        """Upload de um folheto (apenas ADMIN)"""
        # Verifica se o utilizador é ADMIN
        if user_email != self.admin_email:
            raise PermissionError("Apenas o ADMIN pode fazer upload!")

        file_name = os.path.basename(file_path)
        media = MediaFileUpload(file_path, mimetype='application/pdf')

        file_metadata = {
            'name': file_name,
            'parents': [folder_id]
        }

        file = self.service.files().create(
            body=file_metadata,
            media_body=media,
            fields='id'
        ).execute()

        return file.get('id')

    def sync_all_flyers(self, folder_id, supermercado):
        """Sincroniza todos os folhetos de um supermercado"""
        flyers = self.list_flyers_in_drive(folder_id)
        results = []

        for flyer in flyers:
            # Verifica se já foi processado
            # (usamos o nome + data como identificador)
            file_hash = flyer.get('modifiedTime', '') + flyer['name']
            if self.is_file_processed(file_hash, supermercado):
                print(f"⏭️ {flyer['name']} já processado, ignorando")
                continue

            print(f"📄 A processar: {flyer['name']}")
            try:
                # Descarrega o PDF
                pdf_path = self.download_flyer(flyer['id'], flyer['name'])

                # Extrai produtos (usando o seu PDFExtractor)
                from pdf_extractor import extract_products_from_pdf
                produtos = extract_products_from_pdf(pdf_path, supermercado)

                if produtos:
                    # Guarda JSON com nome dinâmico
                    data_atual = datetime.now().strftime("%d%m%y")
                    json_name = f"{supermercado}_{data_atual}.json"
                    with open(json_name, 'w', encoding='utf-8') as f:
                        json.dump(produtos, f, indent=2, ensure_ascii=False)

                    # Marca como processado
                    self.mark_as_processed(file_hash, supermercado, flyer['name'])
                    results.append({
                        'file': flyer['name'],
                        'status': 'success',
                        'produtos': len(produtos),
                        'json': json_name
                    })
                else:
                    results.append({
                        'file': flyer['name'],
                        'status': 'error',
                        'message': 'Nenhum produto encontrado'
                    })

            except Exception as e:
                results.append({
                    'file': flyer['name'],
                    'status': 'error',
                    'message': str(e)
                })

        return results
