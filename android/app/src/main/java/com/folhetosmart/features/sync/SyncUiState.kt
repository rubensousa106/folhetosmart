package com.folhetosmart.features.sync

import com.folhetosmart.data.api.SupermarketStatusDto

/** Resumo da última sincronização concluída (cartão inferior). */
data class LastSyncSummary(
    val finishedAt: String?,
    val productsMatched: Int,
    val promotionsFound: Long,
    val avgSavingsPct: Double?
)

/**
 * Estados do ecrã Sincronizar (Fix 3 — regras de honestidade: nunca afirmar
 * "dados atualizados" nem mostrar "Ver promoções" sem dados reais na BD).
 */
sealed interface SyncUiState {

    /** A ler o estado pela primeira vez. */
    data object Loading : SyncUiState

    /**
     * Vista estática — nada a processar. "Ver promoções da semana" só aparece
     * se [hasCurrentWeekData]; caso contrário mostra a mensagem de espera.
     */
    data class Idle(
        val supermarkets: List<SupermarketStatusDto>,
        val hasCurrentWeekData: Boolean,
        val totalProductsThisWeek: Int,
        val lastSync: LastSyncSummary?,
        val lastCheckedLabel: String?,   // "14:32" ou null
        val offline: Boolean = false
    ) : SyncUiState

    /** Pelo menos 1 supermercado em "running" — barra de progresso + polling 2s. */
    data class Processing(
        val supermarkets: List<SupermarketStatusDto>,
        val doneCount: Int,
        val totalCount: Int,
        val etaSeconds: Int
    ) : SyncUiState

    /**
     * Processamento terminou com >=1 sucesso E há dados desta semana. Mostra o
     * resumo durante 3s e só depois navega para Comparar.
     */
    data class Completed(
        val productsMatched: Int,
        val avgSavingsPct: Double?,
        val supermarkets: List<SupermarketStatusDto>
    ) : SyncUiState

    /** Terminou sem nenhum sucesso — não navega; sugere upload manual de PDFs. */
    data class AllFailed(
        val supermarkets: List<SupermarketStatusDto>
    ) : SyncUiState

    /** Erro de leitura/tempo-limite com botão "Tentar novamente". */
    data class Error(val message: String) : SyncUiState
}
