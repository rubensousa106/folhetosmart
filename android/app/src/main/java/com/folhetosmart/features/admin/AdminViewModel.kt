package com.folhetosmart.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.SupermarketStatusDto
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.repository.AdminRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Painel de administração (Fix 5): carrega o estado dos folhetos, faz upload de
 * um PDF para o Drive e acompanha a extração com IA via polling do sync_run.
 */
class AdminViewModel(
    private val repository: AdminRepository,
    adminEmail: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState(adminEmail = adminEmail, statusLoading = true))
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    // -- estado dos folhetos --------------------------------------------------
    fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusLoading = true, statusError = null) }
            try {
                refreshStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusLoading = false,
                        statusError = "Não foi possível carregar o estado dos folhetos."
                    )
                }
            }
        }
    }

    private suspend fun refreshStatus() {
        val status = repository.flyersStatus()
        _uiState.update {
            it.copy(
                statusLoading = false,
                statusError = null,
                week = status.week,
                supermarkets = status.supermarkets,
                // Pré-seleciona o primeiro supermercado enquanto nenhum estiver escolhido.
                selectedSlug = it.selectedSlug ?: status.supermarkets.firstOrNull()?.slug
            )
        }
    }

    // -- formulário -----------------------------------------------------------
    fun selectSupermarket(slug: String) = _uiState.update { it.copy(selectedSlug = slug) }

    fun previousWeek() = _uiState.update { it.copy(weekStart = it.weekStart.minusWeeks(1)) }

    fun nextWeek() = _uiState.update { it.copy(weekStart = it.weekStart.plusWeeks(1)) }

    fun onPdfPicked(label: String, bytes: ByteArray) = _uiState.update {
        it.copy(picked = PickedPdf(label, bytes), phase = UploadPhase.Selected)
    }

    /** Reverte um erro para o estado anterior ("Tentar novamente"). */
    fun retry() = _uiState.update {
        it.copy(phase = if (it.picked != null) UploadPhase.Selected else UploadPhase.Idle)
    }

    // -- upload ---------------------------------------------------------------
    fun upload() {
        val s = _uiState.value
        val slug = s.selectedSlug ?: return
        val picked = s.picked ?: return
        if (s.isBusy) return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = UploadPhase.Uploading) }
            try {
                val resp = repository.uploadFlyer(slug, s.validFrom, s.validUntil, picked.bytes)
                _uiState.update { it.copy(phase = UploadPhase.Processing) }
                pollRun(resp.syncRunId, slug)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = UploadPhase.Error(humanize(e))) }
            }
        }
    }

    private suspend fun pollRun(runId: String, slug: String) {
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            val run = try {
                repository.run(runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // falha pontual de rede: continua a tentar
            }
            when (run?.status) {
                "done", "error" -> {
                    // Re-busca o estado fresco para atualizar a lista e obter o
                    // nº exato de produtos importados deste supermercado.
                    val fresh = try {
                        repository.flyersStatus()
                    } catch (e: Exception) {
                        null
                    }
                    val imported = fresh?.supermarkets
                        ?.firstOrNull { it.slug == slug }?.productsImported
                        ?: run.productsMatched
                    _uiState.update {
                        val base = it.copy(
                            week = fresh?.week ?: it.week,
                            supermarkets = fresh?.supermarkets ?: it.supermarkets
                        )
                        if (run.status == "error" && imported == 0) {
                            base.copy(
                                phase = UploadPhase.Error(
                                    run.errorMessage ?: "A extração falhou. Tenta novamente."
                                )
                            )
                        } else {
                            // Sucesso (ou run em erro mas com produtos já importados).
                            base.copy(phase = UploadPhase.Done(imported), picked = null)
                        }
                    }
                    return
                }

                else -> Unit // continua em "A processar…"
            }
        }
        _uiState.update {
            it.copy(
                phase = UploadPhase.Error(
                    "⚠️ Timeout no processamento de ${marketName(slug)}. " +
                        "O PDF pode ser muito grande ou a IA está lenta. " +
                        "Tenta novamente ou verifica o PDF."
                )
            )
        }
    }

    // -- forçar sincronização -------------------------------------------------
    fun forceSync() {
        if (_uiState.value.syncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true, statusError = null, syncProgress = null) }
            try {
                repository.trigger()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(syncing = false, statusError = "Não foi possível forçar a sincronização.")
                }
                return@launch
            }
            pollSyncProgress()
        }
    }

    /**
     * Acompanha o processamento multi-supermercado (GET /sync/status) com a
     * barra de progresso detalhada. Tempo-limite de segurança de 2 minutos POR
     * supermercado (a Claude API demora ~30-60s por PDF).
     */
    private suspend fun pollSyncProgress() {
        val startMs = System.currentTimeMillis()
        while (true) {
            val status: SyncStatusDto? = try {
                repository.syncStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (status != null) {
                val markets = status.supermarkets
                _uiState.update { it.copy(syncProgress = progressFrom(markets)) }

                val pending = markets.any { it.syncStatus == "running" || it.syncStatus == "pending" }
                if (!pending) {
                    _uiState.update { it.copy(syncing = false, syncProgress = null) }
                    try {
                        refreshStatus()
                    } catch (e: Exception) {
                        // mantém o último estado conhecido
                    }
                    return
                }

                val budgetMs = markets.size.coerceAtLeast(1) * PER_MARKET_TIMEOUT_MS
                if (System.currentTimeMillis() - startMs > budgetMs) {
                    val stuck = markets.firstOrNull {
                        it.syncStatus == "running" || it.syncStatus == "pending"
                    }?.name ?: "um supermercado"
                    _uiState.update {
                        it.copy(
                            syncing = false,
                            syncProgress = null,
                            statusError = "⚠️ Timeout no processamento de $stuck. " +
                                "O PDF pode ser muito grande ou a IA está lenta. " +
                                "Tenta novamente ou verifica o PDF."
                        )
                    }
                    return
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun progressFrom(markets: List<SupermarketStatusDto>): SyncProgress {
        val total = markets.size
        val done = markets.count { it.syncStatus == "success" || it.syncStatus == "error" }
        val remaining = (total - done).coerceAtLeast(0)
        val lines = markets.map { m ->
            when (m.syncStatus) {
                "success" -> MarketLine(m.name, "${m.productsImported} produtos", done = true, failed = false)
                "error" -> MarketLine(m.name, "Falhou", done = true, failed = true)
                "running" -> MarketLine(m.name, "A processar com IA…", done = false, failed = false)
                else -> MarketLine(m.name, "A aguardar…", done = false, failed = false)
            }
        }
        return SyncProgress(done, total, remaining * SECONDS_PER_MARKET, lines)
    }

    /** Nome do supermercado a partir do slug (para mensagens de timeout). */
    private fun marketName(slug: String): String =
        _uiState.value.supermarkets.firstOrNull { it.slug == slug }?.name ?: "este supermercado"

    private fun humanize(e: Exception): String = when (e) {
        is HttpException -> when (e.code()) {
            403 -> "Sem permissão — apenas administradores podem carregar folhetos."
            400 -> "PDF inválido ou datas incorretas."
            413 -> "O ficheiro é demasiado grande."
            429 -> "Demasiados pedidos. Aguarda um momento."
            else -> "Falha no envio (erro ${e.code()})."
        }
        else -> "Não foi possível enviar o folheto. Verifica a ligação."
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        // Tempo-limite de 2 min por supermercado (a IA demora ~30-60s por PDF).
        private const val PER_MARKET_TIMEOUT_MS = 2 * 60 * 1000L
        private const val MAX_POLLS = 60 // upload de 1 supermercado: ~2 minutos
        private const val SECONDS_PER_MARKET = 45

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                AdminViewModel(app.container.adminRepository, app.container.tokenStore.email)
            }
        }
    }
}
