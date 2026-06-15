package com.folhetosmart.sync;

import com.folhetosmart.common.ApiException;
import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse.FlyerStatus;
import com.folhetosmart.sync.dto.AdminUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.DayOfWeek;
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
    private final Clock clock;

    public AdminService(SupermarketRepository supermarketRepository,
                        SyncRunRepository syncRunRepository,
                        ScraperClient scraperClient,
                        Clock clock) {
        this.supermarketRepository = supermarketRepository;
        this.syncRunRepository = syncRunRepository;
        this.scraperClient = scraperClient;
        this.clock = clock;
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
