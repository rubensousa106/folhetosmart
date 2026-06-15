package com.folhetosmart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting em memória ({@link ConcurrentHashMap}) — suficiente para uma
 * única instância do backend (o plano gratuito do Render não inclui Redis).
 *
 * <ul>
 *   <li>autenticação ({@code POST /api/v1/auth/**}): 5 tentativas / 15 min;</li>
 *   <li>administração ({@code /api/v1/admin/**}): 10 pedidos / 1 min.</li>
 * </ul>
 *
 * As entradas com a janela já expirada são removidas de hora a hora por
 * {@link #purgeExpired()}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final int AUTH_MAX = 5;
    static final Duration AUTH_WINDOW = Duration.ofMinutes(15);
    static final int ADMIN_MAX = 10;
    static final Duration ADMIN_WINDOW = Duration.ofMinutes(1);

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String ADMIN_PREFIX = "/api/v1/admin/";

    /** Tentativas por IP (hash SHA-256) dentro da janela atual, por categoria. */
    private final Map<String, Attempt> authAttempts = new ConcurrentHashMap<>();
    private final Map<String, Attempt> adminAttempts = new ConcurrentHashMap<>();

    /** Contador e instante (epoch millis) da primeira tentativa na janela. */
    private static final class Attempt {
        final long firstAttemptMillis;
        int count;

        Attempt(long firstAttemptMillis) {
            this.firstAttemptMillis = firstAttemptMillis;
            this.count = 0;
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean auth = "POST".equals(request.getMethod()) && uri.startsWith(AUTH_PREFIX);
        boolean admin = uri.startsWith(ADMIN_PREFIX);
        return !(auth || admin);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean isAdmin = request.getRequestURI().startsWith(ADMIN_PREFIX);
        Map<String, Attempt> bucket = isAdmin ? adminAttempts : authAttempts;
        int max = isAdmin ? ADMIN_MAX : AUTH_MAX;
        long windowMillis = (isAdmin ? ADMIN_WINDOW : AUTH_WINDOW).toMillis();

        String key = hashIp(request.getRemoteAddr());
        long now = System.currentTimeMillis();

        // compute() corre sob o lock do bucket: incremento atómico e leitura
        // consistente da contagem, mesmo com pedidos concorrentes do mesmo IP.
        int[] currentCount = new int[1];
        bucket.compute(key, (k, current) -> {
            if (current == null || now - current.firstAttemptMillis > windowMillis) {
                current = new Attempt(now);   // janela nova (ou expirada): reinicia
            }
            current.count++;
            currentCount[0] = current.count;
            return current;
        });

        if (currentCount[0] > max) {
            log.warn("Rate limit excedido em {} (ip={})",
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String msg = isAdmin
                    ? "Demasiados pedidos de administração. Aguarda um minuto."
                    : "Demasiadas tentativas. Tenta novamente daqui a 15 minutos.";
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + msg + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Remove de hora a hora as entradas cuja janela já expirou (ambos os buckets). */
    @Scheduled(fixedRate = 3_600_000L)
    void purgeExpired() {
        purge(authAttempts, AUTH_WINDOW.toMillis());
        purge(adminAttempts, ADMIN_WINDOW.toMillis());
    }

    private static void purge(Map<String, Attempt> bucket, long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        bucket.values().removeIf(a -> a.firstAttemptMillis < cutoff);
    }

    /** SHA-256 do IP — a chave em memória nunca contém o IP em claro. */
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
