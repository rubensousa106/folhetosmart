package com.folhetosmart.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.folhetosmart.sync.dto.AdminFlyersStatusResponse;
import com.folhetosmart.sync.dto.AdminProcessFlyerRequest;
import com.folhetosmart.sync.dto.AdminProcessFlyerResponse;
import com.folhetosmart.sync.dto.AdminUploadResponse;
import com.folhetosmart.sync.dto.SyncTriggerResponse;

/**
 * Painel de administração. Todos os endpoints exigem role ADMIN — qualquer
 * outro utilizador recebe 403 (a verificação é feita pelo @PreAuthorize de
 * classe, garantido pelo @EnableMethodSecurity).
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final SyncService syncService;
    private final R2Service r2Service;

    public AdminController(AdminService adminService, SyncService syncService, R2Service r2Service) {
        this.adminService = adminService;
        this.syncService = syncService;
        this.r2Service = r2Service;
    }

    /**
     * POST /api/v1/admin/upload-flyer — upload de um folheto PDF para o Drive +
     * extração com IA. multipart/form-data: supermarket_slug, valid_from,
     * valid_until (DD-MM-YYYY) e file.
     */
    @PostMapping(value = "/upload-flyer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminUploadResponse> uploadFlyer(
            @RequestParam("supermarket_slug") String supermarketSlug,
            @RequestParam("valid_from") String validFrom,
            @RequestParam("valid_until") String validUntil,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted()
                .body(adminService.uploadFlyer(supermarketSlug, validFrom, validUntil, file));
    }

    /**
     * POST /api/v1/admin/process-flyer — passo 2 (SÍNCRONO, ~1-2 min):
     * descarrega o PDF do Drive, extrai com a Claude API e persiste.
     */
    @PostMapping("/process-flyer")
    public ResponseEntity<AdminProcessFlyerResponse> processFlyer(
            @RequestBody AdminProcessFlyerRequest body) {
        return ResponseEntity.ok(adminService.processFlyer(
                body.supermarketSlug(), body.validFrom(), body.validUntil(), body.driveFileId()));
    }

    /** GET /api/v1/admin/flyers/status — estado dos folhetos da semana atual. */
    @GetMapping("/flyers/status")
    public AdminFlyersStatusResponse flyersStatus() {
        return adminService.flyersStatus();
    }

    /** POST /api/v1/admin/sync/trigger — força a sincronização (site -> Drive). */
    @PostMapping("/sync/trigger")
    public ResponseEntity<SyncTriggerResponse> trigger() {
        return ResponseEntity.accepted().body(syncService.trigger("admin"));
    }

    /**
     * POST /api/v1/admin/products?supermarket=Continente&count=557&flyer=...
     * Corpo = JSON {supermercado, produtos:[...]} produzido pelo scraper. Guarda
     * o folheto mais recente desse supermercado (a app lê-o em
     * GET /api/v1/products/latest) e regista o nome do folheto (flag "analisar 1×").
     */
    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> uploadProducts(
            @RequestParam("supermarket") String supermarket,
            @RequestParam(value = "count", defaultValue = "0") int count,
            @RequestParam(value = "flyer", required = false) String flyer,
            @RequestBody String payload) {
        adminService.saveLatestProducts(supermarket, count, flyer, payload);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/admin/products/source?supermarket=Continente
     * Devolve o nome do folheto já analisado (ou ""). O produtor consulta-o ANTES
     * de gastar IA: se for o mesmo folheto, não reextrai (flag "analisar 1×").
     */
    @GetMapping("/products/source")
    public ResponseEntity<Map<String, String>> productsSource(@RequestParam("supermarket") String supermarket) {
        return ResponseEntity.ok(Map.of("flyer", adminService.currentSourceFlyer(supermarket)));
    }

    /**
     * POST /api/v1/admin/feed-url — o produtor envia o link assinado do R2 onde
     * está o feed normalizado. O GET /api/v1/products/all passa a redirecionar a
     * app para esse link (download rápido e privado, fora do Render).
     */
    @PostMapping(value = "/feed-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setFeedUrl(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "").trim();
        adminService.saveLatestProducts("__feed_url__", 0, null, url);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/flyer-upload-url?supermarket=Continente&valid_from=16-06-2026&valid_until=22-06-2026
     * Devolve um link assinado para a app fazer PUT do PDF do folheto no R2, com
     * o nome no padrão "{Supermercado} {DD-MM-AAAA} - {DD-MM-AAAA}.pdf".
     */
    @PostMapping("/flyer-upload-url")
    public ResponseEntity<Map<String, String>> flyerUploadUrl(
            @RequestParam("supermarket") String supermarket,
            @RequestParam("valid_from") String validFrom,
            @RequestParam("valid_until") String validUntil) {
        if (!r2Service.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "R2 não configurado no servidor."));
        }
        String filename = supermarket.trim() + " " + validFrom + " - " + validUntil + ".pdf";
        return ResponseEntity.ok(Map.of(
                "url", r2Service.presignFlyerPut(filename),
                "filename", filename));
    }
}
