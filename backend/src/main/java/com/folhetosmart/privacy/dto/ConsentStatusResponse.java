package com.folhetosmart.privacy.dto;

import java.time.Instant;
import java.util.List;

/** GET /api/v1/privacy/consent — consentimentos dados pelo utilizador. */
public record ConsentStatusResponse(
        String consentVersion,
        Instant consentGivenAt,
        List<Entry> history
) {
    public record Entry(
            String action,
            String version,
            Instant createdAt
    ) {
    }
}
