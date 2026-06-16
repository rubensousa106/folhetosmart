package com.folhetosmart.sync.dto;

/**
 * Resposta a POST /api/v1/admin/process-flyer. {@code products_imported} +
 * {@code status} ("success" | "error") em snake_case no JSON.
 */
public record AdminProcessFlyerResponse(
        int productsImported,
        String status
) {
}
