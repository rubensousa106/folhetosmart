package com.folhetosmart.sync;

import com.folhetosmart.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Cron do automatizador (Spring Scheduler): às quintas de manhã verifica se
 * todos os folhetos já estão disponíveis e, se sim, dispara a sincronização
 * uma vez por dia (triggered_by = "cron"). O utilizador continua a poder
 * disparar manualmente pela app.
 *
 * Desativável por {@code folheto.scheduler.enabled=false}: no Render (free) o
 * backend não tem worker, e o processamento semanal corre no GitHub Actions.
 */
@Component
@ConditionalOnProperty(name = "folheto.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SyncSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerJob.class);
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    private final SyncService syncService;
    private final SyncRunRepository syncRunRepository;

    public SyncSchedulerJob(SyncService syncService, SyncRunRepository syncRunRepository) {
        this.syncService = syncService;
        this.syncRunRepository = syncRunRepository;
    }

    /** Quinta-feira às 10:00 (hora de Lisboa) — tenta site, depois Drive. */
    @Scheduled(cron = "0 0 10 * * THU", zone = "Europe/Lisbon")
    public void autoSyncThursday() {
        if (alreadyTriggeredToday()) {
            return;
        }
        // Dispara sempre: o worker tenta o site e, em falha, o Google Drive,
        // por cada supermercado. Os que ficarem sem folheto geram aviso ao admin.
        try {
            var run = syncService.trigger("cron");
            log.info("Cron: sincronização semanal disparada (sync_run={})", run.syncRunId());
        } catch (ApiException ex) {
            log.warn("Cron: não foi possível disparar a sincronização: {}", ex.getMessage());
        }
    }

    private boolean alreadyTriggeredToday() {
        return syncRunRepository.findTopByOrderByStartedAtDesc()
                .filter(run -> run.getStartedAt() != null
                        && !"error".equals(run.getStatus()))
                .map(run -> run.getStartedAt().atZone(LISBON).toLocalDate()
                        .equals(LocalDate.now(LISBON)))
                .orElse(false);
    }
}
