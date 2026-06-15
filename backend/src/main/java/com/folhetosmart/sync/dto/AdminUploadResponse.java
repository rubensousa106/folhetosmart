package com.folhetosmart.sync.dto;

import java.util.UUID;

/**
 * Resposta ao upload de um folheto pelo ADMIN. Os campos vão para snake_case
 * no JSON ({@code sync_run_id}, {@code drive_file_id}) pela estratégia global
 * do Jackson.
 */
public record AdminUploadResponse(
        UUID syncRunId,
        String filename,
        String driveFileId,
        String status
) {
}
