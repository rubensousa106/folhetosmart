package com.folhetosmart.products.dto;

import com.folhetosmart.products.Product;

import java.util.UUID;

public record ProductDto(
        UUID id,
        String canonicalName,
        String displayName,
        String brand,
        String category,
        Integer weightGrams
) {
    public static ProductDto from(Product p) {
        return new ProductDto(
                p.getId(),
                p.getCanonicalName(),
                p.getDisplayName(),
                p.getBrand(),
                p.getCategory(),
                p.getWeightGrams());
    }
}
