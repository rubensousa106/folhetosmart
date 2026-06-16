# test_claude_direct.py
import os
import requests
import json

# Carrega o .env
def load_env_file():
    env_path = os.path.join(os.path.dirname(__file__), '..', '.env')
    if os.path.exists(env_path):
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    os.environ[key.strip()] = value.strip()
        return True
    return False

load_env_file()

api_key = os.getenv("ANTHROPIC_API_KEY")
print(f"🔑 API Key: {'✅' if api_key else '❌'}")

# Testa com a API REST
url = "https://api.anthropic.com/v1/messages"

headers = {
    "x-api-key": api_key,
    "anthropic-version": "2023-06-01",
    "content-type": "application/json"
}

# Tenta com diferentes modelos
modelos = [
    "claude-3-5-haiku-20241022",
    "claude-3-haiku-20240307",
    "claude-3-sonnet-20240229",
]

for modelo in modelos:
    print(f"\n🧪 A testar: {modelo}")
    data = {
        "model": modelo,
        "max_tokens": 10,
        "messages": [{"role": "user", "content": "Diz OK"}]
    }

    response = requests.post(url, headers=headers, json=data)
    print(f"Status: {response.status_code}")

    if response.status_code == 200:
        print(f"✅ Funciona! Resposta: {response.json()['content'][0]['text']}")
        print(f"✅ Modelo disponível: {modelo}")
        break
    else:
        print(f"❌ Erro: {response.text[:200]}")
