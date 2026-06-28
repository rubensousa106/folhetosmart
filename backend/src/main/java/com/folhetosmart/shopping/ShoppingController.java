package com.folhetosmart.shopping;

import com.folhetosmart.auth.User;
import com.folhetosmart.shopping.dto.QuantityRequest;
import com.folhetosmart.shopping.dto.ShoppingItemDto;
import com.folhetosmart.shopping.dto.ShoppingItemRequest;
import com.folhetosmart.shopping.dto.ShoppingSyncRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lista de compras do utilizador (requer JWT). Sincroniza entre a web e a app:
 * o que é adicionado num lado fica disponível na conta do outro.
 */
@RestController
@RequestMapping("/api/v1/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @GetMapping
    public List<ShoppingItemDto> list(@AuthenticationPrincipal User user) {
        return shoppingService.list(user);
    }

    @PostMapping
    public ShoppingItemDto upsert(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ShoppingItemRequest request) {
        return shoppingService.upsert(user, request);
    }

    @PatchMapping("/{id}/quantity")
    public ShoppingItemDto setQuantity(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody QuantityRequest request) {
        return shoppingService.setQuantity(user, id, request.quantity());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        shoppingService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    public List<ShoppingItemDto> replaceAll(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ShoppingSyncRequest request) {
        return shoppingService.replaceAll(user, request);
    }
}
