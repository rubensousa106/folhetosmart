package com.folhetosmart.sync;

import org.springframework.stereotype.Service;

@Service
public class DriveService {

    /**
     * Obtém o JSON mais recente de um supermercado do Google Drive.
     *
     * @param supermarket Nome do supermercado (ex: "Continente")
     * @return Conteúdo do JSON como String, ou null se não existir
     */
    public String getLatestJson(String supermarket) {
        // TODO: Implementar leitura do Google Drive
        // Por enquanto, retorna um JSON de exemplo ou null
        System.out.println("Buscando JSON mais recente para: " + supermarket);

        // Simulação - retorna um JSON de exemplo (remove isto depois)
        if ("Continente".equalsIgnoreCase(supermarket)) {
            return "{\"supermercado\":\"Continente\",\"produtos\":[{\"produto\":\"Bacalhau\",\"preco\":17.99}]}";
        }

        return null;
    }
}
