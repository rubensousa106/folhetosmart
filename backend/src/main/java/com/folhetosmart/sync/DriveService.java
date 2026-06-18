package com.folhetosmart.sync;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
public class DriveService {

    @Value("${GOOGLE_DRIVE_CREDENTIALS_PATH:credentials/gdrive_credentials.json}")
    private String credentialsPath;

    @Value("${{GOOGLE_DRIVE_FOLDER_ID:1SiJOZVTNxcfk4x6GEFoCtc7CS_sJVpDf}")
    private String folderId;

    private Drive driveService;

    private Drive getDriveService() throws GeneralSecurityException, IOException {
        if (driveService == null) {
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleCredentials credentials;
            try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(serviceAccountStream)
                        .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));
            }

            driveService = new Drive.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                    .setApplicationName("FolhetoSmart")
                    .build();
        }
        return driveService;
    }

    /**
     * Obtém o JSON mais recente de um supermercado do Google Drive.
     *
     * @param supermarket Nome do supermercado (ex: "Continente")
     * @return Conteúdo do JSON como String, ou null se não existir
     */
    public String getLatestJson(String supermarket) {
        try {
            Drive drive = getDriveService();

            // Procura JSONs com o nome do supermercado
            String query = String.format(
                    "'%s' in parents and mimeType='application/json' and name contains '%s'",
                    folderId, supermarket
            );

            Drive.Files.List request = drive.files().list()
                    .setQ(query)
                    .setFields("files(id, name, createdTime, modifiedTime)")
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1);

            com.google.api.services.drive.model.FileList result = request.execute();
            List<com.google.api.services.drive.model.File> files = result.getFiles();

            if (files == null || files.isEmpty()) {
                System.out.println("⚠️ Nenhum JSON encontrado para: " + supermarket);
                return null;
            }

            // Pega o ficheiro mais recente
            com.google.api.services.drive.model.File file = files.get(0);
            String fileId = file.getId();

            System.out.println("📄 A ler JSON: " + file.getName() + " (ID: " + fileId + ")");

            // Lê o conteúdo do JSON
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                drive.files().get(fileId)
                        .executeMediaAndDownloadTo(outputStream);
                String json = outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("✅ JSON lido com sucesso! Tamanho: " + json.length() + " caracteres");
                return json;
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao ler JSON do Drive: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lista todos os JSONs disponíveis no Drive.
     */
    public List<com.google.api.services.drive.model.File> listAllJsons() {
        try {
            Drive drive = getDriveService();

            String query = String.format(
                    "'%s' in parents and mimeType='application/json'",
                    folderId
            );

            Drive.Files.List request = drive.files().list()
                    .setQ(query)
                    .setFields("files(id, name, createdTime, modifiedTime)")
                    .setOrderBy("modifiedTime desc");

            com.google.api.services.drive.model.FileList result = request.execute();
            return result.getFiles();

        } catch (Exception e) {
            System.err.println("❌ Erro ao listar JSONs: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
