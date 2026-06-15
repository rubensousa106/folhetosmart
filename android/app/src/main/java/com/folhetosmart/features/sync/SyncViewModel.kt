package com.folhetosmart.features.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.repository.SyncRepository
import com.folhetosmart.data.repository.WeekRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncViewModel(
    private val repository: SyncRepository,
    private val weekRepository: WeekRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Recarrega o estado dos folhetos. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Loading
            try {
                val (status, fromCache) = repository.status()
                _uiState.value = toIdleState(status, fromCache)
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "Não foi possível verificar os folhetos. Verifica a ligação à internet."
                )
            }
        }
    }

    /**
     * Sincronização do utilizador = só LEITURA (Fix 3). Não dispara
     * processamento nem chama a Claude API (isso corre 1×/semana no servidor).
     * Lê o estado dos folhetos e descarrega os produtos da semana para a cache.
     */
    fun startSync() {
        val supermarkets = when (val s = _uiState.value) {
            is SyncUiState.WaitingForFlyers -> s.supermarkets
            is SyncUiState.ReadyToSync -> s.supermarkets
            is SyncUiState.Done -> s.supermarkets
            else -> emptyList()
        }
        viewModelScope.launch {
            _uiState.value = SyncUiState.Syncing(SyncProgress(0), supermarkets)
            try {
                val status = repository.status().data
                val products = weekRepository.syncWeekProducts(force = true)
                _uiState.value = SyncUiState.Done(
                    SyncResult(
                        productsMatched = products.size,
                        promotionsFound = status.lastSync?.promotionsFound ?: 0,
                        avgSavingsPct = status.lastSync?.avgSavingsPct,
                        finishedAt = status.lastSync?.finishedAt
                    ),
                    status.supermarkets
                )
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "Não foi possível atualizar os dados. Verifica a ligação."
                )
            }
        }
    }

    /** Upload manual de um folheto em PDF (Fix 3) — entra em "A processar…". */
    fun uploadPdf(slug: String, pdfBytes: ByteArray) {
        val supermarkets = when (val s = _uiState.value) {
            is SyncUiState.WaitingForFlyers -> s.supermarkets
            is SyncUiState.ReadyToSync -> s.supermarkets
            is SyncUiState.Done -> s.supermarkets
            else -> emptyList()
        }
        viewModelScope.launch {
            _uiState.value = SyncUiState.Syncing(SyncProgress(0), supermarkets)
            try {
                val upload = repository.uploadPdf(slug, pdfBytes)
                pollRun(upload.syncRunId, supermarkets)
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "Não foi possível enviar o PDF. Confirma que é um PDF válido."
                )
            }
        }
    }

    private suspend fun pollRun(
        runId: String,
        supermarkets: List<com.folhetosmart.data.api.SupermarketStatusDto>
    ) {
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            val run = try {
                repository.run(runId)
            } catch (e: Exception) {
                null // falha pontual de rede: continua a tentar
            }
            when (run?.status) {
                // "done" e "error" (do run) terminam a sincronização. Em ambos
                // re-buscamos o status fresco para refletir o estado individual
                // de cada supermercado (success/error com botão de upload) e o
                // resumo. O run pode ser "error" mesmo com supermercados a
                // sincronizar com sucesso — daí mostrarmos sempre a lista.
                "done", "error" -> {
                    val fresh = try {
                        repository.status().data
                    } catch (e: Exception) {
                        null
                    }
                    val summary = fresh?.lastSync
                    _uiState.value = SyncUiState.Done(
                        SyncResult(
                            productsMatched = summary?.productsMatched ?: run.productsMatched,
                            promotionsFound = summary?.promotionsFound ?: 0,
                            avgSavingsPct = summary?.avgSavingsPct,
                            finishedAt = summary?.finishedAt ?: run.finishedAt
                        ),
                        fresh?.supermarkets ?: supermarkets
                    )
                    return
                }

                else -> _uiState.value = SyncUiState.Syncing(
                    SyncProgress(run?.productsMatched ?: 0),
                    supermarkets
                )
            }
        }
        _uiState.value = SyncUiState.Error(
            "A sincronização está a demorar mais do que o esperado. Volta a verificar daqui a pouco."
        )
    }

    private fun toIdleState(status: SyncStatusDto, fromCache: Boolean): SyncUiState {
        if (status.totalCount == 0) {
            return SyncUiState.Error("Sem supermercados configurados no servidor.")
        }
        val lastSync = status.lastSync?.let {
            LastSyncSummary(it.finishedAt, it.productsMatched, it.promotionsFound, it.avgSavingsPct)
        }
        return if (status.allReady) {
            SyncUiState.ReadyToSync(status.supermarkets, lastSync)
        } else {
            SyncUiState.WaitingForFlyers(
                ready = status.readyCount,
                total = status.totalCount,
                supermarkets = status.supermarkets,
                lastSync = lastSync,
                offline = fromCache
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_POLLS = 150        // ~5 minutos

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SyncViewModel(app.container.syncRepository, app.container.weekRepository)
            }
        }
    }
}
