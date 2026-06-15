package com.folhetosmart.shoppinglist;

import com.folhetosmart.shoppinglist.dto.OptimizeResponse;
import com.folhetosmart.shoppinglist.dto.OptimizeResponse.BasketItem;
import com.folhetosmart.shoppinglist.dto.OptimizeResponse.SupermarketBasket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Núcleo (puro) do otimizador de lista de compras.
 * <p>
 * Para cada item escolhe o supermercado mais barato e agrupa as compras por
 * supermercado. A poupança é a diferença entre comprar tudo na opção mais cara
 * de cada item e na mais barata. Sendo uma função pura (sem BD), é diretamente
 * testável em unidade.
 */
public final class ShoppingListOptimizer {

    private ShoppingListOptimizer() {
    }

    /** Uma opção de preço para um produto, num supermercado. */
    public record PriceOption(String supermarketSlug, String supermarketName, BigDecimal unitPrice) {
    }

    /** Um item pedido, já com as suas opções de preço por supermercado. */
    public record RequestedItem(UUID productId, String displayName, int quantity, List<PriceOption> options) {
    }

    public static OptimizeResponse optimize(List<RequestedItem> items) {
        // Preserva a ordem em que os supermercados aparecem.
        Map<String, BasketBuilder> baskets = new LinkedHashMap<>();
        BigDecimal optimizedTotal = BigDecimal.ZERO;
        BigDecimal worstTotal = BigDecimal.ZERO;

        for (RequestedItem item : items) {
            if (item.options() == null || item.options().isEmpty()) {
                continue; // produto sem preço disponível esta semana — ignora
            }
            PriceOption cheapest = item.options().stream()
                    .min(Comparator.comparing(PriceOption::unitPrice))
                    .orElseThrow();
            PriceOption dearest = item.options().stream()
                    .max(Comparator.comparing(PriceOption::unitPrice))
                    .orElseThrow();

            BigDecimal qty = BigDecimal.valueOf(item.quantity());
            BigDecimal line = money(cheapest.unitPrice().multiply(qty));
            optimizedTotal = optimizedTotal.add(line);
            worstTotal = worstTotal.add(money(dearest.unitPrice().multiply(qty)));

            baskets
                    .computeIfAbsent(cheapest.supermarketSlug(),
                            k -> new BasketBuilder(cheapest.supermarketName(), k))
                    .add(new BasketItem(
                            item.productId(),
                            item.displayName(),
                            item.quantity(),
                            cheapest.unitPrice(),
                            line));
        }

        List<SupermarketBasket> result = new ArrayList<>();
        for (BasketBuilder b : baskets.values()) {
            result.add(b.build());
        }

        BigDecimal poupanca = money(worstTotal.subtract(optimizedTotal));
        return new OptimizeResponse(money(optimizedTotal), poupanca, result);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Acumulador mutável de um cabaz por supermercado. */
    private static final class BasketBuilder {
        private final String name;
        private final String slug;
        private final List<BasketItem> items = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;

        BasketBuilder(String name, String slug) {
            this.name = name;
            this.slug = slug;
        }

        void add(BasketItem item) {
            items.add(item);
            subtotal = subtotal.add(item.lineTotal());
        }

        SupermarketBasket build() {
            return new SupermarketBasket(name, slug, money(subtotal), items);
        }
    }
}
