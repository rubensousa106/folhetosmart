package com.folhetosmart.alerts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** Corpo de POST /api/v1/alerts. */
public record AlertRequest(
        @NotNull(message = "product_id é obrigatório")
        UUID productId,

        @Positive(message = "O preço-alvo tem de ser positivo")
        BigDecimal targetPrice,

        boolean anyPromotion
) {
}
