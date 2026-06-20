package com.folhetosmart.products;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Folheto mais recente de um supermercado, guardado como JSON simples
 * ({@code {"supermercado": "...", "produtos": [{"produto": "...", "preco": 1.99}]}}).
 * É o que a app lê em GET /api/v1/products/latest. Escrito pelo produtor
 * (scraper) via POST /api/v1/admin/products.
 */
@Entity
@Table(name = "latest_products")
@Getter
@Setter
@NoArgsConstructor
public class LatestProducts {

    /** Chave normalizada em minúsculas, ex.: "continente". */
    @Id
    @Column(name = "supermarket")
    private String supermarket;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "products_count", nullable = false)
    private int productsCount;

    /** Nome do folheto já analisado — flag "analisar 1×" (evita gastar IA à toa). */
    @Column(name = "source_flyer")
    private String sourceFlyer;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
