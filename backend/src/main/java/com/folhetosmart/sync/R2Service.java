package com.folhetosmart.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Configuração do Cloudflare R2 + assinatura de uploads. As credenciais vêm do
 * ambiente (NÃO ficam no código). Se não estiverem definidas, {@link #isConfigured()}
 * devolve false e o upload in-app fica indisponível (usa-se o dashboard do R2).
 */
@Service
public class R2Service {

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;

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

    /** URL assinado (10 min) para a app fazer PUT do PDF do folheto no R2. */
    public String presignFlyerPut(String filename) {
        return R2Presigner.presignPut(endpoint, bucket, filename, accessKey, secretKey, 600);
    }
}
