package com.folhetosmart.sync.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/sync/status. */
public record SyncStatusResponse(
        List<SupermarketStatus> supermarkets,
        boolean allReady,
        int readyCount,
        int totalCount,
        LastSync lastSync,
        // Honestidade da UI (Fix 3): a app só mostra "Ver promoções da semana"
        // quando existem mesmo produtos válidos esta semana na BD.
        boolean hasCurrentWeekData,
        int totalProductsThisWeek
) {
    public record SupermarketStatus(
            String name,
            String slug,
            boolean flyerAvailable,
            Instant availableSince,
            String syncStatus,          // pending | running | success | error
            int productsImported,
            Instant syncedAt,
            String errorMessage,
            String syncSource,          // site | drive | upload | null
            String progressMessage      // ex.: "página 2/4" durante running; pode ser null
    ) {
    }

    public record LastSync(
            Instant finishedAt,
            int productsMatched,
            long promotionsFound,
            Double avgSavingsPct
    ) {
    }
}
