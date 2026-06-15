package com.folhetosmart.shoppinglist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** POST /api/v1/shopping-list/optimize. */
public record OptimizeRequest(
        @NotEmpty(message = "A lista de compras está vazia")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull(message = "product_id em falta")
            UUID productId,

            @Min(value = 1, message = "A quantidade tem de ser pelo menos 1")
            int quantity
    ) {
    }
}
