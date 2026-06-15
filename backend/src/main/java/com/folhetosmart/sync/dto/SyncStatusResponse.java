package com.folhetosmart.sync.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/sync/status. */
public record SyncStatusResponse(
        List<SupermarketStatus> supermarkets,
        boolean allReady,
        int readyCount,
        int totalCount,
        LastSync lastSync
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
            String syncSource           // site | drive | upload | null
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
