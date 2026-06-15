package com.folhetosmart.sync.dto;

import java.time.Instant;
import java.util.List;

/**
 * Estado dos folhetos da semana atual para o painel de administração.
 * snake_case no JSON ({@code has_flyer}, {@code drive_filename}, ...).
 */
public record AdminFlyersStatusResponse(
        String week,
        List<FlyerStatus> supermarkets
) {
    public record FlyerStatus(
            String name,
            String slug,
            boolean hasFlyer,
            String driveFilename,
            int productsImported,
            Instant syncedAt
    ) {
    }
}
