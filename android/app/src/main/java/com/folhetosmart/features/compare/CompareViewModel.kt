package com.folhetosmart.features.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.folhetosmart.FolhetoSmartApp
import com.folhetosmart.data.models.FlyerOfferingDto
import com.folhetosmart.data.repository.CompareRepository
import com.folhetosmart.data.repository.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Um produto e as suas ofertas, ordenadas por preço (a 1.ª é a mais barata —
 * destacada no ecrã). Com a normalização do Claude, o mesmo produto de vários
 * supermercados fica neste grupo.
 */
data class ProductGroup(
    val produto: String,
    val offers: List<FlyerOfferingDto>
) {
    val cheapest: FlyerOfferingDto get() = offers.first()
    val hasMultiple: Boolean get() = offers.size > 1
}

/** Estados do ecrã Comparar (montra de todos os produtos dos folhetos). */
sealed interface CompareUiState {
    data object Loading : CompareUiState
    data class Content(val groups: List<ProductGroup>) : CompareUiState
    data class Empty(val query: String) : CompareUiState
    data class Error(val message: String) : CompareUiState
}

class CompareViewModel(
    private val compareRepository: CompareRepository,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    val query = MutableStateFlow("")

    /** Mensagem efémera (snackbar) — ex.: "adicionado à lista". */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Todas as ofertas, carregadas uma vez; a pesquisa filtra esta lista em memória.
    private var allOfferings: List<FlyerOfferingDto> = emptyList()
    private var loaded = false

    init {
        load()
    }

    fun onQueryChange(value: String) {
        query.value = value
        if (loaded) applyFilter(value)
    }

    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = CompareUiState.Loading
            try {
                // Esconde ofertas cuja validade já terminou — o folheto expirou.
                allOfferings = compareRepository.allOfferings().filterNot { expirou(it.validade) }
                loaded = true
                applyFilter(query.value)
            } catch (e: Exception) {
                _uiState.value = CompareUiState.Error(
                    "Não foi possível carregar os produtos. Verifica a ligação."
                )
            }
        }
    }

    /** Filtra por nome e agrupa por produto, ordenando cada grupo pelo preço. */
    private fun applyFilter(rawQuery: String) {
        val term = rawQuery.trim().lowercase()
        // Tolerante a singular/plural: "sardinhas" também encontra "sardinha".
        val stem = term.trimEnd('s').ifEmpty { term }
        val matched = if (term.isEmpty()) {
            allOfferings
        } else {
            allOfferings.filter {
                val hay = (it.produto + " " + (it.marca ?: "")).lowercase()
                hay.contains(term) || hay.contains(stem)
            }
        }

        if (matched.isEmpty()) {
            _uiState.value = CompareUiState.Empty(rawQuery.trim())
            return
        }

        val groups = matched
            .groupBy { it.produto.trim().lowercase() }
            .map { (_, offers) ->
                ProductGroup(
                    produto = offers.first().produto,
                    offers = offers.sortedBy { it.preco }
                )
            }
            .sortedBy { it.produto.lowercase() }

        _uiState.value = CompareUiState.Content(groups)
    }

    /**
     * True se a validade da oferta já terminou (data de fim anterior a HOJE).
     * Formato "16/06/2026 a 22/06/2026" — usa-se a data depois de " a ". No
     * próprio dia de fim ainda é válida; só desaparece a partir do dia seguinte.
     * Ofertas sem data legível ficam visíveis (não escondemos o que não sabemos datar).
     */
    private fun expirou(validade: String?): Boolean {
        val fim = validade?.substringAfterLast(" a ", "")?.trim().orEmpty()
        if (fim.isEmpty()) return false
        return try {
            val data = java.time.LocalDate.parse(
                fim, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            )
            data.isBefore(java.time.LocalDate.now())
        } catch (e: Exception) {
            false // data ilegível -> não esconder
        }
    }

    /** Adiciona uma OFERTA específica (produto + supermercado + preço) à lista. */
    fun addOffer(produto: String, supermercado: String, preco: Double) {
        viewModelScope.launch {
            shoppingRepository.addOffer(produto, supermercado, preco)
            _message.value = "“$produto” ($supermercado) adicionado à lista 🛒"
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
                    app.container.shoppingRepository
                )
            }
        }
    }
}
