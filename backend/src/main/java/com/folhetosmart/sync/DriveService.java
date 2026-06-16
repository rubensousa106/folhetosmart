package com.folhetosmart.sync;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class DriveService {

    @Value("${google.drive.credentials.path:credentials/gdrive_credentials.json}")
    private String credentialsPath;

    @Value("${google.drive.folder.id:1SiJOZVTNxcfk4x6GEFoCtc7CS_sJVpDf}")
    private String folderId;

    private Drive driveService;

    private Drive getDriveService() throws GeneralSecurityException, IOException {
        if (driveService == null) {
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleCredentials credentials;
            try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(serviceAccountStream)
                        .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));
            }

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    jsonFactory,
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("FolhetoSmart")
                    .build();
        }
        return driveService;
    }

    public String uploadFile(String localFilePath, String folderId) throws GeneralSecurityException, IOException {
        Drive drive = getDriveService();

        File file = new File(localFilePath);
        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File()
                .setName(file.getName())
                .setParents(Collections.singletonList(folderId));

        FileContent mediaContent = new FileContent("application/pdf", file);

        com.google.api.services.drive.model.File uploadedFile = drive.files()
                .create(fileMetadata, mediaContent)
                .setFields("id, name")
                .execute();

        return uploadedFile.getId();
    }

    public String uploadFile(String localFilePath) throws GeneralSecurityException, IOException {
        return uploadFile(localFilePath, folderId);
    }
}
