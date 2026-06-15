package com.folhetosmart.shoppinglist;

import com.folhetosmart.prices.WeeklyPrice;
import com.folhetosmart.prices.WeeklyPriceRepository;
import com.folhetosmart.products.Product;
import com.folhetosmart.products.ProductRepository;
import com.folhetosmart.shoppinglist.ShoppingListOptimizer.PriceOption;
import com.folhetosmart.shoppinglist.ShoppingListOptimizer.RequestedItem;
import com.folhetosmart.shoppinglist.dto.OptimizeRequest;
import com.folhetosmart.shoppinglist.dto.OptimizeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ShoppingListService {

    private final ProductRepository productRepository;
    private final WeeklyPriceRepository priceRepository;
    private final Clock clock;

    public ShoppingListService(ProductRepository productRepository,
                               WeeklyPriceRepository priceRepository,
                               Clock clock) {
        this.productRepository = productRepository;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    public OptimizeResponse optimize(OptimizeRequest request) {
        List<UUID> productIds = request.items().stream()
                .map(OptimizeRequest.Item::productId)
                .toList();

        Map<UUID, String> names = new HashMap<>();
        productRepository.findAllById(productIds)
                .forEach(p -> names.put(p.getId(), p.getDisplayName()));

        // Preços atuais agrupados por produto.
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<PriceOption>> optionsByProduct = new HashMap<>();
        for (WeeklyPrice wp : priceRepository.findCurrentForProducts(productIds, today)) {
            optionsByProduct
                    .computeIfAbsent(wp.getProduct().getId(), k -> new ArrayList<>())
                    .add(new PriceOption(
                            wp.getSupermarket().getSlug(),
                            wp.getSupermarket().getName(),
                            wp.getPrice()));
        }

        List<RequestedItem> requested = new ArrayList<>();
        for (OptimizeRequest.Item item : request.items()) {
            requested.add(new RequestedItem(
                    item.productId(),
                    names.getOrDefault(item.productId(), "Produto"),
                    item.quantity(),
                    optionsByProduct.getOrDefault(item.productId(), List.of())));
        }

        return ShoppingListOptimizer.optimize(requested);
    }
}
