package com.folhetosmart.config;

import com.folhetosmart.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Emite e valida tokens JWT (HMAC-SHA256).
 * Access token: 24h. Refresh token: 30 dias (claim typ="refresh").
 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${folheto.jwt.secret}") String secret,
            @Value("${folheto.jwt.expiration-ms}") long expirationMs,
            @Value("${folheto.jwt.refresh-expiration-ms}") long refreshExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Access token (24h). */
    public String generateToken(User user) {
        return buildToken(user, TYPE_ACCESS, expirationMs);
    }

    /** Refresh token (30 dias). */
    public String generateRefreshToken(User user) {
        return buildToken(user, TYPE_REFRESH, refreshExpirationMs);
    }

    private String buildToken(User user, String type, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    /** True se as claims pertencem a um refresh token. */
    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    /**
     * True se o token pode autenticar pedidos (access token).
     * Tokens antigos sem claim "typ" continuam válidos como access.
     */
    public boolean isAccessToken(Claims claims) {
        String type = claims.get(CLAIM_TYPE, String.class);
        return type == null || TYPE_ACCESS.equals(type);
    }
}
