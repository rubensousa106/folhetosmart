package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * PUT /api/v1/users/me/email — troca o email (exige a palavra-passe atual).
 * Como o email é o "subject" do JWT, a resposta traz um par de tokens novo.
 */
public record ChangeEmailRequest(
        @NotBlank(message = "Indica a palavra-passe atual")
        String currentPassword,

        @NotBlank(message = "Indica o novo email")
        @Email(message = "Email inválido")
        String newEmail
) {
}
