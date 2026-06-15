package com.folhetosmart.promotions;

import com.folhetosmart.prices.WeeklyPriceRepository;
import com.folhetosmart.promotions.dto.PromotionDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PromotionService {

    private final WeeklyPriceRepository priceRepository;
    private final Clock clock;

    public PromotionService(WeeklyPriceRepository priceRepository, Clock clock) {
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    public List<PromotionDto> current(String supermarketSlug, int limit) {
        LocalDate today = LocalDate.now(clock);
        return priceRepository
                .findCurrentPromotions(today, supermarketSlug,
                        PageRequest.of(0, Math.min(limit, 200)))
                .stream()
                .map(PromotionDto::from)
                .toList();
    }
}
