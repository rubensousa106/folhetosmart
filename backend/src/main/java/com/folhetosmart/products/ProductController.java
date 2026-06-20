package com.folhetosmart.products;

import com.folhetosmart.prices.PriceService;
import com.folhetosmart.prices.dto.PriceHistoryPointDto;
import com.folhetosmart.prices.dto.ProductPriceDto;
import com.folhetosmart.products.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final PriceService priceService;
    private final LatestProductsRepository latestProductsRepository;

    public ProductController(ProductService productService, PriceService priceService,
                             LatestProductsRepository latestProductsRepository) {
        this.productService = productService;
        this.priceService = priceService;
        this.latestProductsRepository = latestProductsRepository;
    }

    /**
     * GET /api/v1/products?search=doritos — pesquisa.
     * GET /api/v1/products?week=current — produtos da semana (cache da app).
     */
    @GetMapping
    public Page<ProductDto> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String week,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if ("current".equalsIgnoreCase(week)) {
            return productService.currentWeek(PageRequest.of(page, Math.min(size, 200)));
        }
        return productService.search(search, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    /** GET /api/v1/products/{id}/prices — preços atuais por supermercado. */
    @GetMapping("/{id}/prices")
    public ResponseEntity<List<ProductPriceDto>> prices(@PathVariable UUID id) {
        productService.getEntity(id); // valida existência -> 404 se não existir
        return ResponseEntity.ok(priceService.currentPrices(id));
    }

    /** GET /api/v1/products/{id}/price-history — histórico (12 semanas). */
    @GetMapping("/{id}/price-history")
    public ResponseEntity<List<PriceHistoryPointDto>> priceHistory(@PathVariable UUID id) {
        productService.getEntity(id);
        return ResponseEntity.ok(priceService.priceHistory(id));
    }
    /** GET /api/v1/products/latest?supermarket=Continente
     * Devolve o JSON mais recente de um supermercado (lido do Google Drive).
     **/
    @GetMapping("/latest")
    public ResponseEntity<String> getLatestProducts(@RequestParam String supermarket) {
        return latestProductsRepository.findById(supermarket.trim().toLowerCase())
                .map(lp -> ResponseEntity.ok(lp.getPayload()))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body("{\"error\": \"Sem produtos disponíveis para " + supermarket + "\"}"));
    }
}
