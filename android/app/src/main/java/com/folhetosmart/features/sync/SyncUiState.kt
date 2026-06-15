package com.folhetosmart.features.sync

/**
 * Estados do ecrã Sincronizar para o USER (leitura instantânea de dados já
 * processados). NÃO há polling nem progresso por supermercado — isso é só do
 * ecrã Admin. Um único GET, com tempo-limite de 15s.
 */
sealed interface SyncUiState {

    data object Loading : SyncUiState

    /** Resumo da semana. Se [hasData] for false mostra a mensagem de espera. */
    data class Ready(
        val hasData: Boolean,
        val totalProducts: Int,
        val supermarketsWithData: Int,
        val lastUpdateIso: String?,      // formatado no ecrã
        val validityLabel: String,       // ex.: "12/06 a 18/06/2026"
        val offline: Boolean = false
    ) : SyncUiState

    /** Erro/tempo-limite com botão "Tentar novamente". */
    data class Error(val message: String) : SyncUiState
}
