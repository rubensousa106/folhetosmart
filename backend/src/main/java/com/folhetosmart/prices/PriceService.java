package com.folhetosmart.prices;

import com.folhetosmart.prices.dto.PriceHistoryPointDto;
import com.folhetosmart.prices.dto.ProductPriceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PriceService {

    /** Quantas semanas de histórico devolver. */
    private static final int HISTORY_WEEKS = 12;

    private final WeeklyPriceRepository priceRepository;
    private final Clock clock;

    public PriceService(WeeklyPriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    /** Preços atuais por supermercado, com o mais barato marcado como "melhor". */
    public List<ProductPriceDto> currentPrices(UUID productId) {
        List<WeeklyPrice> prices =
                priceRepository.findCurrentForProduct(productId, today());
        // A query já vem ordenada por preço ascendente: o primeiro é o melhor.
        return java.util.stream.IntStream.range(0, prices.size())
                .mapToObj(i -> ProductPriceDto.from(prices.get(i), i == 0))
                .toList();
    }

    public List<PriceHistoryPointDto> priceHistory(UUID productId) {
        return priceRepository.findHistoryForProduct(productId).stream()
                // limita às últimas N semanas distintas (já vem por data desc)
                .limit((long) HISTORY_WEEKS * 5)  // até 5 supermercados por semana
                .map(PriceHistoryPointDto::from)
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
