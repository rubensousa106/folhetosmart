package com.folhetosmart.sync.dto;

import java.util.UUID;

/** POST /api/v1/sync/upload/{slug} — PDF aceite, em processamento. */
public record SyncUploadResponse(
        UUID syncRunId,
        String status
) {
}
