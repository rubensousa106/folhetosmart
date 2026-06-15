package com.folhetosmart.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditoria RGPD de ações de privacidade. O user_id é guardado como UUID
 * simples (sem relação JPA) porque deve sobreviver ao hard delete da conta —
 * o FK na BD tem ON DELETE SET NULL.
 */
@Entity
@Table(name = "consent_log")
@Getter
@Setter
@NoArgsConstructor
public class ConsentLog {

    /** Ações registadas. */
    public static final String ACTION_CONSENT_GIVEN = "consent_given";
    public static final String ACTION_CONSENT_WITHDRAWN = "consent_withdrawn";
    public static final String ACTION_DATA_EXPORTED = "data_exported";
    public static final String ACTION_ACCOUNT_DELETED = "account_deleted";
    public static final String ACTION_NOTIFICATIONS_CONSENT = "notifications_consent_given";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String action;

    private String version;

    @Column(name = "ip_hash")
    private String ipHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public static ConsentLog of(UUID userId, String action, String version, String ipHash) {
        ConsentLog log = new ConsentLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setVersion(version);
        log.setIpHash(ipHash);
        return log;
    }
}
