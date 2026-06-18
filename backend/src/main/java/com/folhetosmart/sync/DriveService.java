package com.folhetosmart.sync;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class DriveService {

    @Value("${GOOGLE_DRIVE_CREDENTIALS_PATH:credentials/gdrive_credentials.json}")
    private String credentialsPath;

    @Value("${GOOGLE_DRIVE_FOLDER_ID:1SiJOZVTNxcfk4x6GEFoCtc7CS_sJVpDf}")
    private String folderId;

    private String accessToken;

    private String getAccessToken() throws IOException {
        if (accessToken == null) {
            try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                        .createScoped(java.util.Arrays.asList("https://www.googleapis.com/auth/drive.readonly"));
                credentials.refreshIfExpired();
                accessToken = credentials.getAccessToken().getTokenValue();
            }
        }
        return accessToken;
    }

    /**
     * Obtém o JSON mais recente de um supermercado do Google Drive.
     */
    public String getLatestJson(String supermarket) {
        try {
            // 1. Lista os ficheiros na pasta
            String listUrl = String.format(
                    "https://www.googleapis.com/drive/v3/files?q='%s' in parents and mimeType='application/json' and name contains '%s'&orderBy=modifiedTime desc&pageSize=1&fields=files(id,name)",
                    folderId, supermarket
            );

            String listResponse = sendGetRequest(listUrl);
            System.out.println("📄 Lista de ficheiros: " + listResponse);

            // 2. Extrai o ID do ficheiro
            String fileId = extractFileId(listResponse);
            if (fileId == null) {
                System.out.println("⚠️ Nenhum JSON encontrado para: " + supermarket);
                return null;
            }

            // 3. Descarrega o ficheiro
            String downloadUrl = String.format(
                    "https://www.googleapis.com/drive/v3/files/%s?alt=media",
                    fileId
            );

            String json = sendGetRequest(downloadUrl);
            System.out.println("✅ JSON lido com sucesso! Tamanho: " + json.length() + " caracteres");
            return json;

        } catch (Exception e) {
            System.err.println("❌ Erro ao ler JSON do Drive: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + getAccessToken());

        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP error: " + conn.getResponseCode() + " - " + conn.getResponseMessage());
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             java.io.InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private String extractFileId(String jsonResponse) {
        // Procura por "id":"..." no JSON
        int start = jsonResponse.indexOf("\"id\":\"");
        if (start == -1) return null;
        start += 6;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
