package com.folhetosmart.products;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Pesquisa por nome (case-insensitive). Usa o índice trigram da coluna. */
    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(p.displayName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.canonicalName) LIKE LOWER(CONCAT('%', :search, '%'))
            ORDER BY p.displayName
            """)
    Page<Product> search(@Param("search") String search, Pageable pageable);

    /** Todos os produtos com preço válido na data dada (para a cache semanal da app). */
    @Query("""
            SELECT p FROM Product p
            WHERE p.id IN (
                SELECT wp.product.id FROM WeeklyPrice wp
                WHERE wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            )
            ORDER BY p.displayName
            """)
    Page<Product> findCurrentWeek(@Param("onDate") LocalDate onDate, Pageable pageable);

    /**
     * Produtos com promoção ativa na data dada — ecrã de início útil quando a
     * pesquisa está vazia. Ordenados pelo maior desconto absoluto.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.id IN (
                SELECT wp.product.id FROM WeeklyPrice wp
                WHERE wp.promotion = true
                  AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            )
            ORDER BY (
                SELECT MAX(wp2.originalPrice - wp2.price) FROM WeeklyPrice wp2
                WHERE wp2.product.id = p.id AND wp2.promotion = true
                  AND wp2.validFrom <= :onDate AND wp2.validUntil >= :onDate
            ) DESC
            """)
    List<Product> findOnPromotion(@Param("onDate") LocalDate onDate, Pageable pageable);
}
