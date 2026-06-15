package com.folhetosmart.shoppinglist;

import com.folhetosmart.shoppinglist.dto.OptimizeRequest;
import com.folhetosmart.shoppinglist.dto.OptimizeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shopping-list")
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        return shoppingListService.optimize(request);
    }
}
