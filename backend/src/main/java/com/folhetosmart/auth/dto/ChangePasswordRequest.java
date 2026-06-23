package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/users/me/password — troca a palavra-passe (exige a atual). */
public record ChangePasswordRequest(
        @NotBlank(message = "Indica a palavra-passe atual")
        String currentPassword,

        @NotBlank(message = "Indica a nova palavra-passe")
        @Size(min = 8, message = "A nova palavra-passe deve ter pelo menos 8 caracteres")
        String newPassword
) {
}
