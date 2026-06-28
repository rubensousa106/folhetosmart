package com.folhetosmart.shopping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {

    List<ShoppingItem> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ShoppingItem> findByIdAndUserId(UUID id, UUID userId);

    /** Upsert por oferta: localiza o item único (user, produto, supermercado). */
    Optional<ShoppingItem> findByUserIdAndProdutoAndSupermercado(
            UUID userId, String produto, String supermercado);

    /** Eliminação RGPD / bulk replace: remove todos os itens de um utilizador. */
    void deleteByUserId(UUID userId);
}
