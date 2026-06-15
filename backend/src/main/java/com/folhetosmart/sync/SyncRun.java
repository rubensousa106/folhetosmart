package com.folhetosmart.sync;

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

/** Uma execução do automatizador (scraping + IA matching). */
@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "triggered_by")
    private String triggeredBy;     // "user" | "cron"

    private String status;          // "pending" | "running" | "done" | "error"

    @Column(name = "supermarkets_ready")
    private int supermarketsReady;

    @Column(name = "supermarkets_total")
    private int supermarketsTotal = 5;

    @Column(name = "products_matched")
    private int productsMatched;

    @Column(name = "products_unmatched")
    private int productsUnmatched;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
