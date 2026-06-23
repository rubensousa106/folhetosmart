package com.folhetosmart.sync;

import com.folhetosmart.products.LatestProducts;
import com.folhetosmart.products.LatestProductsRepository;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse;
import com.folhetosmart.sync.dto.AdminFlyersStatusResponse.FlyerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Operações do painel de administração: produtos do folheto e estado da semana. */
@Service
public class AdminService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final SupermarketRepository supermarketRepository;
    private final LatestProductsRepository latestProductsRepository;
    private final Clock clock;

    public AdminService(SupermarketRepository supermarketRepository,
                        LatestProductsRepository latestProductsRepository,
                        Clock clock) {
        this.supermarketRepository = supermarketRepository;
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
}
