package com.folhetosmart.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folhetosmart.prices.PriceService;
import com.folhetosmart.prices.dto.PriceHistoryPointDto;
import com.folhetosmart.prices.dto.ProductPriceDto;
import com.folhetosmart.products.dto.FlyerOfferingDto;
import com.folhetosmart.products.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    /** Validade no nome do folheto: "... 16/06/2026 - 22/06/2026.pdf". */
    private static final Pattern VALIDADE_RE =
            Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*(\\d{2}/\\d{2}/\\d{4})");

    private final ProductService productService;
    private final PriceService priceService;
    private final LatestProductsRepository latestProductsRepository;
    private final ObjectMapper objectMapper;

    public ProductController(ProductService productService, PriceService priceService,
                             LatestProductsRepository latestProductsRepository,
                             ObjectMapper objectMapper) {
        this.productService = productService;
        this.priceService = priceService;
        this.latestProductsRepository = latestProductsRepository;
        this.objectMapper = objectMapper;
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

    /**
     * GET /api/v1/products/all — TODOS os produtos de TODOS os supermercados num
     * só sítio (modelo simples), cada um com o supermercado e a validade. A app
     * (Comparar) junta os que têm o mesmo nome e destaca o mais barato.
     */
    @GetMapping("/all")
    public List<FlyerOfferingDto> getAllProducts() {
        List<FlyerOfferingDto> offerings = new ArrayList<>();
        for (LatestProducts lp : latestProductsRepository.findAll()) {
            try {
                JsonNode root = objectMapper.readTree(lp.getPayload());
                String supermercado = root.path("supermercado").asText(lp.getSupermarket());
                String validade = parseValidade(lp.getSourceFlyer());
                for (JsonNode item : root.path("produtos")) {
                    String produto = item.path("produto").asText(null);
                    if (produto == null || produto.isBlank()) {
                        continue;
                    }
                    // Nome canónico (se já normalizado) para agrupar; senão o original.
                    String canonico = item.path("canonico").asText(null);
                    String nome = (canonico != null && !canonico.isBlank()) ? canonico : produto;
                    offerings.add(new FlyerOfferingDto(
                            nome, item.path("preco").asDouble(0), supermercado, validade, produto));
                }
            } catch (Exception ignored) {
                // um folheto com payload inválido não trava os outros
            }
        }
        return offerings;
    }

    private static String parseValidade(String sourceFlyer) {
        if (sourceFlyer == null) {
            return null;
        }
        Matcher m = VALIDADE_RE.matcher(sourceFlyer);
        return m.find() ? m.group(1) + " a " + m.group(2) : null;
    }
}
