package com.folhetosmart.privacy.dto;

import com.folhetosmart.alerts.dto.AlertDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/privacy/my-data — exportação completa dos dados do utilizador
 * (direito à portabilidade, Art. 20.º RGPD).
 */
public record MyDataExport(
        UserData user,
        List<AlertDto> alerts,
        List<ConsentStatusResponse.Entry> consents,
        Instant exportedAt
) {
    public record UserData(
            UUID id,
            String email,
            String role,
            Instant createdAt,
            Instant consentGivenAt,
            String consentVersion
    ) {
    }
}
