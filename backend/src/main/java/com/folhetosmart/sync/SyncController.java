package com.folhetosmart.sync;

import com.folhetosmart.sync.dto.SyncRunDto;
import com.folhetosmart.sync.dto.SyncStatusResponse;
import com.folhetosmart.sync.dto.SyncTriggerResponse;
import com.folhetosmart.sync.dto.SyncUploadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /** GET /api/v1/sync/status — estado dos folhetos + última sincronização. */
    @GetMapping("/status")
    public SyncStatusResponse status() {
        return syncService.getStatus();
    }

    /**
     * POST /api/v1/sync/trigger — dispara scraping + IA matching.
     * Só ADMIN: o processamento (Claude API) corre 1× por semana no servidor,
     * nunca por utilizador final (evita custos multiplicados).
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncTriggerResponse> trigger() {
        return ResponseEntity.accepted().body(syncService.trigger("admin"));
    }

    /** GET /api/v1/sync/runs/{id} — polling do progresso de uma sincronização. */
    @GetMapping("/runs/{id}")
    public SyncRunDto run(@PathVariable UUID id) {
        return syncService.getRun(id);
    }

    /**
     * POST /api/v1/sync/upload/{slug} — upload manual de um folheto em PDF
     * (Fix 3). multipart/form-data com campo "file".
     */
    @PostMapping(value = "/upload/{slug}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncUploadResponse> upload(
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(syncService.uploadPdf(slug, file));
    }

    /**
     * PUT /api/v1/sync/flyers/{slug} — marca um folheto como disponível.
     * Só ADMIN (representa o verificador de disponibilidade / callback do worker).
     */
    @PutMapping("/flyers/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> setFlyerAvailability(
            @PathVariable String slug,
            @RequestBody FlyerAvailabilityRequest body) {
        syncService.setFlyerAvailability(slug, body.available());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    public record FlyerAvailabilityRequest(boolean available) {
    }
}
