package com.folhetosmart.shopping;

import com.folhetosmart.auth.User;
import com.folhetosmart.common.NotFoundException;
import com.folhetosmart.shopping.dto.ShoppingItemDto;
import com.folhetosmart.shopping.dto.ShoppingItemRequest;
import com.folhetosmart.shopping.dto.ShoppingSyncRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lista de compras por utilizador, guardada no servidor para sincronizar entre
 * a web e a app. Cada item é uma oferta única por (user, produto, supermercado);
 * o total por supermercado é calculado no cliente (como na app Android).
 */
@Service
public class ShoppingService {

    private final ShoppingItemRepository repository;

    public ShoppingService(ShoppingItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ShoppingItemDto> list(User user) {
        return repository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(ShoppingItemDto::from)
                .toList();
    }

    /**
     * Adiciona ou atualiza uma oferta. Se já existir o mesmo
     * (produto, supermercado) do utilizador, atualiza preço/quantidade — igual
     * ao {@code addOffer} (upsert do Room) na app.
     */
    @Transactional
    public ShoppingItemDto upsert(User user, ShoppingItemRequest request) {
        ShoppingItem item = repository
                .findByUserIdAndProdutoAndSupermercado(
                        user.getId(), request.produto(), request.supermercado())
                .orElseGet(() -> {
                    ShoppingItem novo = new ShoppingItem();
                    novo.setUser(user);
                    novo.setProduto(request.produto());
                    novo.setSupermercado(request.supermercado());
                    return novo;
                });
        item.setPreco(request.preco());
        item.setQuantity(request.quantity() == null ? 1 : request.quantity());
        return ShoppingItemDto.from(repository.save(item));
    }

    /** Ajusta a quantidade (mínimo 1 — espelha o setQuantity da app). */
    @Transactional
    public ShoppingItemDto setQuantity(User user, UUID id, int quantity) {
        ShoppingItem item = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Item não encontrado."));
        item.setQuantity(Math.max(1, quantity));
        return ShoppingItemDto.from(repository.save(item));
    }

    @Transactional
    public void delete(User user, UUID id) {
        ShoppingItem item = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Item não encontrado."));
        repository.delete(item);
    }

    /**
     * Substitui toda a lista do utilizador pelo conjunto enviado (bulk replace).
     * Útil para a app empurrar a lista local na primeira sincronização. O
     * supermercado nulo é coagido para "" porque integra a chave de unicidade.
     */
    @Transactional
    public List<ShoppingItemDto> replaceAll(User user, ShoppingSyncRequest request) {
        repository.deleteByUserId(user.getId());
        for (ShoppingItemRequest r : request.items()) {
            String supermercado = r.supermercado() == null ? "" : r.supermercado();
            upsert(user, new ShoppingItemRequest(
                    r.produto(), supermercado, r.preco(), r.quantity()));
        }
        return list(user);
    }
}
