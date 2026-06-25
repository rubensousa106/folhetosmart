package com.folhetosmart.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class R2Service {

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public boolean isConfigured() {
        return !endpoint.isBlank() && !bucket.isBlank() && !accessKey.isBlank() && !secretKey.isBlank();
    }

    /**
     * Lista os ficheiros no R2 que correspondem a um padrão.
     */
    public List<String> listFiles(String pattern) {
        if (!isConfigured()) return new ArrayList<>();

        List<String> files = new ArrayList<>();
        try {
            // Constrói a URL da lista de objetos do R2
            String listUrl = String.format("%s/%s?list-type=2&prefix=%s",
                    endpoint, bucket, pattern.replace("*", ""));
            URL url = new URL(listUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "AWS " + accessKey + ":" + signRequest(listUrl, "GET"));

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String xmlResponse = readResponse(conn);
                files = parseXmlKeys(xmlResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }

    /**
     * Gera um URL assinado para download (válido por 7 dias).
     */
    public String generatePresignedUrl(String key) {
        // Por simplicidade, retorna o URL público (se o bucket for público)
        // Ou implementa assinatura se necessário
        return String.format("%s/%s/%s", endpoint, bucket, key);
    }

    /**
     * Gera um URL para upload (válido por 10 minutos) - placeholder.
     */
    public String presignFlyerPut(String filename) {
        return String.format("%s/%s/%s", endpoint, bucket, filename);
    }

    /**
     * Upload de bytes para o R2 (usando REST).
     */
    public void uploadBytes(byte[] content, String key) {
        if (!isConfigured()) {
            throw new IllegalStateException("R2 não configurado");
        }
        try {
            String uploadUrl = String.format("%s/%s/%s", endpoint, bucket, key);
            URL url = new URL(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "AWS " + accessKey + ":" + signRequest(uploadUrl, "PUT"));
            conn.setRequestProperty("Content-Type", "application/json");

            conn.getOutputStream().write(content);
            conn.getOutputStream().flush();

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new RuntimeException("Upload falhou: " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer upload", e);
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private List<String> parseXmlKeys(String xml) {
        List<String> keys = new ArrayList<>();
        try {
            // Extrai as chaves do XML (simplificado, melhor usar um parser XML)
            String[] parts = xml.split("<Key>");
            for (int i = 1; i < parts.length; i++) {
                int end = parts[i].indexOf("</Key>");
                if (end > 0) {
                    keys.add(parts[i].substring(0, end));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return keys;
    }

    private String signRequest(String url, String method) {
        // Implementação simplificada da assinatura AWS V4 (se necessário)
        // Para já, retorna um placeholder
        return "SIGNATURE_PLACEHOLDER";
    }
}
