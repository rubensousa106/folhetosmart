package com.folhetosmart.compare.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Corpo de POST /api/v1/compare — { "product_ids": ["uuid1", "uuid2"] }. */
public record CompareRequest(
        @NotEmpty(message = "Indica pelo menos um produto a comparar")
        List<UUID> productIds
) {
}
