package com.folhetosmart.shoppinglist.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resposta da otimização: total, poupança e o que comprar em cada supermercado. */
public record OptimizeResponse(
        BigDecimal totalOtimizado,
        BigDecimal poupanca,
        List<SupermarketBasket> porSupermercado
) {
    public record SupermarketBasket(
            String supermarket,
            String supermarketSlug,
            BigDecimal subtotal,
            List<BasketItem> items
    ) {
    }

    public record BasketItem(
            UUID productId,
            String displayName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }
}
