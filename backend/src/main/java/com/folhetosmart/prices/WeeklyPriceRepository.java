package com.folhetosmart.prices;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WeeklyPriceRepository extends JpaRepository<WeeklyPrice, UUID> {

    /** Preços atuais (válidos na data dada) de um produto, mais barato primeiro. */
    @Query("""
            SELECT wp FROM WeeklyPrice wp
            JOIN FETCH wp.supermarket
            WHERE wp.product.id = :productId
              AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            ORDER BY wp.price ASC
            """)
    List<WeeklyPrice> findCurrentForProduct(
            @Param("productId") UUID productId,
            @Param("onDate") LocalDate onDate);

    /** Preços atuais de vários produtos (para comparação e otimização de lista). */
    @Query("""
            SELECT wp FROM WeeklyPrice wp
            JOIN FETCH wp.supermarket
            WHERE wp.product.id IN :productIds
              AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            ORDER BY wp.product.id, wp.price ASC
            """)
    List<WeeklyPrice> findCurrentForProducts(
            @Param("productIds") List<UUID> productIds,
            @Param("onDate") LocalDate onDate);

    /** Histórico de preços de um produto (todas as semanas), mais recente primeiro. */
    @Query("""
            SELECT wp FROM WeeklyPrice wp
            JOIN FETCH wp.supermarket
            WHERE wp.product.id = :productId
            ORDER BY wp.validFrom DESC
            """)
    List<WeeklyPrice> findHistoryForProduct(@Param("productId") UUID productId);

    /** Nº de promoções ativas na data dada (para o resumo de sincronização). */
    @Query("""
            SELECT COUNT(wp) FROM WeeklyPrice wp
            WHERE wp.promotion = true
              AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            """)
    long countCurrentPromotions(@Param("onDate") LocalDate onDate);

    /** Poupança média (%) das promoções ativas: (original - preço) / original * 100. */
    @Query("""
            SELECT AVG((wp.originalPrice - wp.price) / wp.originalPrice * 100)
            FROM WeeklyPrice wp
            WHERE wp.promotion = true
              AND wp.originalPrice IS NOT NULL AND wp.originalPrice > 0
              AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            """)
    Double averageCurrentSavingsPct(@Param("onDate") LocalDate onDate);

    /** Nº de produtos distintos com preço ativo (para "products_matched"). */
    @Query("""
            SELECT COUNT(DISTINCT wp.product.id) FROM WeeklyPrice wp
            WHERE wp.validFrom <= :onDate AND wp.validUntil >= :onDate
            """)
    long countDistinctProductsWithCurrentPrice(@Param("onDate") LocalDate onDate);

    /** Promoções ativas (maior desconto primeiro). Filtro opcional por supermercado. */
    @Query("""
            SELECT wp FROM WeeklyPrice wp
            JOIN FETCH wp.supermarket s
            JOIN FETCH wp.product
            WHERE wp.promotion = true
              AND wp.validFrom <= :onDate AND wp.validUntil >= :onDate
              AND (:slug IS NULL OR s.slug = :slug)
            ORDER BY (wp.originalPrice - wp.price) DESC
            """)
    List<WeeklyPrice> findCurrentPromotions(
            @Param("onDate") LocalDate onDate,
            @Param("slug") String slug,
            Pageable pageable);
}
