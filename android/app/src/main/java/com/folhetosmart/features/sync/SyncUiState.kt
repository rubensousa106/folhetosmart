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
        val validityLabel: String,         // "16/06 a 22/06/2026"
        val lastCheckedLabel: String?,     // "14:32"
        val lastSyncDate: String,       // "2026-06-16T14:32:00Z" (para comparar com o servidor)
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
