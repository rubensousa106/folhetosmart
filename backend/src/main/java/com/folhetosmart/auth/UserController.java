package com.folhetosmart.auth;

import com.folhetosmart.auth.dto.AuthResponse;
import com.folhetosmart.auth.dto.ChangeEmailRequest;
import com.folhetosmart.auth.dto.ChangePasswordRequest;
import com.folhetosmart.auth.dto.UpdateMeRequest;
import com.folhetosmart.auth.dto.UserMeResponse;
import com.folhetosmart.common.ApiException;
import com.folhetosmart.config.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do utilizador autenticado (requer JWT). Editável no menu Definições. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** GET /api/v1/users/me — perfil (nome, email, distrito/cidade, papel). */
    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal User user) {
        return UserMeResponse.from(user);
    }

    /** PUT /api/v1/users/me — atualiza o nome e a localização (distrito/cidade). */
    @PutMapping("/me")
    @Transactional
    public UserMeResponse updateMe(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody UpdateMeRequest request) {
        user.setName(blankToNull(request.name()));
        user.setDistrict(blankToNull(request.district()));
        user.setCity(blankToNull(request.city()));
        return UserMeResponse.from(userRepository.save(user));
    }

    /** PUT /api/v1/users/me/password — troca a palavra-passe (exige a atual). */
    @PutMapping("/me/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        requireCurrentPassword(user, request.currentPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Concluiu o "definir nova palavra-passe": deixa de ser temporária.
        user.setTempPasswordExpiresAt(null);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/v1/users/me/email — troca o email (exige a palavra-passe atual).
     * O email é o "subject" do JWT, por isso devolve um par de tokens novo — a app
     * substitui os tokens guardados e a sessão continua sem novo login.
     */
    @PutMapping("/me/email")
    @Transactional
    public AuthResponse changeEmail(@AuthenticationPrincipal User user,
                                    @Valid @RequestBody ChangeEmailRequest request) {
        requireCurrentPassword(user, request.currentPassword());
        String newEmail = request.newEmail().trim();
        if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma conta com este email.");
        }
        user.setEmail(newEmail);
        userRepository.save(user);
        return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                user.getEmail(),
                user.getRole().name(),
                user.getTempPasswordExpiresAt() != null);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A palavra-passe atual está incorreta.");
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
