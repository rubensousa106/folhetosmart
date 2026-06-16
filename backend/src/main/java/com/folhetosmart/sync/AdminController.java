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

    public AdminController(AdminService adminService, SyncService syncService) {
        this.adminService = adminService;
        this.syncService = syncService;
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
}
