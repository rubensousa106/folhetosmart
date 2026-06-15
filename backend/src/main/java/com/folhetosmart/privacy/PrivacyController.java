package com.folhetosmart.privacy;

import com.folhetosmart.auth.User;
import com.folhetosmart.privacy.dto.ConsentRequest;
import com.folhetosmart.privacy.dto.ConsentStatusResponse;
import com.folhetosmart.privacy.dto.MyDataExport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direitos RGPD do utilizador (todos os endpoints requerem JWT). */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    /** GET /api/v1/privacy/my-data — exporta todos os dados em JSON. */
    @GetMapping("/my-data")
    public MyDataExport myData(@AuthenticationPrincipal User user, HttpServletRequest request) {
        return privacyService.exportMyData(user, request.getRemoteAddr());
    }

    /** DELETE /api/v1/privacy/my-account — elimina conta e dados (irreversível). */
    @DeleteMapping("/my-account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user,
                                              HttpServletRequest request) {
        privacyService.deleteAccount(user, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/privacy/consent — regista consentimento explícito. */
    @PostMapping("/consent")
    public ConsentStatusResponse consent(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody ConsentRequest body,
                                         HttpServletRequest request) {
        return privacyService.registerConsent(user, body, request.getRemoteAddr());
    }

    /** GET /api/v1/privacy/consent — consulta consentimentos dados. */
    @GetMapping("/consent")
    public ConsentStatusResponse consentStatus(@AuthenticationPrincipal User user) {
        return privacyService.getConsent(user);
    }
}
