package com.folhetosmart.shopping;

import com.folhetosmart.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item da lista de compras de um utilizador: uma OFERTA específica
 * (produto + supermercado + preço + quantidade). A unicidade por
 * (user, produto, supermercado) espelha a chave composta usada no Room da app
 * Android ({@code "<produto>::<supermercado>"}), permitindo sincronizar a Lista
 * entre a web e o telemóvel.
 */
@Entity
@Table(name = "shopping_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_shopping_items_user_produto_super",
                columnNames = {"user_id", "produto", "supermercado"}))
@Getter
@Setter
@NoArgsConstructor
public class ShoppingItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Nome do produto (= displayName no Room). */
    @Column(nullable = false)
    private String produto;

    /** Supermercado da oferta (parte da chave de unicidade — nunca nulo). */
    @Column(nullable = false)
    private String supermercado;

    @Column(nullable = false)
    private BigDecimal preco;

    @Column(nullable = false)
    private int quantity = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
