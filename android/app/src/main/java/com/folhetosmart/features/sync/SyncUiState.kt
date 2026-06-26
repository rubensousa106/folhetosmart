package com.folhetosmart.features.sync

/**
 * Estado do ecrã Sincronizar. Modelo simples: existe UM só ficheiro — os
 * produtos normalizados. [synced] = ✓ (descarregado/disponível) ou ✗ (por
 * sincronizar).
 */
sealed interface SyncUiState {

    data object Loading : SyncUiState

    data class Content(
        val synced: Boolean,
        val productCount: Int,
        val validityLabel: String,         // "22/06 a 28/06/2026" (das datas dos feeds)
        val lastCheckedLabel: String?,     // "14:32"
        val checking: Boolean = false,
        val errorMessage: String? = null
    ) : SyncUiState

    data class Error(val message: String) : SyncUiState
}

/** Eventos one-shot do ecrã Sincronizar (mostrados uma vez, ex.: Snackbar). */
sealed interface SyncEvent {
    /** Uma sincronização MANUAL terminou; [hasData] = ficou sincronizado. */
    data class Checked(val hasData: Boolean) : SyncEvent
}
