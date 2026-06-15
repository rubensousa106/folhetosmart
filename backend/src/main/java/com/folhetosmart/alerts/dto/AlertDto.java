package com.folhetosmart.alerts.dto;

import com.folhetosmart.alerts.PriceAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertDto(
        UUID id,
        UUID productId,
        String productDisplayName,
        BigDecimal targetPrice,
        boolean anyPromotion,
        boolean active,
        Instant createdAt
) {
    public static AlertDto from(PriceAlert a) {
        return new AlertDto(
                a.getId(),
                a.getProduct().getId(),
                a.getProduct().getDisplayName(),
                a.getTargetPrice(),
                a.isAnyPromotion(),
                a.isActive(),
                a.getCreatedAt());
    }
}
