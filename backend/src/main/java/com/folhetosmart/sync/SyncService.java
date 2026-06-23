package com.folhetosmart.sync;

import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.prices.WeeklyPriceRepository;
import com.folhetosmart.sync.dto.SyncRunDto;
import com.folhetosmart.sync.dto.SyncStatusResponse;
import com.folhetosmart.sync.dto.SyncStatusResponse.LastSync;
import com.folhetosmart.sync.dto.SyncStatusResponse.SupermarketStatus;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura do estado dos folhetos/sincronização para a app. O processamento em si
 * (extração + publicação do feed) corre fora do backend — no produtor (R2) via
 * GitHub Actions/Cowork — pelo que aqui já não há "trigger" nem worker.
 */
@Service
public class SyncService {

    private final SupermarketRepository supermarketRepository;
    private final SyncRunRepository syncRunRepository;
    private final WeeklyPriceRepository priceRepository;
    private final Clock clock;

    public SyncService(SupermarketRepository supermarketRepository,
                       SyncRunRepository syncRunRepository,
                       WeeklyPriceRepository priceRepository,
                       Clock clock) {
        this.supermarketRepository = supermarketRepository;
        this.syncRunRepository = syncRunRepository;
        this.priceRepository = priceRepository;
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
                        s.getSyncedAt(), s.getErrorMessage(), s.getSyncSource(),
                        null))   // progressMessage: sem fonte por agora (reservado)
                .toList();

        LastSync lastSync = syncRunRepository
                .findTopByStatusOrderByFinishedAtDesc("done")
                .map(this::buildLastSync)
                .orElse(null);

        // Há dados reais desta semana? (controla "Ver promoções da semana" na app)
        LocalDate today = LocalDate.now(clock);
        int totalThisWeek = (int) priceRepository.countDistinctProductsWithCurrentPrice(today);

        return new SyncStatusResponse(
                statuses, total > 0 && ready == total, ready, total, lastSync,
                totalThisWeek > 0, totalThisWeek);
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

    /** Progresso de uma sincronização (polling da app). */
    @Transactional(readOnly = true)
    public SyncRunDto getRun(UUID id) {
        return syncRunRepository.findById(id)
                .map(SyncRunDto::from)
                .orElseThrow(() -> new NotFoundException("Sincronização não encontrada: " + id));
    }

    /**
     * Marca a disponibilidade de um folheto. Em produção é chamado pelo
     * verificador periódico ao detetar o folheto publicado.
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
