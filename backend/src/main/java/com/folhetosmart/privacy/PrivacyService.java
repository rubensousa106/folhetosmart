package com.folhetosmart.privacy;

import com.folhetosmart.alerts.PriceAlertRepository;
import com.folhetosmart.alerts.dto.AlertDto;
import com.folhetosmart.auth.User;
import com.folhetosmart.auth.UserRepository;
import com.folhetosmart.privacy.dto.ConsentRequest;
import com.folhetosmart.privacy.dto.ConsentStatusResponse;
import com.folhetosmart.privacy.dto.MyDataExport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Direitos RGPD do titular dos dados: acesso/portabilidade (export),
 * eliminação (hard delete) e registo de consentimento com auditoria.
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    private final UserRepository userRepository;
    private final PriceAlertRepository alertRepository;
    private final ConsentLogRepository consentLogRepository;
    private final Clock clock;

    public PrivacyService(UserRepository userRepository,
                          PriceAlertRepository alertRepository,
                          ConsentLogRepository consentLogRepository,
                          Clock clock) {
        this.userRepository = userRepository;
        this.alertRepository = alertRepository;
        this.consentLogRepository = consentLogRepository;
        this.clock = clock;
    }

    /** Exporta todos os dados do utilizador (Art. 15.º e 20.º RGPD). */
    @Transactional
    public MyDataExport exportMyData(User user, String clientIp) {
        List<AlertDto> alerts = alertRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(AlertDto::from)
                .toList();

        List<ConsentStatusResponse.Entry> consents = consentHistory(user);

        consentLogRepository.save(ConsentLog.of(
                user.getId(), ConsentLog.ACTION_DATA_EXPORTED, null, hashIp(clientIp)));

        return new MyDataExport(
                new MyDataExport.UserData(
                        user.getId(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getCreatedAt(),
                        user.getConsentGivenAt(),
                        user.getConsentVersion()),
                alerts,
                consents,
                Instant.now(clock));
    }

    /** Elimina a conta e todos os dados (Art. 17.º RGPD). Irreversível. */
    @Transactional
    public void deleteAccount(User user, String clientIp) {
        // Auditoria primeiro — o FK ON DELETE SET NULL preserva o registo
        // depois do hard delete do utilizador.
        consentLogRepository.save(ConsentLog.of(
                user.getId(), ConsentLog.ACTION_ACCOUNT_DELETED, null, hashIp(clientIp)));

        alertRepository.deleteByUserId(user.getId());

        // Hard delete — sem período de graça.
        userRepository.delete(user);
        log.info("Conta eliminada (RGPD) — uid={}", user.getId());
    }

    /** Regista consentimento explícito com timestamp e versão dos termos. */
    @Transactional
    public ConsentStatusResponse registerConsent(User user, ConsentRequest request, String clientIp) {
        Instant now = Instant.now(clock);
        user.setConsentGivenAt(now);
        user.setConsentVersion(request.version());
        userRepository.save(user);

        String ipHash = hashIp(clientIp);
        consentLogRepository.save(ConsentLog.of(
                user.getId(), ConsentLog.ACTION_CONSENT_GIVEN, request.version(), ipHash));
        if (request.notificationsAccepted()) {
            consentLogRepository.save(ConsentLog.of(
                    user.getId(), ConsentLog.ACTION_NOTIFICATIONS_CONSENT, request.version(), ipHash));
        }
        return getConsent(user);
    }

    /** Consulta os consentimentos dados. */
    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsent(User user) {
        return new ConsentStatusResponse(
                user.getConsentVersion(),
                user.getConsentGivenAt(),
                consentHistory(user));
    }

    private List<ConsentStatusResponse.Entry> consentHistory(User user) {
        return consentLogRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(c -> new ConsentStatusResponse.Entry(
                        c.getAction(), c.getVersion(), c.getCreatedAt()))
                .toList();
    }

    /** SHA-256 do IP — nunca guardamos o IP em claro. */
    static String hashIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 existe em qualquer JVM; nunca deve acontecer.
            return null;
        }
    }
}
