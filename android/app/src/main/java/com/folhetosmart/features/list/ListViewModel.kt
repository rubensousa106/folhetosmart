package com.folhetosmart.features.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.api.ProductDto
import com.folhetosmart.data.local.ShoppingItemEntity
import com.folhetosmart.data.repository.ShoppingRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Total da lista num supermercado. */
data class StoreTotal(val supermercado: String, val subtotal: Double, val itemCount: Int)

/** Totais da lista por supermercado + total geral. */
data class ListTotals(val perStore: List<StoreTotal>, val grandTotal: Double)

/** Estado composto do ecrã Lista. */
data class ListUiState(
    val searchResults: List<ProductDto> = emptyList(),
    val searching: Boolean = false,
    val totals: ListTotals? = null,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class ListViewModel(private val repository: ShoppingRepository) : ViewModel() {

    /** Itens da lista (Room — sobrevive a reinícios e funciona offline). */
    val items: StateFlow<List<ShoppingItemEntity>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            searchQuery.debounce(400).collectLatest { q ->
                if (q.length < 2) {
                    _uiState.value = _uiState.value.copy(searchResults = emptyList(), searching = false)
                } else {
                    searchProducts(q)
                }
            }
        }
    }

    fun onSearchChange(value: String) {
        searchQuery.value = value
    }

    private suspend fun searchProducts(query: String) {
        _uiState.value = _uiState.value.copy(searching = true, error = null)
        try {
            val results = repository.searchProducts(query)
            _uiState.value = _uiState.value.copy(searchResults = results, searching = false)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                searching = false,
                error = "Não foi possível pesquisar produtos."
            )
        }
    }

    fun addProduct(product: ProductDto) {
        viewModelScope.launch {
            repository.addProduct(product)
            searchQuery.value = ""
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), totals = null)
        }
    }

    fun changeQuantity(item: ShoppingItemEntity, delta: Int) {
        viewModelScope.launch {
            repository.setQuantity(item, item.quantity + delta)
            _uiState.value = _uiState.value.copy(totals = null) // o total ficou obsoleto
        }
    }

    fun remove(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repository.remove(item.productId)
            _uiState.value = _uiState.value.copy(totals = null)
        }
    }

    /**
     * Calcula LOCALMENTE (sem backend) o total da lista por supermercado, usando
     * o preço e a loja já guardados em cada item.
     */
    fun showTotals() {
        val current = items.value
        if (current.isEmpty()) return
        val perStore = current
            .groupBy { it.supermercado?.takeIf { s -> s.isNotBlank() } ?: "Sem supermercado" }
            .map { (loja, its) ->
                StoreTotal(
                    supermercado = loja,
                    subtotal = its.sumOf { it.preco * it.quantity },
                    itemCount = its.sumOf { it.quantity }
                )
            }
            .sortedBy { it.supermercado }
        _uiState.value = _uiState.value.copy(
            totals = ListTotals(perStore, perStore.sumOf { it.subtotal })
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FolhetoSmartApp
                ListViewModel(app.container.shoppingRepository)
            }
        }
    }
}
