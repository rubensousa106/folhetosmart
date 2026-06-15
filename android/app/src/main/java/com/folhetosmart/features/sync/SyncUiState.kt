package com.folhetosmart.features.sync

import com.folhetosmart.data.api.SupermarketStatusDto

/** Progresso enquanto o automatizador corre. */
data class SyncProgress(val productsCompared: Int)

/** Resultado final de uma sincronização. */
data class SyncResult(
    val productsMatched: Int,
    val promotionsFound: Long,
    val avgSavingsPct: Double?,
    val finishedAt: String?
)

/** Resumo da última sincronização (cartão inferior). */
data class LastSyncSummary(
    val finishedAt: String?,
    val productsMatched: Int,
    val promotionsFound: Long,
    val avgSavingsPct: Double?
)

/** Estados do ecrã Sincronizar (Tarefa 2). */
sealed interface SyncUiState {

    /** A verificar folhetos. */
    data object Loading : SyncUiState

    /** Folhetos em falta — botão desativado "Aguardar todos os folhetos (x/y)". */
    data class WaitingForFlyers(
        val ready: Int,
        val total: Int,
        val supermarkets: List<SupermarketStatusDto>,
        val lastSync: LastSyncSummary?,
        val offline: Boolean = false
    ) : SyncUiState

    /** Todos disponíveis — botão ativo "▶ Comparar preços agora". */
    data class ReadyToSync(
        val supermarkets: List<SupermarketStatusDto>,
        val lastSync: LastSyncSummary?
    ) : SyncUiState

    /** A processar, com progresso. */
    data class Syncing(
        val progress: SyncProgress,
        val supermarkets: List<SupermarketStatusDto>
    ) : SyncUiState

    /** Resultado final. */
    data class Done(
        val result: SyncResult,
        val supermarkets: List<SupermarketStatusDto>
    ) : SyncUiState

    /** Erro com botão "Tentar novamente". */
    data class Error(val message: String) : SyncUiState
}
