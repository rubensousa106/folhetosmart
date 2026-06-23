package com.folhetosmart.sync;

import com.folhetosmart.sync.dto.SyncRunDto;
import com.folhetosmart.sync.dto.SyncStatusResponse;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** GET /api/v1/sync/runs/{id} — polling do progresso de uma sincronização. */
    @GetMapping("/runs/{id}")
    public SyncRunDto run(@PathVariable UUID id) {
        return syncService.getRun(id);
    }

    /**
     * PUT /api/v1/sync/flyers/{slug} — marca um folheto como disponível.
     * Só ADMIN (verificador de disponibilidade de folhetos).
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
