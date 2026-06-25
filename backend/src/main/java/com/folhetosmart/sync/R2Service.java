package com.folhetosmart.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço único para interação com o Cloudflare R2.
 * Centraliza: listagem de ficheiros, geração de URLs assinados e uploads.
 */
@Service
public class R2Service {

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private S3Client s3Client;

    public R2Service(
            @Value("${R2_ENDPOINT:}") String endpoint,
            @Value("${R2_BUCKET:folhetosmart}") String bucket,
            @Value("${R2_ACCESS_KEY_ID:}") String accessKey,
            @Value("${R2_SECRET_ACCESS_KEY:}") String secretKey) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    private S3Client getS3Client() {
        if (s3Client == null) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.of("auto"))
                    .build();
        }
        return s3Client;
    }

    public boolean isConfigured() {
        return !endpoint.isBlank() && !bucket.isBlank() && !accessKey.isBlank() && !secretKey.isBlank();
    }

    /**
     * Lista os ficheiros no R2 que correspondem a um padrão.
     * @param pattern Padrão para filtrar (ex: "produtos_*.json")
     * @return Lista de URLs assinados dos ficheiros
     */
    public List<String> listFiles(String pattern) {
        if (!isConfigured()) return new ArrayList<>();

        S3Client s3 = getS3Client();
        List<String> fileUrls = new ArrayList<>();
        String prefix = pattern.replace("*", "");

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        ListObjectsV2Response response = s3.listObjectsV2(request);

        for (S3Object object : response.contents()) {
            String key = object.key();
            if (key.endsWith(".json")) {
                fileUrls.add(generatePresignedUrl(key));
            }
        }
        return fileUrls;
    }

    /** Gera um URL assinado para download (válido por 7 dias).**/
    public String generatePresignedUrl(String key) {
        S3Client s3 = getS3Client();
        return s3.utilities().getPresignedUrl(builder -> builder
                .bucket(bucket)
                .key(key)
                .signatureDuration(Duration.ofDays(7))
        ).toString();
    }

    /** Gera um URL assinado para upload (válido por 10 minutos) **/
    public String presignFlyerPut(String filename) {
        // Reutiliza o mesmo gerador de URLs, mas com tempo mais curto
        S3Client s3 = getS3Client();
        return s3.utilities().getPresignedUrl(builder -> builder
                .bucket(bucket)
                .key(filename)
                .signatureDuration(Duration.ofMinutes(10))
        ).toString();
    }

    /** Faz upload de bytes para o R2 **/
    public void uploadBytes(byte[] content, String key) {
        if (!isConfigured()) {
            throw new IllegalStateException("R2 não configurado");
        }
        S3Client s3 = getS3Client();
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build(), RequestBody.fromBytes(content));
    }

    // ============================================================
    // 4. GETTERS (para compatibilidade)
    // ============================================================
    public String getBucketName() { return bucket; }
    public String getEndpointUrl() { return endpoint; }
}
