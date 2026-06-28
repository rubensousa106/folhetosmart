package com.folhetosmart.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Corpo de POST /api/v1/shopping (adicionar/atualizar uma oferta na lista).
 * O upsert é feito por (utilizador, produto, supermercado).
 */
public record ShoppingItemRequest(
        @NotBlank(message = "produto é obrigatório")
        String produto,

        @NotBlank(message = "supermercado é obrigatório")
        String supermercado,

        @NotNull(message = "preco é obrigatório")
        @PositiveOrZero(message = "O preço não pode ser negativo")
        BigDecimal preco,

        /** Opcional: quando nulo assume 1 (igual ao addOffer do Room). */
        @Positive(message = "A quantidade tem de ser positiva")
        Integer quantity
) {
}
