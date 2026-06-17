# scraper/utils/processed_tracker.py
import os
import json
from datetime import datetime

class ProcessedTracker:
    """
    Controla quais ficheiros já foram processados.
    Usa um ficheiro JSON local para guardar o estado.
    """

    def __init__(self, tracker_file='processed_files.json'):
        self.tracker_file = tracker_file
        self.data = self._load()

    def _load(self):
        """Carrega o ficheiro de controlo"""
        if os.path.exists(self.tracker_file):
            try:
                with open(self.tracker_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except:
                return {"processed": []}
        return {"processed": []}

    def _save(self):
        """Guarda o ficheiro de controlo"""
        with open(self.tracker_file, 'w', encoding='utf-8') as f:
            json.dump(self.data, f, indent=2, ensure_ascii=False)

    def is_processed(self, file_id, file_name):
        """
        Verifica se um ficheiro já foi processado.

        Args:
            file_id: ID do ficheiro no Google Drive
            file_name: Nome do ficheiro

        Returns:
            bool: True se já foi processado
        """
        for item in self.data["processed"]:
            if item.get("file_id") == file_id:
                return True
            if item.get("file_name") == file_name:
                # Verifica se foi processado há menos de 7 dias
                processed_date = item.get("processed_date")
                if processed_date:
                    try:
                        date_obj = datetime.fromisoformat(processed_date)
                        if (datetime.now() - date_obj).days > 7:
                            return False
                    except:
                        pass
                return True
        return False

    def mark_as_processed(self, file_id, file_name, json_file=None, produtos=None):
        """
        Marca um ficheiro como processado.
        """
        # Remove entradas antigas (para evitar duplicados)
        self.data["processed"] = [
            item for item in self.data["processed"]
            if item.get("file_id") != file_id
        ]

        # Adiciona nova entrada
        self.data["processed"].append({
            "file_id": file_id,
            "file_name": file_name,
            "processed_date": datetime.now().isoformat(),
            "json_file": json_file,
            "total_produtos": produtos
        })

        self._save()
        print(f"✅ Marcado como processado: {file_name}")

    def get_processed_files(self):
        """Retorna a lista de ficheiros processados"""
        return self.data["processed"]

    def reset(self):
        """Reseta o controlo (apenas para ADMIN)"""
        self.data = {"processed": []}
        self._save()
        print("🔄 Controlo de processamento resetado")
