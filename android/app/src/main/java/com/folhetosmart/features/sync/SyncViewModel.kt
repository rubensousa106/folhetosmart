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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Ecrã Sincronizar (USER e ADMIN). A lista de supermercados está sempre
 * visível. "Verificar agora" é um único GET de leitura a `/api/v1/sync/status`
 * (sem polling, sem processamento) com tempo-limite de 30s. O ADMIN tem, por
 * baixo, uma área de administração para upload de folhetos.
 */
class SyncViewModel(
    private val repository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        verify()
    }

    /** Botão "Verificar agora" — lê o estado da BD (30s máx). NÃO processa nada. */
    fun verify() {
        viewModelScope.launch {
            // A lista nunca desaparece: se já há conteúdo, mostra a barra no topo.
            (_uiState.value as? SyncUiState.Content)?.let {
                _uiState.value = it.copy(checking = true, errorMessage = null)
            }
            try {
                val (status, fromCache) = withTimeout(TIMEOUT_MS) { repository.status() }
                _uiState.value = toContent(status, fromCache)
            } catch (e: TimeoutCancellationException) {
                onCheckFailure("⚠️ Sem resposta. Verifica a tua ligação e tenta novamente.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onCheckFailure("⚠️ Sem resposta. Verifica a tua ligação e tenta novamente.")
            }
        }
    }

    private fun onCheckFailure(message: String) {
        _uiState.value = when (val s = _uiState.value) {
            // Mantém a lista visível; mostra só o aviso.
            is SyncUiState.Content -> s.copy(checking = false, errorMessage = message)
            else -> SyncUiState.Error(message)
        }
    }

    private fun toContent(status: SyncStatusDto, fromCache: Boolean): SyncUiState.Content =
        SyncUiState.Content(
            supermarkets = status.supermarkets,
            hasData = status.hasCurrentWeekData || status.totalProductsThisWeek > 0,
            validityLabel = currentWeekValidity(),
            lastCheckedLabel = LocalTime.now().format(HHMM),
            checking = false,
            errorMessage = null,
            offline = fromCache
        )

    private fun currentWeekValidity(): String {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        return "${monday.format(DM)} a ${sunday.format(DMY)}"
    }

    companion object {
        private const val TIMEOUT_MS = 30_000L   // USER: leitura < 3s; corta aos 30s
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        private val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SyncViewModel(app.container.syncRepository)
            }
        }
    }
}
