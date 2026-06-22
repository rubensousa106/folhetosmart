package com.folhetosmart.sync;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TreeMap;

/**
 * Gera URLs assinados (AWS SigV4) para PUT no Cloudflare R2 (S3-compatível),
 * SEM SDK (evita dependências pesadas no build offline). A app usa o URL para
 * fazer upload do PDF diretamente para o R2 — o ficheiro não passa pelo Render.
 */
public final class R2Presigner {

    private R2Presigner() {
    }

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * URL assinado para PUT de um objeto no R2.
     *
     * @param endpoint       https://&lt;accountid&gt;.r2.cloudflarestorage.com (sem bucket)
     * @param bucket         nome do bucket
     * @param key            nome do objeto (ex.: "Continente 16-06-2026 - 22-06-2026.pdf")
     * @param expiresSeconds validade do link
     */
    public static String presignPut(String endpoint, String bucket, String key,
                                    String accessKey, String secretKey, int expiresSeconds) {
        try {
            String host = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
            String region = "auto";
            String service = "s3";

            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(AMZ_DATE);
            String dateStamp = now.format(DATE_STAMP);
            String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";

            // path-style: /{bucket}/{key} (mantém as barras do caminho)
            String canonicalUri = "/" + uriEncode(bucket, false) + "/" + uriEncode(key, false);

            // Parâmetros de query, ordenados pela chave (TreeMap), valores codificados.
            TreeMap<String, String> params = new TreeMap<>();
            params.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
            params.put("X-Amz-Credential", accessKey + "/" + credentialScope);
            params.put("X-Amz-Date", amzDate);
            params.put("X-Amz-Expires", String.valueOf(expiresSeconds));
            params.put("X-Amz-SignedHeaders", "host");

            StringBuilder cq = new StringBuilder();
            for (var e : params.entrySet()) {
                if (cq.length() > 0) {
                    cq.append('&');
                }
                cq.append(uriEncode(e.getKey(), true)).append('=').append(uriEncode(e.getValue(), true));
            }
            String canonicalQuery = cq.toString();

            String canonicalHeaders = "host:" + host + "\n";
            String signedHeaders = "host";
            String payloadHash = "UNSIGNED-PAYLOAD";

            String canonicalRequest = "PUT\n" + canonicalUri + "\n" + canonicalQuery + "\n"
                    + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n"
                    + hex(sha256(canonicalRequest));

            byte[] signingKey = signatureKey(secretKey, dateStamp, region, service);
            String signature = hex(hmacSha256(signingKey, stringToSign));

            return "https://" + host + canonicalUri + "?" + canonicalQuery
                    + "&X-Amz-Signature=" + signature;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao assinar URL R2: " + e.getMessage(), e);
        }
    }

    /** Codificação URI RFC 3986. encodeSlash=false mantém '/' (para o caminho). */
    private static String uriEncode(String input, boolean encodeSlash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            char ch = (char) c;
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch);
            } else if (ch == '/' && !encodeSlash) {
                sb.append('/');
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] signatureKey(String secret, String dateStamp, String region, String service)
            throws Exception {
        byte[] kSecret = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
