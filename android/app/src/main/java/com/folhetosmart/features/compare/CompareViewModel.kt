package com.folhetosmart.features.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.api.ProductPriceDto
import com.folhetosmart.data.repository.AlertsRepository
import com.folhetosmart.data.repository.CompareRepository
import com.folhetosmart.data.repository.WeekRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Estados do ecrã Comparar. */
sealed interface CompareUiState {
    data object Idle : CompareUiState                       // empty state inicial
    data object Searching : CompareUiState
    data class Results(
        val products: List<ProductDto>,
        val isPromotions: Boolean = false                  // true = promoções da semana
    ) : CompareUiState
    data class NoResults(val query: String) : CompareUiState
    data class LoadingPrices(val product: ProductDto) : CompareUiState
    data class Prices(
        val product: ProductDto,
        val prices: List<ProductPriceDto>,
        val offline: Boolean
    ) : CompareUiState
    data class Error(val message: String) : CompareUiState
}

@OptIn(FlowPreview::class)
class CompareViewModel(
    private val compareRepository: CompareRepository,
    private val alertsRepository: AlertsRepository,
    private val weekRepository: WeekRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CompareUiState>(CompareUiState.Idle)
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    val query = MutableStateFlow("")

    /** Mensagem efémera (snackbar) — ex.: resultado da criação de alerta. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            query.debounce(400).collectLatest { q ->
                // Pesquisa vazia (ou < 2 chars) -> promoções da semana (Fix 3).
                if (q.trim().length < 2) loadPromotions() else search(q)
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Ecrã de início útil: promoções da semana (search vazio na API).
     * Fix 5 (offline-first): em falha de rede, recorre à cache local da semana.
     */
    private suspend fun loadPromotions() {
        _uiState.value = CompareUiState.Searching
        try {
            val products = compareRepository.search("")
            _uiState.value = if (products.isNotEmpty()) {
                CompareUiState.Results(products, isPromotions = true)
            } else {
                fromCacheOr(CompareUiState.Idle)
            }
        } catch (e: Exception) {
            _uiState.value = fromCacheOr(
                CompareUiState.Error("Não foi possível carregar as promoções. Verifica a ligação.")
            )
        }
    }

    /** Produtos da cache local da semana, ou o estado de recurso dado. */
    private suspend fun fromCacheOr(fallback: CompareUiState): CompareUiState {
        val cached = weekRepository.cachedWeekProducts()
        return if (cached.isNotEmpty()) {
            CompareUiState.Results(cached, isPromotions = true)
        } else {
            fallback
        }
    }

    private suspend fun search(q: String) {
        _uiState.value = CompareUiState.Searching
        try {
            val products = compareRepository.search(q)
            _uiState.value = if (products.isEmpty()) {
                CompareUiState.NoResults(q)
            } else {
                CompareUiState.Results(products)
            }
        } catch (e: Exception) {
            _uiState.value = CompareUiState.Error(
                "Não foi possível pesquisar produtos. Verifica a ligação."
            )
        }
    }

    fun selectProduct(product: ProductDto) {
        viewModelScope.launch {
            _uiState.value = CompareUiState.LoadingPrices(product)
            try {
                val (prices, fromCache) = compareRepository.prices(product.id)
                _uiState.value = CompareUiState.Prices(product, prices, fromCache)
            } catch (e: Exception) {
                _uiState.value = CompareUiState.Error(
                    "Sem preços disponíveis para este produto esta semana."
                )
            }
        }
    }

    fun backToResults() {
        val q = query.value
        viewModelScope.launch {
            if (q.trim().length < 2) loadPromotions() else search(q)
        }
    }

    /** Cria um alerta de preço para o produto atualmente aberto. */
    fun createAlert(targetPrice: Double?, anyPromotion: Boolean) {
        val current = _uiState.value as? CompareUiState.Prices ?: return
        viewModelScope.launch {
            if (!alertsRepository.isLoggedIn) {
                _message.value = "Inicia sessão no separador Alertas para criar alertas."
                return@launch
            }
            try {
                alertsRepository.create(current.product.id, targetPrice, anyPromotion)
                _message.value = "Alerta criado para ${current.product.displayName} 🔔"
            } catch (e: Exception) {
                _message.value = "Não foi possível criar o alerta. Tenta novamente."
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                CompareViewModel(
                    app.container.compareRepository,
                    app.container.alertsRepository,
                    app.container.weekRepository
                )
            }
        }
    }
}
