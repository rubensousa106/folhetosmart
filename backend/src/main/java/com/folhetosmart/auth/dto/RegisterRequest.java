package com.folhetosmart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "O email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "A palavra-passe é obrigatória")
        @Size(min = 8, message = "A palavra-passe tem de ter pelo menos 8 caracteres")
        String password
) {
}
