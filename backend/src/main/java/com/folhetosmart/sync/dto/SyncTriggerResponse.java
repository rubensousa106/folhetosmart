package com.folhetosmart.sync.dto;

import java.util.UUID;

/** POST /api/v1/sync/trigger — devolve o id para polling de progresso. */
public record SyncTriggerResponse(
        UUID syncRunId,
        String status,
        int readyCount,
        int totalCount
) {
}
