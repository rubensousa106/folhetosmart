package com.folhetosmart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Rate limiting dos endpoints de autenticação: máx. 5 tentativas por IP em
 * janelas de 15 minutos.
 *
 * Implementado sobre Redis (chave {@code rate_limit:auth:{ip_hash}}) para
 * funcionar corretamente com múltiplas instâncias do backend. Se o Redis
 * estiver indisponível, o filtro falha aberto (deixa passar) e regista um
 * aviso — privilegia disponibilidade do login sobre o limite estrito.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "rate_limit:auth:";

    private final StringRedisTemplate redis;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Só limita POSTs de autenticação (login, register, refresh).
        return !("POST".equals(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/auth/"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String key = KEY_PREFIX + hashIp(request.getRemoteAddr());

        long attempts;
        try {
            Long count = redis.opsForValue().increment(key);
            attempts = count == null ? 1 : count;
            if (attempts == 1) {
                // Primeira tentativa da janela: arranca o TTL de 15 minutos.
                redis.expire(key, WINDOW);
            }
        } catch (Exception ex) {
            // Redis indisponível: falha aberto para não bloquear logins.
            log.warn("Rate limiter sem Redis ({}); pedido permitido.", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (attempts > MAX_ATTEMPTS) {
            log.warn("Rate limit excedido em {} (ip={})",
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Demasiadas tentativas. Tenta novamente daqui a 15 minutos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** SHA-256 do IP — a chave no Redis nunca contém o IP em claro. */
    private static String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (ip == null ? "" : ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 existe em qualquer JVM.
            return "na";
        }
    }
}
