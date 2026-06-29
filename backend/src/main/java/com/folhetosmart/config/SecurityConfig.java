package com.folhetosmart.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Segurança stateless baseada em JWT + RBAC, endurecida para produção. */
@Configuration
@EnableMethodSecurity   // ativa @PreAuthorize (ex.: endpoints só de ADMIN)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final boolean requireHttps;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          RateLimitFilter rateLimitFilter,
                          @Value("${folheto.security.require-https}") boolean requireHttps) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.requireHttps = requireHttps;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Cabeçalhos de segurança exigidos para a Play Store (Update 5):
                // HSTS (1 ano, inclui subdomínios), X-Content-Type-Options: nosniff,
                // X-Frame-Options: DENY.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .authorizeHttpRequests(auth -> auth
                        // Públicos
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Amostra pública (isco do site) — limitada a poucas ofertas.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/highlights").permitAll()
                        // Dados dos folhetos — NEGÓCIO PRIVADO: só utilizadores
                        // autenticados (a app envia sempre o JWT). Evita que
                        // concorrentes "raspem" a base de comparação.
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").authenticated()
                        // Leitura do estado/progresso é pública (a app só lê).
                        .requestMatchers(HttpMethod.GET, "/api/v1/sync/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sync/runs/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        // Restante de /sync (ex.: marcar disponibilidade) é só ADMIN
                        // (@PreAuthorize nos métodos garante o papel).
                        .requestMatchers("/api/v1/sync/**").authenticated()
                        // Painel de administração — só ADMIN (@PreAuthorize de classe).
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        // Geridos por utilizador autenticado
                        .requestMatchers("/api/v1/alerts/**").authenticated()
                        .requestMatchers("/api/v1/privacy/**").authenticated()
                        .anyRequest().authenticated())
                // Rate limiting antes da autenticação (5 tentativas / 15 min).
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        // HTTPS obrigatório em produção (FOLHETO_REQUIRE_HTTPS=true).
        // Em dev local fica desligado para permitir http://localhost:8080.
        if (requireHttps) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http.build();
    }

    /** CORS — permite à app web (browser) chamar a API a partir do seu domínio. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "https://folhetosmart.pt",
                "https://www.folhetosmart.pt",
                "https://*.pages.dev",
                "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Custo 12 — mínimo exigido na revisão de segurança (Update 5).
        return new BCryptPasswordEncoder(12);
    }
}
