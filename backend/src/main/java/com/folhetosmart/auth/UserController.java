package com.folhetosmart.auth;

import com.folhetosmart.auth.dto.UpdateMeRequest;
import com.folhetosmart.auth.dto.UserMeResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do utilizador autenticado (requer JWT). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** GET /api/v1/users/me — perfil (inclui distrito/cidade para o Aldi). */
    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal User user) {
        return UserMeResponse.from(user);
    }

    /** PUT /api/v1/users/me — atualiza a localização escolhida manualmente. */
    @PutMapping("/me")
    @Transactional
    public UserMeResponse updateMe(@AuthenticationPrincipal User user,
                                   @Valid @RequestBody UpdateMeRequest request) {
        user.setDistrict(blankToNull(request.district()));
        user.setCity(blankToNull(request.city()));
        return UserMeResponse.from(userRepository.save(user));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
