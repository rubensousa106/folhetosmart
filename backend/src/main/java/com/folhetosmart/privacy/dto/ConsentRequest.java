package com.folhetosmart.privacy.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/privacy/consent — consentimento explícito do utilizador. */
public record ConsentRequest(
        @NotBlank(message = "A versão dos termos é obrigatória")
        String version,

        boolean notificationsAccepted
) {
}
