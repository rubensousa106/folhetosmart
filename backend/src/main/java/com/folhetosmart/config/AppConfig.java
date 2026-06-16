package com.folhetosmart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Clock injetável (facilita testar lógica dependente da data). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Cliente HTTP para o worker Python do scraper.
     *
     * Dois detalhes importantes, ambos causaram bugs reais:
     * - Recebe o {@code RestClient.Builder} auto-configurado do Spring Boot
     *   (e não {@code RestClient.builder()} estático) para herdar o
     *   ObjectMapper da aplicação — com snake_case, como o worker espera.
     * - Usa SimpleClientHttpRequestFactory (HTTP/1.1): o JDK HttpClient por
     *   omissão tenta upgrade h2c sobre http://, que o uvicorn rejeita
     *   ("Unsupported upgrade request") e o corpo do POST perde-se (422).
     */
    @Bean
    public RestClient scraperRestClient(RestClient.Builder builder,
                                        @Value("${folheto.scraper.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return builder
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Cliente HTTP de longa duração para o pipeline do ADMIN
     * (upload-to-drive + process-flyer). A extração com IA é SÍNCRONA e pode
     * demorar 1-2 min, pelo que o read timeout tem de ser bem mais alto.
     */
    @Bean
    public RestClient scraperLongRestClient(RestClient.Builder builder,
                                            @Value("${folheto.scraper.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(180_000);   // 3 min — folga para a extração IA
        return builder
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }
}
