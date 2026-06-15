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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Ecrã Sincronizar do USER (a maioria dos utilizadores). Lê dados JÁ
 * processados (`GET /api/v1/sync/status`) num único pedido instantâneo — sem
 * polling, sem barra de supermercados, com tempo-limite de 15s. Em paralelo
 * guarda os produtos da semana na cache Room para o Comparar.
 *
 * O processamento pesado (Claude API) e o respetivo progresso vivem APENAS no
 * ecrã Admin (AdminViewModel).
 */
class SyncViewModel(
    private val repository: SyncRepository,
    private val weekRepository: WeekRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Carga automática: respeita a cache de 1h dos produtos.
        refresh(manual = false)
    }

    /** Botão "Atualizar"/"Verificar agora" — força a atualização. */
    fun refresh() = refresh(manual = true)

    /** Um GET ao estado (com tempo-limite de 15s) + cache dos produtos. */
    private fun refresh(manual: Boolean) {
        viewModelScope.launch {
            if (_uiState.value !is SyncUiState.Ready) _uiState.value = SyncUiState.Loading
            try {
                val (status, fromCache) = withTimeout(REQUEST_TIMEOUT_MS) { repository.status() }
                _uiState.value = toReady(status, fromCache)
                prefetchWeek(force = manual)   // produtos para o Comparar
            } catch (e: TimeoutCancellationException) {
                _uiState.value = SyncUiState.Error(
                    "⚠️ Sem resposta do servidor. Verifica a tua ligação."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error(
                    "⚠️ Sem resposta do servidor. Verifica a tua ligação."
                )
            }
        }
    }

    private fun toReady(status: SyncStatusDto, fromCache: Boolean): SyncUiState.Ready {
        val withData = status.supermarkets.count { it.productsImported > 0 }
        return SyncUiState.Ready(
            hasData = status.hasCurrentWeekData || status.totalProductsThisWeek > 0,
            totalProducts = status.totalProductsThisWeek,
            supermarketsWithData = withData,
            lastUpdateIso = status.lastSync?.finishedAt,
            validityLabel = currentWeekValidity(),
            offline = fromCache
        )
    }

    /** Pré-carrega os produtos da semana na cache Room (best-effort). */
    private fun prefetchWeek(force: Boolean) {
        viewModelScope.launch {
            try {
                weekRepository.syncWeekProducts(force = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // best-effort: o Comparar recarrega na mesma
            }
        }
    }

    private fun currentWeekValidity(): String {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        return "${monday.format(DM)} a ${sunday.format(DMY)}"
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 15_000L   // USER: leitura < 3s; corta aos 15s
        private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        private val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                SyncViewModel(app.container.syncRepository, app.container.weekRepository)
            }
        }
    }
}
