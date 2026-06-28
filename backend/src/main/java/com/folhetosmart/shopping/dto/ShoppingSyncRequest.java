package com.folhetosmart.shopping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Corpo de PUT /api/v1/shopping — substitui a lista do utilizador pelo conjunto
 * de itens enviado. Pensado para a app Android empurrar a lista local (Room) na
 * primeira sincronização com o servidor.
 */
public record ShoppingSyncRequest(
        @NotNull(message = "items é obrigatório")
        @Valid
        List<ShoppingItemRequest> items
) {
}
