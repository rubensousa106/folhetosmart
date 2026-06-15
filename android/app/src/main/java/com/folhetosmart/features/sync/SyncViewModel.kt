package com.folhetosmart.features.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.SupermarketStatusDto
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.repository.SyncRepository
import com.folhetosmart.data.repository.WeekRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Ecrã Sincronizar (Fix 3). É **só leitura**: lê `GET /api/v1/sync/status` e
 * reflete honestamente o estado do servidor. Nunca afirma "dados atualizados"
 * sem que o processamento tenha mesmo terminado com sucesso, nem oferece "Ver
 * promoções" sem dados reais desta semana na BD. Quando há supermercados a
 * processar, faz polling a cada 2s (com tempo-limite de 10 min).
 */
class SyncViewModel(
    private val repository: SyncRepository,
    private val weekRepository: WeekRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var lastCheckedLabel: String? = null
    private var pollingJob: Job? = null

    init {
        refresh()
    }

    /**
     * Lê o estado atual (SÓ LEITURA — não dispara processamento). Usado na carga
     * inicial e pelo botão "Atualizar estado". Atualiza "Última verificação" e,
     * se algum supermercado estiver a processar, arranca o polling.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val (status, fromCache) = repository.status()
                lastCheckedLabel = nowHHmm()
                applyStatus(status, fromCache)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "Não foi possível verificar os folhetos. Verifica a ligação à internet."
                )
            }
        }
    }

    private fun applyStatus(status: SyncStatusDto, fromCache: Boolean) {
        if (status.totalCount == 0) {
            _uiState.value = SyncUiState.Error("Sem supermercados configurados no servidor.")
            return
        }
        if (status.supermarkets.any { it.syncStatus == "running" }) {
            _uiState.value = processingFrom(status.supermarkets)
            startPolling()
        } else {
            stopPolling()
            _uiState.value = toIdle(status, fromCache)
        }
    }

    /**
     * Upload manual de um folheto em PDF (botão 📎 nos supermercados em erro).
     * Após o envio entra em "A processar…" e segue o progresso por polling.
     */
    fun uploadPdf(slug: String, pdfBytes: ByteArray) {
        viewModelScope.launch {
            try {
                repository.uploadPdf(slug, pdfBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "Não foi possível enviar o PDF. Confirma que é um PDF válido."
                )
                return@launch
            }
            // Otimista: marca o supermercado como "running" e arranca o polling
            // com um período de tolerância para o worker arrancar.
            val markets = currentSupermarkets().map {
                if (it.slug == slug) it.copy(syncStatus = "running", errorMessage = null) else it
            }
            _uiState.value = processingFrom(markets)
            startPolling(graceMs = UPLOAD_GRACE_MS)
        }
    }

    private fun startPolling(graceMs: Long = 0L) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            var sawRunning = graceMs == 0L
            while (true) {
                delay(POLL_INTERVAL_MS)
                val elapsed = System.currentTimeMillis() - startMs
                val status = try {
                    repository.status().data
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // falha pontual de rede: continua a tentar
                }
                if (status != null) {
                    lastCheckedLabel = nowHHmm()
                    val running = status.supermarkets.any { it.syncStatus == "running" }
                    if (running) sawRunning = true
                    // Termina quando já não há nada a processar (e, no caso do
                    // upload, depois de termos visto o running ou de esgotar a
                    // tolerância) — evita "concluir" antes do worker arrancar.
                    if (!running && (sawRunning || elapsed >= graceMs)) {
                        finishProcessing(status)
                        return@launch
                    }
                    _uiState.value = processingFrom(status.supermarkets)
                }
                if (elapsed > TIMEOUT_MS) {
                    _uiState.value = SyncUiState.Error(
                        "⚠️ A sincronização está a demorar mais que o esperado. " +
                            "Verifica a tua ligação e tenta novamente."
                    )
                    return@launch
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun finishProcessing(status: SyncStatusDto) {
        val anySuccess = status.supermarkets.any { it.syncStatus == "success" }
        _uiState.value = if (anySuccess && status.hasCurrentWeekData) {
            SyncUiState.Completed(
                productsMatched = status.totalProductsThisWeek.takeIf { it > 0 }
                    ?: status.lastSync?.productsMatched ?: 0,
                avgSavingsPct = status.lastSync?.avgSavingsPct,
                supermarkets = status.supermarkets
            )
        } else {
            SyncUiState.AllFailed(status.supermarkets)
        }
    }

    /**
     * Pré-carrega os produtos da semana para a cache local (offline no
     * Comparar). NÃO dispara processamento nem chama a Claude API — best-effort.
     */
    fun prefetchWeek() {
        viewModelScope.launch {
            try {
                weekRepository.syncWeekProducts(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // best-effort: o Comparar recarrega na mesma
            }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private fun processingFrom(markets: List<SupermarketStatusDto>): SyncUiState.Processing {
        val total = markets.size
        val done = markets.count { it.syncStatus == "success" || it.syncStatus == "error" }
        val remaining = (total - done).coerceAtLeast(0)
        return SyncUiState.Processing(markets, done, total, remaining * SECONDS_PER_MARKET)
    }

    private fun toIdle(status: SyncStatusDto, fromCache: Boolean): SyncUiState.Idle {
        val lastSync = status.lastSync?.let {
            LastSyncSummary(it.finishedAt, it.productsMatched, it.promotionsFound, it.avgSavingsPct)
        }
        return SyncUiState.Idle(
            supermarkets = status.supermarkets,
            hasCurrentWeekData = status.hasCurrentWeekData,
            totalProductsThisWeek = status.totalProductsThisWeek,
            lastSync = lastSync,
            lastCheckedLabel = lastCheckedLabel,
            offline = fromCache
        )
    }

    private fun currentSupermarkets(): List<SupermarketStatusDto> = when (val s = _uiState.value) {
        is SyncUiState.Idle -> s.supermarkets
        is SyncUiState.Processing -> s.supermarkets
        is SyncUiState.Completed -> s.supermarkets
        is SyncUiState.AllFailed -> s.supermarkets
        else -> emptyList()
    }

    private fun nowHHmm(): String = LocalTime.now().format(HHMM)

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val TIMEOUT_MS = 10 * 60 * 1000L      // 10 minutos
        private const val UPLOAD_GRACE_MS = 20_000L         // espera o worker arrancar
        private const val SECONDS_PER_MARKET = 30
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SyncViewModel(app.container.syncRepository, app.container.weekRepository)
            }
        }
    }
}
