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

    /** Resposta do worker a /process-pdf (contrato entre serviços). */
    public record ProcessPdfResponse(
            @JsonProperty("accepted") boolean accepted,
            @JsonProperty("sync_run_id") String syncRunId,
            @JsonProperty("drive_file_id") String driveFileId
    ) {
    }

    /**
     * Upload de folheto pelo ADMIN: além do OCR/matcher, o worker guarda o PDF
     * no Google Drive com {@code driveFilename} (substitui se já existir) e
     * devolve o id do ficheiro no Drive.
     */
    public String uploadFlyer(UUID syncRunId, String slug, byte[] pdfBytes, String driveFilename) {
        ByteArrayResource pdf = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return driveFilename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", pdf);
        body.add("supermarket_slug", slug);
        body.add("sync_run_id", syncRunId.toString());
        body.add("drive_filename", driveFilename);

        try {
            ProcessPdfResponse resp = restClient.post()
                    .uri("/process-pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ProcessPdfResponse.class);
            log.info("Folheto enviado ao worker (sync_run={}, slug={})", syncRunId, slug);
            return resp == null ? null : resp.driveFileId();
        } catch (Exception ex) {
            log.error("Falha ao enviar o folheto ao worker", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar o folheto ao automatizador. Tenta novamente.");
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
