package com.folhetosmart.auth;

import com.folhetosmart.auth.dto.AuthResponse;
import com.folhetosmart.auth.dto.ForgotPasswordRequest;
import com.folhetosmart.auth.dto.LoginRequest;
import com.folhetosmart.auth.dto.RefreshRequest;
import com.folhetosmart.auth.dto.RegisterRequest;
import com.folhetosmart.common.ApiException;
import com.folhetosmart.config.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    // Logs de autenticação sem dados sensíveis: nunca passwords nem tokens,
    // e o email aparece mascarado (r***@dominio).
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetMailer mailer;
    private final long resetTtlMinutes;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       PasswordResetMailer mailer,
                       @Value("${folheto.password-reset.ttl-minutes:60}") long resetTtlMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailer = mailer;
        this.resetTtlMinutes = resetTtlMinutes;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registo recusado — email já existe ({})", mask(request.email()));
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma conta com este email.");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        userRepository.save(user);
        log.info("Conta criada — uid={}", user.getId());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("Login falhado ({})", mask(request.email()));
                    return new ApiException(
                            HttpStatus.UNAUTHORIZED, "Email ou palavra-passe incorretos.");
                });
        if (user.getTempPasswordExpiresAt() != null
                && Instant.now().isAfter(user.getTempPasswordExpiresAt())) {
            log.warn("Login com palavra-passe temporária expirada — uid={}", user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "A palavra-passe temporária expirou. Pede uma nova.");
        }
        log.info("Login OK — uid={}", user.getId());
        return toResponse(user);
    }

    /**
     * Recuperação de palavra-passe: gera uma temporária, grava-a (e a sua validade)
     * e pede ao n8n para a enviar por email. Responde sempre de forma neutra — não
     * revela se o email existe (evita enumeração de contas).
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresentOrElse(user -> {
            String temp = generateTempPassword();
            user.setPasswordHash(passwordEncoder.encode(temp));
            user.setTempPasswordExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTtlMinutes)));
            userRepository.save(user);
            mailer.sendTempPassword(user.getEmail(), user.getName(), temp, resetTtlMinutes);
            log.info("Palavra-passe temporária emitida — uid={}", user.getId());
        }, () ->
            log.warn("Recuperação pedida para email inexistente ({})", mask(request.email()))
        );
    }

    /** Troca um refresh token válido por um novo par de tokens. */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        final var invalid = new ApiException(
                HttpStatus.UNAUTHORIZED, "Sessão expirada. Inicia sessão novamente.");
        try {
            var claims = jwtService.parse(request.refreshToken());
            if (!jwtService.isRefreshToken(claims)) {
                log.warn("Refresh recusado — token não é refresh");
                throw invalid;
            }
            User user = userRepository.findByEmail(claims.getSubject())
                    .orElseThrow(() -> invalid);
            log.info("Refresh OK — uid={}", user.getId());
            return toResponse(user);
        } catch (JwtException e) {
            log.warn("Refresh recusado — token inválido/expirado");
            throw invalid;
        }
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                user.getEmail(),
                user.getRole().name(),
                user.getTempPasswordExpiresAt() != null);
    }

    /** Palavra-passe temporária aleatória (sem caracteres ambíguos: 0/O/1/l/I). */
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** "ruben@exemplo.pt" -> "r***@exemplo.pt" (para logs). */
    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "?";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
