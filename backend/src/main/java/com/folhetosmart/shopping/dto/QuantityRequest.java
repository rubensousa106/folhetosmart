package com.folhetosmart.shopping.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corpo de PATCH /api/v1/shopping/{id}/quantity. */
public record QuantityRequest(
        @NotNull(message = "quantity é obrigatório")
        @Positive(message = "A quantidade tem de ser positiva")
        Integer quantity
) {
}
