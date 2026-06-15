package com.folhetosmart.compare.dto;

import com.folhetosmart.prices.dto.ProductPriceDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Resultado de comparação de um produto entre supermercados. */
public record CompareResultDto(
        UUID productId,
        String displayName,
        String bestSupermarket,
        BigDecimal bestPrice,
        List<ProductPriceDto> prices
) {
}
