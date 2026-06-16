package com.folhetosmart.sync.dto;

/**
 * Corpo de POST /api/v1/admin/process-flyer. Os campos vêm em snake_case do
 * JSON ({@code drive_file_id}, {@code supermarket_slug}, ...) — a estratégia
 * global SNAKE_CASE do Jackson trata da desserialização.
 */
public record AdminProcessFlyerRequest(
        String driveFileId,
        String supermarketSlug,
        String validFrom,
        String validUntil
) {
}
