package com.folhetosmart.products;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folhetosmart.prices.PriceService;
import com.folhetosmart.prices.dto.PriceHistoryPointDto;
import com.folhetosmart.prices.dto.ProductPriceDto;
import com.folhetosmart.products.dto.FlyerOfferingDto;
import com.folhetosmart.products.dto.ProductDto;
import com.folhetosmart.storage.R2Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    /** Datas no nome do folheto: 16/06/2026, 16-06-2026, 19062026 (DDMMYYYY) ou 220626 (DDMMYY). */
    private static final Pattern DATE_TOKEN =
            Pattern.compile("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{8}|\\d{6}");

    private final ProductService productService;
    private final PriceService priceService;
    private final LatestProductsRepository latestProductsRepository;
    private final ObjectMapper objectMapper;
    private final R2Service r2Service;  // ← ADICIONADO

    public ProductController(ProductService productService, PriceService priceService,
                             LatestProductsRepository latestProductsRepository,
                             ObjectMapper objectMapper,
                             R2Service r2Service) {  // ← ADICIONADO
        this.productService = productService;
        this.priceService = priceService;
        this.latestProductsRepository = latestProductsRepository;
        this.objectMapper = objectMapper;
        this.r2Service = r2Service;  // ← ADICIONADO
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
    public ResponseEntity<?> getAllProducts() {
        // Se houver feed publicado no R2, redireciona a app para o link assinado
        // (download rápido/privado fora do Render; a app segue o redirect).
        var feed = latestProductsRepository.findById("__feed_url__");
        if (feed.isPresent() && feed.get().getPayload() != null && !feed.get().getPayload().isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, feed.get().getPayload()).build();
        }
        // Fallback: constrói da BD (enquanto não houver feed no R2).
        List<FlyerOfferingDto> offerings = new ArrayList<>();
        for (LatestProducts lp : latestProductsRepository.findAll()) {
            if ("__feed_url__".equals(lp.getSupermarket())) {
                continue;
            }
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
        return ResponseEntity.ok(offerings);
    }

    /**
     * GET /api/v1/products/feeds — lista os links assinados dos feeds ATIVOS no R2
     * (multi-feed: supermercados principais + Aldi com datas próprias). A app
     * descarrega todos e funde-os. Ignora feeds já totalmente expirados.
     */
    @GetMapping("/feeds")
    public List<String> feeds() {
        LocalDate today = LocalDate.now();
        List<String> urls = new ArrayList<>();
        for (LatestProducts lp : latestProductsRepository.findAll()) {
            String key = lp.getSupermarket();
            if (key == null || !key.startsWith("__feed_url__")) {
                continue;
            }
            String url = lp.getPayload();
            if (url == null || url.isBlank() || feedExpired(lp.getSourceFlyer(), today)) {
                continue;
            }
            urls.add(url);
        }
        return urls;
    }

    /**
     * GET /api/v1/products/feeds/r2 — lista os URLs dos ficheiros produtos_*.json
     * disponíveis no Cloudflare R2.
     */
    @GetMapping("/feeds/r2")
    public ResponseEntity<List<String>> getAvailableFeeds() {
        List<String> files = r2Service.listFiles("produtos_*.json");
        return ResponseEntity.ok(files);
    }

    /** True se a data de fim (DD-MM-AAAA, guardada no source_flyer) já passou. */
    private static boolean feedExpired(String validUntil, LocalDate today) {
        if (validUntil == null || validUntil.isBlank()) {
            return false; // sem data -> assume válido
        }
        try {
            String[] p = validUntil.trim().split("-");
            LocalDate end = LocalDate.of(
                    Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            return end.isBefore(today);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String parseValidade(String sourceFlyer) {
        if (sourceFlyer == null) {
            return null;
        }
        Matcher m = DATE_TOKEN.matcher(sourceFlyer);
        List<String> datas = new ArrayList<>();
        while (m.find() && datas.size() < 2) {
            String d = normalizeDate(m.group());
            if (d != null) {
                datas.add(d);
            }
        }
        return datas.size() == 2 ? datas.get(0) + " a " + datas.get(1) : null;
    }

    /** Normaliza um token de data para "DD/MM/AAAA" (aceita /, -, DDMMYYYY, DDMMYY). */
    private static String normalizeDate(String token) {
        try {
            if (token.contains("/") || token.contains("-")) {
                String[] p = token.split("[/-]");
                return String.format("%02d/%02d/%04d",
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            if (token.length() == 8) {
                return token.substring(0, 2) + "/" + token.substring(2, 4) + "/" + token.substring(4, 8);
            }
            if (token.length() == 6) {
                return token.substring(0, 2) + "/" + token.substring(2, 4) + "/20" + token.substring(4, 6);
            }
        } catch (Exception ignored) {
            // token que não é data válida
        }
        return null;
    }
}
