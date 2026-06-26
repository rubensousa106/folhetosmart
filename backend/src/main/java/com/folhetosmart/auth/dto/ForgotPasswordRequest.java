package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Pedido de recuperação de palavra-passe (envia uma temporária por email). */
public record ForgotPasswordRequest(
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {
}
