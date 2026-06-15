package com.folhetosmart.promotions;

import com.folhetosmart.promotions.dto.PromotionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Destaques de promoções da semana. */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /** GET /api/v1/promotions?supermarket=lidl&limit=50 */
    @GetMapping
    public List<PromotionDto> current(
            @RequestParam(required = false) String supermarket,
            @RequestParam(defaultValue = "50") int limit) {
        return promotionService.current(supermarket, limit);
    }
}
