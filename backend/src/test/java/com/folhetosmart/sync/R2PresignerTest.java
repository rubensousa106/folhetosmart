package com.folhetosmart.sync;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integração opcional: assina um PUT e fá-lo contra o R2 real. Só corre quando
 * as variáveis R2_* estão no ambiente (senão é ignorado, não trava o build).
 */
class R2PresignerTest {

    @Test
    void presignedPutReachesR2() throws Exception {
        String endpoint = System.getenv("R2_ENDPOINT");
        String bucket = System.getenv("R2_BUCKET");
        String access = System.getenv("R2_ACCESS_KEY_ID");
        String secret = System.getenv("R2_SECRET_ACCESS_KEY");
        assumeTrue(endpoint != null && bucket != null && access != null && secret != null,
                "R2_* não definidas — teste de integração ignorado");

        String url = R2Presigner.presignPut(endpoint, bucket, "test-r2-presign.txt", access, secret, 300);

        HttpResponse<String> resp = HttpClient.newBuilder().sslContext(trustAll()).build().send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofString("hello r2"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertTrue(resp.statusCode() == 200 || resp.statusCode() == 201,
                "R2 recusou o PUT assinado: HTTP " + resp.statusCode() + " — " + resp.body());
    }

    private static SSLContext trustAll() throws Exception {
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {
            }

            public void checkServerTrusted(X509Certificate[] c, String a) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        return ssl;
    }
}
