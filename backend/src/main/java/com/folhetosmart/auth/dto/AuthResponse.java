package com.folhetosmart.auth.dto;

public record AuthResponse(
        String token,
        String refreshToken,
        String email,
        String role,
        /** True quando a palavra-passe atual é temporária — a app obriga a defini-la de novo. */
        boolean mustChangePassword
) {
}
