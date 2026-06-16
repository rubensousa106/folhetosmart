package com.folhetosmart.features.sync

import com.folhetosmart.data.api.SupermarketStatusDto

/**
 * Estado do ecrã Sincronizar. A lista de supermercados está SEMPRE visível
 * (USER e ADMIN); é um único GET de leitura ao estado, sem polling contínuo.
 */
sealed interface SyncUiState {

    /** Primeira leitura (ainda nada para mostrar). */
    data object Loading : SyncUiState

    /**
     * Lista de supermercados + estado. [checking] mostra a barra de
     * verificação no topo; [errorMessage] é um aviso transitório (ex.: timeout)
     * que NÃO faz a lista desaparecer.
     */
    data class Content(
        val supermarkets: List<SupermarketStatusDto>,
        val hasData: Boolean,
        val validityLabel: String,         // "12/06 a 18/06/2026"
        val lastCheckedLabel: String?,     // "14:32"
        val checking: Boolean = false,
        val errorMessage: String? = null,
        val offline: Boolean = false
    ) : SyncUiState

    /** Falha na primeira leitura sem nada em cache para mostrar. */
    data class Error(val message: String) : SyncUiState
}

/** Eventos one-shot do ecrã Sincronizar (mostrados uma vez, ex.: Snackbar). */
sealed interface SyncEvent {
    /** Uma verificação MANUAL terminou; [hasData] = há dados esta semana. */
    data class Checked(val hasData: Boolean) : SyncEvent
}
