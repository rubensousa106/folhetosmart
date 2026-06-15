package com.folhetosmart.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Supermercado suportado. */
@Entity
@Table(name = "supermarkets")
@Getter
@Setter
@NoArgsConstructor
public class Supermarket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "flyer_day")
    private String flyerDay;

    @Column(name = "flyer_available")
    private boolean flyerAvailable;

    @Column(name = "flyer_available_since")
    private Instant flyerAvailableSince;

    @Column(name = "website_url")
    private String websiteUrl;

    // --- Estado de sincronização (ecrã Sincronizar — 4 estados) ---
    @Column(name = "sync_status")
    private String syncStatus = "pending";   // pending | running | success | error

    @Column(name = "products_imported")
    private int productsImported;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "sync_source")
    private String syncSource;   // site | drive | upload | null
}
