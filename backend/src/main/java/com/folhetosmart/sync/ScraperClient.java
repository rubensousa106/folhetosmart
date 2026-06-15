package com.folhetosmart.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.folhetosmart.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/** Cliente do worker Python que corre o pipeline de scraping + IA. */
@Component
public class ScraperClient {

    private static final Logger log = LoggerFactory.getLogger(ScraperClient.class);

    private final RestClient restClient;

    public ScraperClient(RestClient scraperRestClient) {
        this.restClient = scraperRestClient;
    }

    /**
     * Corpo de POST /run. Os nomes JSON são explícitos (@JsonProperty) porque
     * este é um contrato entre serviços — não pode depender da estratégia de
     * naming global do ObjectMapper.
     */
    public record RunRequest(
            @JsonProperty("supermarkets") List<String> supermarkets,
            @JsonProperty("sync_run_id") UUID syncRunId
    ) {
    }

    /** Dispara o pipeline. O worker responde 202 e processa em background. */
    public void triggerRun(UUID syncRunId, List<String> slugs) {
        try {
            restClient.post()
                    .uri("/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RunRequest(slugs, syncRunId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Pipeline disparado no worker (sync_run={})", syncRunId);
        } catch (Exception ex) {
            log.error("Falha ao contactar o worker do scraper", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível contactar o automatizador. Tenta novamente.");
        }
    }

    /** Reenvia um PDF carregado manualmente para o worker (OCR + matcher). */
    public void processPdf(UUID syncRunId, String slug, byte[] pdfBytes, String filename) {
        // O nome do ficheiro tem de vir do recurso para o multipart o incluir.
        ByteArrayResource pdf = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", pdf);
        body.add("supermarket_slug", slug);
        body.add("sync_run_id", syncRunId.toString());

        try {
            restClient.post()
                    .uri("/process-pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("PDF reenviado ao worker (sync_run={}, slug={})", syncRunId, slug);
        } catch (Exception ex) {
            log.error("Falha ao enviar o PDF ao worker", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar o PDF ao automatizador. Tenta novamente.");
        }
    }
}
