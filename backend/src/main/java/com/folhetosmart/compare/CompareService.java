package com.folhetosmart.compare;

import com.folhetosmart.compare.dto.CompareResultDto;
import com.folhetosmart.prices.WeeklyPrice;
import com.folhetosmart.prices.WeeklyPriceRepository;
import com.folhetosmart.prices.dto.ProductPriceDto;
import com.folhetosmart.products.Product;
import com.folhetosmart.products.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CompareService {

    private final WeeklyPriceRepository priceRepository;
    private final ProductService productService;
    private final Clock clock;

    public CompareService(WeeklyPriceRepository priceRepository,
                          ProductService productService,
                          Clock clock) {
        this.priceRepository = priceRepository;
        this.productService = productService;
        this.clock = clock;
    }

    public List<CompareResultDto> compare(List<UUID> productIds) {
        LocalDate today = LocalDate.now(clock);

        // Agrupa os preços atuais por produto (a query já vem ordenada por preço).
        Map<UUID, List<WeeklyPrice>> byProduct = new LinkedHashMap<>();
        for (WeeklyPrice wp : priceRepository.findCurrentForProducts(productIds, today)) {
            byProduct.computeIfAbsent(wp.getProduct().getId(), k -> new ArrayList<>()).add(wp);
        }

        List<CompareResultDto> results = new ArrayList<>();
        for (UUID productId : productIds) {
            Product product = productService.getEntity(productId);
            List<WeeklyPrice> prices = byProduct.getOrDefault(productId, List.of());

            List<ProductPriceDto> priceDtos = new ArrayList<>();
            for (int i = 0; i < prices.size(); i++) {
                priceDtos.add(ProductPriceDto.from(prices.get(i), i == 0));
            }

            WeeklyPrice best = prices.isEmpty() ? null : prices.get(0);
            results.add(new CompareResultDto(
                    productId,
                    product.getDisplayName(),
                    best != null ? best.getSupermarket().getName() : null,
                    best != null ? best.getPrice() : null,
                    priceDtos));
        }
        return results;
    }
}
