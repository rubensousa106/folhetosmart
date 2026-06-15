package com.folhetosmart.auth.dto;

import com.folhetosmart.auth.User;

import java.util.UUID;

/** GET /api/v1/users/me — perfil do utilizador autenticado. */
public record UserMeResponse(
        UUID id,
        String email,
        String role,
        String district,
        String city
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDistrict(),
                user.getCity());
    }
}
