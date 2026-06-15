package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/refresh. */
public record RefreshRequest(
        @NotBlank(message = "O refresh token é obrigatório")
        String refreshToken
) {
}
