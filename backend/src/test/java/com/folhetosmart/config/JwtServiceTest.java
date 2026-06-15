package com.folhetosmart.config;

import com.folhetosmart.auth.Role;
import com.folhetosmart.auth.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // Segredo base64 de teste (>= 256 bits).
    private static final String SECRET =
            "ZmFsaGV0by1zbWFydC1kZXYtc2VjcmV0LWNoYW5nZS1tZS1wbGVhc2UtMzJi";

    private final JwtService jwtService =
            new JwtService(SECRET, 86_400_000L, 2_592_000_000L);

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("ruben@folhetosmart.pt")
                .role(Role.USER)
                .build();
    }

    @Test
    void gera_e_valida_access_token() {
        User user = user();
        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("ruben@folhetosmart.pt");

        var claims = jwtService.parse(token);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("uid", String.class)).isEqualTo(user.getId().toString());
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    @Test
    void refresh_token_tem_tipo_refresh_e_nao_autentica() {
        String refresh = jwtService.generateRefreshToken(user());

        var claims = jwtService.parse(refresh);
        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }
}
