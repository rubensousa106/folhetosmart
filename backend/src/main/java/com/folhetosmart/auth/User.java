package com.folhetosmart.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Nome do utilizador (opcional; editável no perfil em Definições). */
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "fcm_token")
    private String fcmToken;

    // --- Localização para o folheto regional do Aldi (escolha manual) ---
    private String district;

    private String city;

    // --- RGPD (Update 5) ---
    @Column(name = "consent_given_at")
    private Instant consentGivenAt;

    @Column(name = "consent_version")
    private String consentVersion;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    // --- Recuperação de palavra-passe ---
    /** Se ≠ null, a palavra-passe atual é temporária e expira nesta data. */
    @Column(name = "temp_password_expires_at")
    private Instant tempPasswordExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
