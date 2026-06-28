package com.folhetosmart.data.repository

import com.folhetosmart.data.api.ApiService
import com.folhetosmart.data.api.ShoppingItemDto
import com.folhetosmart.data.api.ShoppingItemRequest
import com.folhetosmart.data.api.ShoppingQuantityRequest
import com.folhetosmart.data.api.ShoppingSyncRequest
import com.folhetosmart.data.local.ShoppingDao
import com.folhetosmart.data.local.ShoppingItemEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Lista de compras — Room como CACHE local + sincronização com o servidor
 * (`/api/v1/shopping`), para a lista ser partilhada entre a app e a web: o que se
 * adiciona num lado fica disponível no outro. As escritas são local-first
 * (otimistas) com write-through ao backend (best-effort; offline reconcilia depois).
 */
class ShoppingRepository(
    private val api: ApiService,
    private val shoppingDao: ShoppingDao
) {
    /** Lista de compras observável (Room) — a UI continua a observar isto. */
    val items: Flow<List<ShoppingItemEntity>> = shoppingDao.observeItems()

    /**
     * Sincroniza com o servidor. 1.ª vez (servidor vazio + lista local não-vazia):
     * empurra a local (`PUT`). Caso contrário: empurra adições locais ainda sem
     * `serverId` (feitas offline) e depois adota a lista do servidor. Best-effort:
     * sem sessão/offline, mantém a lista local e tenta de novo mais tarde.
     */
    suspend fun sync() {
        try {
            val server = api.shoppingList()
            val local = shoppingDao.getAll()
            if (server.isEmpty() && local.isNotEmpty()) {
                val pushed = api.shoppingReplaceAll(
                    ShoppingSyncRequest(local.map { it.toRequest() })
                )
                replaceLocalWith(pushed)
            } else {
                local.filter { it.serverId == null }.forEach { item ->
                    runCatching { api.shoppingUpsert(item.toRequest()) }
                }
                replaceLocalWith(api.shoppingList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // sem sessão / offline — fica a cache local; sincroniza na próxima.
        }
    }

    private suspend fun replaceLocalWith(server: List<ShoppingItemDto>) {
        shoppingDao.clear()
        server.forEach { dto ->
            shoppingDao.upsert(
                ShoppingItemEntity(
                    productId = "${dto.produto}::${dto.supermercado}",
                    displayName = dto.produto,
                    supermercado = dto.supermercado,
                    preco = dto.preco,
                    quantity = dto.quantity,
                    serverId = dto.id,
                )
            )
        }
    }

    /** Adiciona uma oferta (produto + supermercado + preço) — local + servidor. */
    suspend fun addOffer(produto: String, supermercado: String, preco: Double) {
        val entity = ShoppingItemEntity(
            productId = "$produto::$supermercado",
            displayName = produto,
            supermercado = supermercado,
            preco = preco,
            quantity = 1,
        )
        shoppingDao.upsert(entity)  // otimista
        runCatching {
            val dto = api.shoppingUpsert(ShoppingItemRequest(produto, supermercado, preco, 1))
            shoppingDao.upsert(entity.copy(serverId = dto.id, quantity = dto.quantity))
        }
    }

    /**
     * Ajusta a quantidade, com **mínimo de 1** — baixar até 0 NÃO remove o item
     * (fica em 1). Para remover existe o caixote do lixo ([remove]). Local + servidor.
     */
    suspend fun setQuantity(item: ShoppingItemEntity, quantity: Int) {
        val q = quantity.coerceAtLeast(1)
        shoppingDao.upsert(item.copy(quantity = q))
        runCatching {
            val sid = item.serverId
            if (sid != null) {
                api.shoppingSetQuantity(sid, ShoppingQuantityRequest(q))
            } else {
                val dto = api.shoppingUpsert(item.toRequest().copy(quantity = q))
                shoppingDao.upsert(item.copy(quantity = q, serverId = dto.id))
            }
        }
    }

    suspend fun remove(productId: String) {
        val item = shoppingDao.getAll().firstOrNull { it.productId == productId }
        shoppingDao.delete(productId)
        item?.serverId?.let { sid -> runCatching { api.shoppingDelete(sid) } }
    }
}

private fun ShoppingItemEntity.toRequest() =
    ShoppingItemRequest(displayName, supermercado ?: "", preco, quantity)
