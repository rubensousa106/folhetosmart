package com.folhetosmart.sync;

import com.folhetosmart.common.ApiException;
import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.products.LatestProducts;
import com.folhetosmart.products.LatestProductsRepository;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse.FlyerStatus;
import com.folhetosmart.sync.dto.AdminProcessFlyerResponse;
import com.folhetosmart.sync.dto.AdminUploadResponse;
import com.folhetosmart.sync.dto.AdminUploadToDriveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Pattern;

/** Operações do painel de administração: upload de folhetos e estado da semana. */
@Service
public class AdminService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern DATE_RE = Pattern.compile("\\d{2}-\\d{2}-\\d{4}");

    private final SupermarketRepository supermarketRepository;
    private final SyncRunRepository syncRunRepository;
    private final ScraperClient scraperClient;
    private final LatestProductsRepository latestProductsRepository;
    private final Clock clock;

    public AdminService(SupermarketRepository supermarketRepository,
                        SyncRunRepository syncRunRepository,
                        ScraperClient scraperClient,
                        LatestProductsRepository latestProductsRepository,
                        Clock clock) {
        this.supermarketRepository = supermarketRepository;
        this.syncRunRepository = syncRunRepository;
        this.scraperClient = scraperClient;
        this.latestProductsRepository = latestProductsRepository;
        this.clock = clock;
    }

    /**
     * Guarda/atualiza o folheto mais recente de um supermercado (modelo simples
     * scraper -> JSON -> backend). O `payload` é o JSON tal e qual o produtor o
     * enviou ({@code {"supermercado","produtos":[...]}}) — a app lê-o intacto.
     */
    @Transactional
    public void saveLatestProducts(String supermarket, int count, String flyer, String payload) {
        String key = supermarket.trim().toLowerCase();
        LatestProducts lp = latestProductsRepository.findById(key).orElseGet(LatestProducts::new);
        lp.setSupermarket(key);
        lp.setPayload(payload);
        lp.setProductsCount(count);
        if (flyer != null && !flyer.isBlank()) {
            lp.setSourceFlyer(flyer.trim());
        }
        lp.setUpdatedAt(Instant.now());
        latestProductsRepository.save(lp);
    }

    /**
     * Nome do folheto já analisado deste supermercado (ou "" se nunca). O produtor
     * lê isto ANTES de gastar IA: se o folheto for o mesmo, não reextrai.
     */
    @Transactional(readOnly = true)
    public String currentSourceFlyer(String supermarket) {
        return latestProductsRepository.findById(supermarket.trim().toLowerCase())
                .map(LatestProducts::getSourceFlyer)
                .orElse("");
    }

    /**
     * Upload de um folheto PDF: valida, constrói o nome
     * "{Supermercado} {DD-MM-YYYY} - {DD-MM-YYYY}.pdf", envia ao worker (que o
     * guarda no Google Drive sem duplicar e corre a extração com IA) e devolve
     * o id no Drive + o sync_run para acompanhamento.
     */
    @Transactional
    public AdminUploadResponse uploadFlyer(String slug, String validFrom,
                                           String validUntil, MultipartFile file) {
        Supermarket market = supermarketRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Supermercado não encontrado: " + slug));
        validateDate(validFrom);
        validateDate(validUntil);

        byte[] bytes = SyncService.readBytes(file);
        SyncService.validatePdf(file, bytes);

        String filename = market.getName() + " " + validFrom + " - " + validUntil + ".pdf";

        SyncRun run = new SyncRun();
        run.setTriggeredBy("admin-upload");
        run.setStatus("running");
        run.setSupermarketsReady(1);
        run.setSupermarketsTotal(1);
        run = syncRunRepository.save(run);

        market.setSyncStatus("running");
        supermarketRepository.save(market);

        String driveFileId;
        try {
            driveFileId = scraperClient.uploadFlyer(run.getId(), slug, bytes, filename);
        } catch (ApiException ex) {
            run.setStatus("error");
            run.setErrorMessage(ex.getMessage());
            syncRunRepository.save(run);
            throw ex;
        }
        return new AdminUploadResponse(run.getId(), filename, driveFileId, "processing");
    }

    /**
     * Pipeline novo, passo 1: guarda o PDF no Google Drive (em memória, sem
     * disco) com o nome "{Supermercado} {DD-MM-YYYY} - {DD-MM-YYYY}.pdf" e
     * devolve o id do ficheiro. NÃO processa nada ainda.
     */
    public AdminUploadToDriveResponse uploadToDrive(String slug, String validFrom,
                                                    String validUntil, MultipartFile file) {
        Supermarket market = supermarketRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Supermercado não encontrado: " + slug));
        validateDate(validFrom);
        validateDate(validUntil);

        byte[] bytes = SyncService.readBytes(file);
        SyncService.validatePdf(file, bytes);

        String filename = market.getName() + " " + validFrom + " - " + validUntil + ".pdf";
        String driveFileId = scraperClient.uploadToDrive(bytes, filename);
        if (driveFileId == null || driveFileId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível guardar o folheto no Google Drive.");
        }
        return new AdminUploadToDriveResponse(driveFileId, filename);
    }

    /**
     * Pipeline novo, passo 2: o worker descarrega o PDF do Drive (memória),
     * corre a Claude API e persiste. SÍNCRONO — bloqueia ~1-2 min. NÃO é
     * {@code @Transactional} e NÃO cria sync_run de propósito: o worker (chamada
     * síncrona) é o dono do estado do supermercado, e uma transação aqui
     * fecharia por cima do resultado dele.
     */
    public AdminProcessFlyerResponse processFlyer(String slug, String validFrom,
                                                  String validUntil, String driveFileId) {
        supermarketRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Supermercado não encontrado: " + slug));
        validateDate(validFrom);
        validateDate(validUntil);
        if (driveFileId == null || driveFileId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "drive_file_id em falta.");
        }

        ScraperClient.ProcessFlyerResult result =
                scraperClient.processFlyer(driveFileId, slug, validFrom, validUntil, null);
        return new AdminProcessFlyerResponse(result.productsImported(), result.status());
    }

    /** Estado dos folhetos da semana atual (segunda a domingo). */
    @Transactional(readOnly = true)
    public AdminFlyersStatusResponse flyersStatus() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        String range = monday.format(DATE) + " - " + sunday.format(DATE);

        List<FlyerStatus> markets = supermarketRepository.findAllByOrderByIdAsc().stream()
                .map(s -> {
                    boolean hasFlyer = s.getProductsImported() > 0 || s.isFlyerAvailable();
                    String driveFilename = hasFlyer
                            ? s.getName() + " " + range + ".pdf"
                            : null;
                    return new FlyerStatus(
                            s.getName(), s.getSlug(), hasFlyer,
                            driveFilename, s.getProductsImported(), s.getSyncedAt());
                })
                .toList();

        return new AdminFlyersStatusResponse(range, markets);
    }

    private static void validateDate(String value) {
        if (value == null || !DATE_RE.matcher(value).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Data inválida (usa o formato DD-MM-AAAA): " + value);
        }
    }
}
