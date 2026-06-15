package com.folhetosmart.promotions.dto;

import com.folhetosmart.prices.WeeklyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/** Uma promoção ativa, para o ecrã de promoções/destaques. */
public record PromotionDto(
        UUID productId,
        String displayName,
        String supermarket,
        String supermarketSlug,
        BigDecimal price,
        BigDecimal originalPrice,
        String promotionLabel,
        Double savingsPct,
        LocalDate validUntil
) {
    public static PromotionDto from(WeeklyPrice wp) {
        return new PromotionDto(
                wp.getProduct().getId(),
                wp.getProduct().getDisplayName(),
                wp.getSupermarket().getName(),
                wp.getSupermarket().getSlug(),
                wp.getPrice(),
                wp.getOriginalPrice(),
                wp.getPromotionLabel(),
                savings(wp),
                wp.getValidUntil());
    }

    private static Double savings(WeeklyPrice wp) {
        BigDecimal original = wp.getOriginalPrice();
        if (original == null || original.signum() <= 0) {
            return null;
        }
        return original.subtract(wp.getPrice())
                .divide(original, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
