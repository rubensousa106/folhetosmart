package com.folhetosmart.shoppinglist;

import com.folhetosmart.shoppinglist.ShoppingListOptimizer.PriceOption;
import com.folhetosmart.shoppinglist.ShoppingListOptimizer.RequestedItem;
import com.folhetosmart.shoppinglist.dto.OptimizeResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListOptimizerTest {

    private static PriceOption opt(String slug, String name, String price) {
        return new PriceOption(slug, name, new BigDecimal(price));
    }

    @Test
    void escolhe_o_supermercado_mais_barato_por_item_e_agrupa() {
        UUID doritos = UUID.randomUUID();
        UUID leite = UUID.randomUUID();

        var items = List.of(
                new RequestedItem(doritos, "Doritos 150g", 2, List.of(
                        opt("lidl", "Lidl", "1.39"),
                        opt("continente", "Continente", "1.49"))),
                new RequestedItem(leite, "Leite Mimosa 1L", 1, List.of(
                        opt("lidl", "Lidl", "0.89"),
                        opt("pingo-doce", "Pingo Doce", "0.79")))
        );

        OptimizeResponse r = ShoppingListOptimizer.optimize(items);

        // 1,39*2 + 0,79*1 = 3,57
        assertThat(r.totalOtimizado()).isEqualByComparingTo("3.57");
        // (1,49*2 + 0,89) - 3,57 = 3,87 - 3,57 = 0,30
        assertThat(r.poupanca()).isEqualByComparingTo("0.30");

        // Dois cabazes: Lidl (doritos) e Pingo Doce (leite)
        assertThat(r.porSupermercado()).hasSize(2);

        var lidl = r.porSupermercado().stream()
                .filter(b -> b.supermarketSlug().equals("lidl")).findFirst().orElseThrow();
        assertThat(lidl.subtotal()).isEqualByComparingTo("2.78");
        assertThat(lidl.items()).hasSize(1);
        assertThat(lidl.items().get(0).quantity()).isEqualTo(2);

        var pingo = r.porSupermercado().stream()
                .filter(b -> b.supermarketSlug().equals("pingo-doce")).findFirst().orElseThrow();
        assertThat(pingo.subtotal()).isEqualByComparingTo("0.79");
    }

    @Test
    void agrupa_varios_itens_no_mesmo_supermercado() {
        var items = List.of(
                new RequestedItem(UUID.randomUUID(), "A", 1, List.of(
                        opt("lidl", "Lidl", "1.00"),
                        opt("aldi", "Aldi", "1.20"))),
                new RequestedItem(UUID.randomUUID(), "B", 3, List.of(
                        opt("lidl", "Lidl", "0.50"),
                        opt("aldi", "Aldi", "0.60")))
        );

        OptimizeResponse r = ShoppingListOptimizer.optimize(items);

        assertThat(r.porSupermercado()).hasSize(1);
        var lidl = r.porSupermercado().get(0);
        assertThat(lidl.supermarketSlug()).isEqualTo("lidl");
        // 1,00 + 0,50*3 = 2,50
        assertThat(lidl.subtotal()).isEqualByComparingTo("2.50");
        assertThat(r.totalOtimizado()).isEqualByComparingTo("2.50");
    }

    @Test
    void ignora_itens_sem_precos_disponiveis() {
        var items = List.of(
                new RequestedItem(UUID.randomUUID(), "Com preço", 1, List.of(
                        opt("lidl", "Lidl", "2.00"))),
                new RequestedItem(UUID.randomUUID(), "Sem preço", 5, List.of())
        );

        OptimizeResponse r = ShoppingListOptimizer.optimize(items);

        assertThat(r.porSupermercado()).hasSize(1);
        assertThat(r.totalOtimizado()).isEqualByComparingTo("2.00");
        assertThat(r.poupanca()).isEqualByComparingTo("0.00");
    }

    @Test
    void lista_vazia_devolve_zero() {
        OptimizeResponse r = ShoppingListOptimizer.optimize(List.of());
        assertThat(r.totalOtimizado()).isEqualByComparingTo("0.00");
        assertThat(r.poupanca()).isEqualByComparingTo("0.00");
        assertThat(r.porSupermercado()).isEmpty();
    }
}
