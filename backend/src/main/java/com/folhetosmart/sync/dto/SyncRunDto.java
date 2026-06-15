package com.folhetosmart.sync.dto;

import com.folhetosmart.sync.SyncRun;

import java.time.Instant;
import java.util.UUID;

/** GET /api/v1/sync/runs/{id} — progresso de uma sincronização (polling). */
public record SyncRunDto(
        UUID id,
        String status,
        String triggeredBy,
        int supermarketsReady,
        int supermarketsTotal,
        int productsMatched,
        int productsUnmatched,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
    public static SyncRunDto from(SyncRun run) {
        return new SyncRunDto(
                run.getId(),
                run.getStatus(),
                run.getTriggeredBy(),
                run.getSupermarketsReady(),
                run.getSupermarketsTotal(),
                run.getProductsMatched(),
                run.getProductsUnmatched(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
