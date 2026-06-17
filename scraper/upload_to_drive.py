# upload_to_drive.py - VERSÃO COM OAUTH (SUBSTITUIR)
import os
import pickle
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from dotenv import load_dotenv

load_dotenv()

SCOPES = ['https://www.googleapis.com/auth/drive.file']

def autenticar_utilizador():
    """Autentica usando OAuth 2.0 com a sua conta pessoal"""
    creds = None

    # Verifica se já existe um token guardado
    if os.path.exists('token.pickle'):
        with open('token.pickle', 'rb') as token:
            creds = pickle.load(token)

    # Se não houver credenciais válidas, faz login
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            # Usa o ficheiro de credenciais OAuth
            flow = InstalledAppFlow.from_client_secrets_file(
                'credentials/oauth_credentials.json', SCOPES)
            creds = flow.run_local_server(port=0)

        # Guarda o token para a próxima vez
        with open('token.pickle', 'wb') as token:
            pickle.dump(creds, token)

    return creds

class DriveUploader:
    def __init__(self):
        self.creds = autenticar_utilizador()
        self.service = build('drive', 'v3', credentials=self.creds)
        self.folder_id = os.getenv("GOOGLE_DRIVE_FOLDER_ID")

        if not self.folder_id:
            raise ValueError("❌ GOOGLE_DRIVE_FOLDER_ID não definido!")

    def upload_json_to_drive(self, json_path, supermarket):
        """Faz upload do JSON para o Google Drive"""
        if not os.path.exists(json_path):
            return {"error": f"Ficheiro não encontrado: {json_path}"}

        file_name = os.path.basename(json_path)
        file_metadata = {'name': file_name, 'parents': [self.folder_id]}

        file_metadata = {
            'name': file_name,
            'parents': [self.folder_id]
        }

        media = MediaFileUpload(
            json_path,
            mimetype='application/json',
            resumable=True
        )

        try:
            file = self.service.files().create(
                body=file_metadata,
                media_body=media,
                fields='id, name, webViewLink'
            ).execute()

            print(f"✅ JSON guardado no Drive: {file.get('name')}")
            print(f"   🔗 Link: {file.get('webViewLink')}")

            return {
                "success": True,
                "file_id": file.get('id'),
                "file_name": file.get('name'),
                "link": file.get('webViewLink')
            }

        except Exception as e:
            print(f"❌ Erro: {e}")
            return {"error": str(e)}

    def list_uploaded_jsons(self):
        """Lista os JSONs guardados"""
        query = f"'{self.folder_id}' in parents and mimeType='application/json'"
        results = self.service.files().list(
            q=query,
            fields="files(id, name, createdTime, modifiedTime)"
        ).execute()
        return results.get('files', [])


def upload_json(json_path, supermarket):
    """Função helper para fazer upload"""
    uploader = DriveUploader()
    return uploader.upload_json_to_drive(json_path, supermarket)


if __name__ == "__main__":
    print("🧪 TESTE DE UPLOAD COM OAUTH")
    print("=" * 60)

    uploader = DriveUploader()
    print("✅ Autenticado com sucesso!")

    # Lista os JSONs já guardados
    jsons = uploader.list_uploaded_jsons()
    if jsons:
        print(f"\n📂 JSONs encontrados no Drive ({len(jsons)}):")
        for f in jsons[:5]:
            print(f"   - {f.get('name')}")
    else:
        print("\n📂 Nenhum JSON encontrado no Drive")

    # Procura um JSON local para upload
    json_files = [f for f in os.listdir('.') if f.endswith('.json')]
    if json_files:
        print(f"\n📄 Encontrado JSON local: {json_files[0]}")
        resultado = uploader.upload_json_to_drive(json_files[0], "Continente")
        if resultado.get('success'):
            print("✅ Upload concluído!")
        else:
            print(f"❌ Erro: {resultado.get('error')}")
    else:
        print("\n📄 Nenhum JSON local encontrado.")
        print("   Execute primeiro: python test_pdf.py")
