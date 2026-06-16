package com.folhetosmart.sync.dto;

/**
 * Resposta a POST /api/v1/admin/upload-to-drive. snake_case no JSON
 * ({@code drive_file_id}) pela estratégia global do Jackson.
 */
public record AdminUploadToDriveResponse(
        String driveFileId,
        String filename
) {
}
