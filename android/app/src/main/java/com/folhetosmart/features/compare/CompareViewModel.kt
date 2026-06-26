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
import com.folhetosmart.data.repository.UserRepository
import com.folhetosmart.ui.regiaoDoDistrito
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
    private val shoppingRepository: ShoppingRepository,
    private val userRepository: UserRepository
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
    // A 1.ª busca de rede já terminou? Evita mostrar "vazio" antes disso.
    private var fetchDone = false

    // Região do utilizador (para escolher o Aldi da zona). Carregada uma vez.
    private var userRegion: String? = null
    private var regionLoaded = false

    init {
        observeOfferings()
        refresh()
    }

    fun onQueryChange(value: String) {
        query.value = value
        if (loaded) applyFilter(value)
    }

    fun reload() = refresh()

    /**
     * Observa a cache (Room Flow): a cópia atual e cada (re)escrita re-renderizam o
     * ecrã. É isto que faz o "Sincronizar agora" (que escreve a cache) aparecer no
     * Comparar ao vivo, mesmo estando-se noutro separador.
     */
    private fun observeOfferings() {
        viewModelScope.launch {
            val region = loadUserRegion()
            compareRepository.offeringsStream().collect { raw ->
                // Esconde expirados e mostra só o Aldi da zona do utilizador.
                allOfferings = aldiDaRegiao(raw.filterNot { expirou(it.validade) }, region)
                loaded = true
                // Só renderiza "vazio" depois de a 1.ª busca terminar (não pisca).
                if (raw.isNotEmpty() || fetchDone) applyFilter(query.value)
            }
        }
    }

    /** Dispara a busca de rede que (re)escreve a cache; o stream acima trata do render. */
    private fun refresh() {
        viewModelScope.launch {
            if (allOfferings.isEmpty()) _uiState.value = CompareUiState.Loading
            val ok = runCatching { compareRepository.allOfferings() }.isSuccess
            fetchDone = true
            if (!ok && allOfferings.isEmpty()) {
                _uiState.value = CompareUiState.Error(
                    "Não foi possível carregar os produtos. Verifica a ligação."
                )
            } else {
                applyFilter(query.value)
            }
        }
    }

    /** Região do utilizador (distrito do perfil → região), carregada uma só vez. */
    private suspend fun loadUserRegion(): String? {
        if (regionLoaded) return userRegion
        userRegion = try {
            regiaoDoDistrito(userRepository.me().district)
        } catch (e: Exception) {
            null
        }
        regionLoaded = true
        return userRegion
    }

    /**
     * O Aldi vem por região no feed ("Aldi Norte", "Aldi Centro", …). Mostra só o
     * da zona do utilizador (ou o primeiro, se a zona não estiver definida) e
     * rotula-o como "Aldi"; esconde os das outras regiões. Os outros supermercados
     * passam intactos.
     */
    private fun aldiDaRegiao(offers: List<FlyerOfferingDto>, region: String?): List<FlyerOfferingDto> {
        val regionais = offers.filter { it.supermercado.startsWith("Aldi ", ignoreCase = true) }
        if (regionais.isEmpty()) return offers
        val escolhido = region
            ?.let { r -> regionais.firstOrNull { it.supermercado.equals("Aldi $r", ignoreCase = true) }?.supermercado }
            ?: regionais.first().supermercado
        return offers
            .filter { o ->
                !o.supermercado.startsWith("Aldi ", ignoreCase = true) ||
                        o.supermercado.equals(escolhido, ignoreCase = true)
            }
            .map { o ->
                if (o.supermercado.equals(escolhido, ignoreCase = true)) o.copy(supermercado = "Aldi") else o
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
                    app.container.shoppingRepository,
                    app.container.userRepository
                )
            }
        }
    }
}
