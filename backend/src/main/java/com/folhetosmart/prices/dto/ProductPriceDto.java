package com.folhetosmart.prices.dto;

import com.folhetosmart.prices.WeeklyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Preço de um produto num supermercado, para o ecrã Comparar. */
public record ProductPriceDto(
        String supermarket,
        String supermarketSlug,
        BigDecimal price,
        BigDecimal originalPrice,
        boolean isPromotion,
        String promotionLabel,
        LocalDate validUntil,
        boolean isBestPrice
) {
    public static ProductPriceDto from(WeeklyPrice wp, boolean isBestPrice) {
        return new ProductPriceDto(
                wp.getSupermarket().getName(),
                wp.getSupermarket().getSlug(),
                wp.getPrice(),
                wp.getOriginalPrice(),
                wp.isPromotion(),
                wp.getPromotionLabel(),
                wp.getValidUntil(),
                isBestPrice);
    }
}
