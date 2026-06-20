package com.folhetosmart.features.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.SyncStatusDto
import com.folhetosmart.data.models.Product
import com.folhetosmart.data.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 *
 * Também suporta o download de produtos para cada supermercado.
 */
class SyncViewModel(
    private val repository: SyncRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Loading)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    // Eventos one-shot (Snackbar de "Verificar agora") — só em verificação manual.
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    // ============================================================
    // PRODUTOS POR SUPERMERCADO
    // ============================================================
    private val _productsMap = MutableStateFlow<Map<String, List<Product>>>(emptyMap())
    val productsMap: StateFlow<Map<String, List<Product>>> = _productsMap.asStateFlow()

    private val _loadingSupermarkets = MutableStateFlow<Set<String>>(emptySet())
    val loadingSupermarkets: StateFlow<Set<String>> = _loadingSupermarkets.asStateFlow()

    init {
        // Carga automática inicial — sem Snackbar.
        verify(manual = false)
    }

    /** Botão "Verificar agora" — lê o estado da BD (30s máx). NÃO processa nada. */
    fun verify() = verify(manual = true)

    private fun verify(manual: Boolean) {
        viewModelScope.launch {
            // A lista nunca desaparece: se já há conteúdo, mostra a barra no topo.
            (_uiState.value as? SyncUiState.Content)?.let {
                _uiState.value = it.copy(checking = true, errorMessage = null)
            }
            try {
                val (status, fromCache) = withTimeout(TIMEOUT_MS) { repository.status() }
                val content = toContent(status, fromCache)
                _uiState.value = content
                if (manual) _events.tryEmit(SyncEvent.Checked(content.hasData))
            } catch (e: TimeoutCancellationException) {
                onCheckFailure("⚠️ Sem resposta. Verifica a tua ligação e tenta novamente.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onCheckFailure("⚠️ Sem resposta. Verifica a tua ligação e tenta novamente.")
            }
        }
    }

    /**
     * Descarrega os produtos de um supermercado específico.
     * @param supermarket Nome do supermercado (ex: "Continente", "Pingo Doce", etc.)
     */
    fun downloadProducts(supermarket: String) {
        viewModelScope.launch {
            // Marca este supermercado como "a carregar"
            _loadingSupermarkets.value = _loadingSupermarkets.value + supermarket

            try {
                val response = apiService.getLatestProducts(supermarket)
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null && data.produtos.isNotEmpty()) {
                        // Guarda os produtos no mapa
                        _productsMap.value = _productsMap.value + (supermarket to data.produtos)
                        _events.tryEmit(SyncEvent.ProductsLoaded(supermarket, data.produtos))
                    } else {
                        _events.tryEmit(SyncEvent.Error("Sem produtos disponíveis para $supermarket"))
                    }
                } else {
                    _events.tryEmit(SyncEvent.Error("Erro ao carregar dados de $supermarket"))
                }
            } catch (e: Exception) {
                _events.tryEmit(SyncEvent.Error("Erro: ${e.message}"))
            } finally {
                // Remove este supermercado da lista de "a carregar"
                _loadingSupermarkets.value = _loadingSupermarkets.value - supermarket
            }
        }
    }

    /**
     * Descarrega os produtos de TODOS os supermercados disponíveis.
     */
    fun downloadAllProducts(supermarkets: List<String>) {
        supermarkets.forEach { supermarket ->
            downloadProducts(supermarket)
        }
    }

    /**
     * Obtém os produtos de um supermercado (da cache local).
     */
    fun getProductsForSupermarket(supermarket: String): List<Product> {
        return _productsMap.value[supermarket] ?: emptyList()
    }

    private fun onCheckFailure(message: String) {
        _uiState.value = when (val s = _uiState.value) {
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
                SyncViewModel(
                    repository = app.container.syncRepository,
                    apiService = app.container.apiService
                )
            }
        }
    }
}

// ============================================================
// EVENTOS
// ============================================================
sealed class SyncEvent {
    data class Checked(val hasData: Boolean) : SyncEvent()
    data class ProductsLoaded(val supermarket: String, val products: List<Product>) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}

// ============================================================
// UI STATE
// ============================================================
sealed class SyncUiState {
    data object Loading : SyncUiState()
    data class Error(val message: String) : SyncUiState()
    data class Content(
        val supermarkets: List<String>,
        val hasData: Boolean,
        val validityLabel: String,
        val lastCheckedLabel: String,
        val checking: Boolean = false,
        val errorMessage: String? = null,
        val offline: Boolean = false
    ) : SyncUiState()
}
