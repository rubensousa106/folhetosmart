package com.folhetosmart.prices;

import com.folhetosmart.products.Product;
import com.folhetosmart.sync.Supermarket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Preço de um produto num supermercado, válido numa semana. */
@Entity
@Table(name = "weekly_prices")
@Getter
@Setter
@NoArgsConstructor
public class WeeklyPrice {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supermarket_id")
    private Supermarket supermarket;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "original_price")
    private BigDecimal originalPrice;

    @Column(name = "is_promotion")
    private boolean promotion;

    @Column(name = "promotion_label")
    private String promotionLabel;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "raw_product_name")
    private String rawProductName;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;
}
