package com.folhetosmart.sync;

import com.folhetosmart.common.ApiException;
import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.prices.WeeklyPriceRepository;
import com.folhetosmart.sync.dto.SyncRunDto;
import com.folhetosmart.sync.dto.SyncStatusResponse;
import com.folhetosmart.sync.dto.SyncStatusResponse.LastSync;
import com.folhetosmart.sync.dto.SyncStatusResponse.SupermarketStatus;
import com.folhetosmart.sync.dto.SyncTriggerResponse;
import com.folhetosmart.sync.dto.SyncUploadResponse;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class SyncService {

    private final SupermarketRepository supermarketRepository;
    private final SyncRunRepository syncRunRepository;
    private final WeeklyPriceRepository priceRepository;
    private final ScraperClient scraperClient;
    private final Clock clock;

    public SyncService(SupermarketRepository supermarketRepository,
                       SyncRunRepository syncRunRepository,
                       WeeklyPriceRepository priceRepository,
                       ScraperClient scraperClient,
                       Clock clock) {
        this.supermarketRepository = supermarketRepository;
        this.syncRunRepository = syncRunRepository;
        this.priceRepository = priceRepository;
        this.scraperClient = scraperClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SyncStatusResponse getStatus() {
        List<Supermarket> markets = supermarketRepository.findAllByOrderByIdAsc();
        int total = markets.size();
        int ready = (int) markets.stream().filter(Supermarket::isFlyerAvailable).count();

        List<SupermarketStatus> statuses = markets.stream()
                .map(s -> new SupermarketStatus(
                        s.getName(), s.getSlug(),
                        s.isFlyerAvailable(), s.getFlyerAvailableSince(),
                        s.getSyncStatus(), s.getProductsImported(),
                        s.getSyncedAt(), s.getErrorMessage(), s.getSyncSource()))
                .toList();

        LastSync lastSync = syncRunRepository
                .findTopByStatusOrderByFinishedAtDesc("done")
                .map(this::buildLastSync)
                .orElse(null);

        return new SyncStatusResponse(
                statuses, total > 0 && ready == total, ready, total, lastSync);
    }

    private LastSync buildLastSync(SyncRun run) {
        LocalDate today = LocalDate.now(clock);
        long promos = priceRepository.countCurrentPromotions(today);
        Double avg = priceRepository.averageCurrentSavingsPct(today);
        int matched = run.getProductsMatched() > 0
                ? run.getProductsMatched()
                : (int) priceRepository.countDistinctProductsWithCurrentPrice(today);
        return new LastSync(run.getFinishedAt(), matched, promos, round1(avg));
    }

    /**
     * Dispara o processamento centralizado (scraping/Drive + Claude). Só ADMIN
     * e cron — corre 1× por semana no servidor. Com o fallback do Drive já não
     * exige que todos os folhetos estejam disponíveis: tenta todos os
     * supermercados (o worker resolve site -> Drive por cada).
     */
    @Transactional
    public SyncTriggerResponse trigger(String triggeredBy) {
        List<Supermarket> markets = supermarketRepository.findAllByOrderByIdAsc();
        int total = markets.size();
        if (total == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Sem supermercados configurados.");
        }

        // Marca todos como "running" para a app refletir o estado de transição.
        markets.forEach(s -> s.setSyncStatus("running"));
        supermarketRepository.saveAll(markets);

        SyncRun run = new SyncRun();
        run.setTriggeredBy(triggeredBy);
        run.setStatus("pending");
        run.setSupermarketsReady(total);
        run.setSupermarketsTotal(total);
        run = syncRunRepository.save(run);

        try {
            scraperClient.triggerRun(run.getId(),
                    markets.stream().map(Supermarket::getSlug).toList());
        } catch (ApiException ex) {
            run.setStatus("error");
            run.setErrorMessage(ex.getMessage());
            syncRunRepository.save(run);
            throw ex;
        }

        run.setStatus("running");
        syncRunRepository.save(run);
        return new SyncTriggerResponse(run.getId(), run.getStatus(), total, total);
    }

    /** Progresso de uma sincronização (polling da app). */
    @Transactional(readOnly = true)
    public SyncRunDto getRun(UUID id) {
        return syncRunRepository.findById(id)
                .map(SyncRunDto::from)
                .orElseThrow(() -> new NotFoundException("Sincronização não encontrada: " + id));
    }

    /**
     * Upload manual de um folheto em PDF (Fix 3): valida, guarda em
     * /tmp/uploads e reenvia ao worker para OCR + AI matching.
     */
    @Transactional
    public SyncUploadResponse uploadPdf(String slug, MultipartFile file) {
        supermarketRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Supermercado não encontrado: " + slug));

        byte[] bytes = readBytes(file);
        validatePdf(file, bytes);

        // Guarda uma cópia local (auditoria/diagnóstico).
        Path saved = savePdf(slug, bytes);

        SyncRun run = new SyncRun();
        run.setTriggeredBy("upload");
        run.setStatus("running");
        run.setSupermarketsReady(1);
        run.setSupermarketsTotal(1);
        run = syncRunRepository.save(run);

        try {
            scraperClient.processPdf(run.getId(), slug, bytes, saved.getFileName().toString());
        } catch (ApiException ex) {
            run.setStatus("error");
            run.setErrorMessage(ex.getMessage());
            syncRunRepository.save(run);
            throw ex;
        }
        return new SyncUploadResponse(run.getId(), "processing");
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nenhum ficheiro recebido.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível ler o ficheiro.");
        }
    }

    /** Valida content-type + magic bytes (%PDF). */
    private static void validatePdf(MultipartFile file, byte[] bytes) {
        String contentType = file.getContentType();
        boolean typeOk = contentType == null
                || contentType.contains("pdf")
                || contentType.equals("application/octet-stream");
        boolean magicOk = bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (!typeOk || !magicOk) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "O ficheiro tem de ser um PDF válido.");
        }
    }

    private static Path savePdf(String slug, byte[] bytes) {
        try {
            // /tmp/uploads no container Linux (java.io.tmpdir); portátil em dev.
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "uploads");
            Files.createDirectories(dir);
            Path target = dir.resolve(slug + "_" + System.currentTimeMillis() + ".pdf");
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível guardar o PDF.");
        }
    }

    /**
     * Marca a disponibilidade de um folheto. Em produção é chamado pelo
     * verificador periódico (ou pelo worker ao detetar o folheto publicado).
     */
    @Transactional
    public void setFlyerAvailability(String slug, boolean available) {
        Supermarket s = supermarketRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Supermercado não encontrado: " + slug));
        s.setFlyerAvailable(available);
        s.setFlyerAvailableSince(available ? Instant.now(clock) : null);
        supermarketRepository.save(s);
    }

    private static Double round1(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
}
