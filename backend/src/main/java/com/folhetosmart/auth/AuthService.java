package com.folhetosmart.auth;

import com.folhetosmart.auth.dto.AuthResponse;
import com.folhetosmart.auth.dto.LoginRequest;
import com.folhetosmart.auth.dto.RefreshRequest;
import com.folhetosmart.auth.dto.RegisterRequest;
import com.folhetosmart.common.ApiException;
import com.folhetosmart.config.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // Logs de autenticação sem dados sensíveis: nunca passwords nem tokens,
    // e o email aparece mascarado (r***@dominio).
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        log.info("Login OK — uid={}", user.getId());
        return toResponse(user);
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
                user.getRole().name());
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
