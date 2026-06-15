package com.folhetosmart.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
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
                    "O processamento está a demorar mais do que o esperado. Volta a verificar daqui a pouco."
                )
            )
        }
    }

    // -- forçar sincronização -------------------------------------------------
    fun forceSync() {
        if (_uiState.value.syncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true, statusError = null) }
            try {
                repository.trigger()
                refreshStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusError = "Não foi possível forçar a sincronização.")
                }
            } finally {
                _uiState.update { it.copy(syncing = false) }
            }
        }
    }

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
        private const val MAX_POLLS = 90 // ~3 minutos (extração IA demora 1–2 min)

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                AdminViewModel(app.container.adminRepository, app.container.tokenStore.email)
            }
        }
    }
}
