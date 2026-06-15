package com.folhetosmart.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PriceAlert> findByIdAndUserId(UUID id, UUID userId);

    /** Alertas ativos de um produto (usado pela avaliação de preços/FCM). */
    List<PriceAlert> findByProductIdAndActiveTrue(UUID productId);

    /** Eliminação RGPD: remove todos os alertas de um utilizador. */
    void deleteByUserId(UUID userId);
}
