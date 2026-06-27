package com.folhetosmart.data.repository

import com.folhetosmart.data.local.ShoppingDao
import com.folhetosmart.data.local.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Lista de compras — 100% local (Room). Os produtos entram a partir do ecrã
 * Comparar (botão de carrinho em cada oferta); não há pesquisa nem otimização
 * pelo backend (o total por supermercado é calculado localmente no ListViewModel).
 */
class ShoppingRepository(private val shoppingDao: ShoppingDao) {

    /** Lista de compras persistida localmente (Room). */
    val items: Flow<List<ShoppingItemEntity>> = shoppingDao.observeItems()

    /**
     * Adiciona uma OFERTA específica (produto + supermercado + preço) à lista. O
     * mesmo produto de lojas diferentes fica em itens separados — a Lista mostra-os
     * agrupados por supermercado.
     */
    suspend fun addOffer(produto: String, supermercado: String, preco: Double) =
        shoppingDao.upsert(ShoppingItemEntity(
            productId = "$produto::$supermercado",
            displayName = produto,
            supermercado = supermercado,
            preco = preco,
            quantity = 1
        ))

    /**
     * Ajusta a quantidade, com **mínimo de 1** — baixar até 0 NÃO remove o item
     * (fica em 1). Para remover existe o caixote do lixo ([remove]).
     */
    suspend fun setQuantity(item: ShoppingItemEntity, quantity: Int) {
        shoppingDao.upsert(item.copy(quantity = quantity.coerceAtLeast(1)))
    }

    suspend fun remove(productId: String) = shoppingDao.delete(productId)
}
