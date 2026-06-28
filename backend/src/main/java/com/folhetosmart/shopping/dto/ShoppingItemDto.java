package com.folhetosmart.shopping.dto;

import com.folhetosmart.shopping.ShoppingItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Item da lista devolvido ao cliente (web/app). JSON em snake_case (global). */
public record ShoppingItemDto(
        UUID id,
        String produto,
        String supermercado,
        BigDecimal preco,
        int quantity,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShoppingItemDto from(ShoppingItem i) {
        return new ShoppingItemDto(
                i.getId(),
                i.getProduto(),
                i.getSupermercado(),
                i.getPreco(),
                i.getQuantity(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
