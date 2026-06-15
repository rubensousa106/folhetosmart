package com.folhetosmart.prices.dto;

import com.folhetosmart.prices.WeeklyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ponto do histórico de preços (uma semana, um supermercado). */
public record PriceHistoryPointDto(
        String supermarketSlug,
        BigDecimal price,
        boolean isPromotion,
        LocalDate validFrom,
        LocalDate validUntil
) {
    public static PriceHistoryPointDto from(WeeklyPrice wp) {
        return new PriceHistoryPointDto(
                wp.getSupermarket().getSlug(),
                wp.getPrice(),
                wp.isPromotion(),
                wp.getValidFrom(),
                wp.getValidUntil());
    }
}
