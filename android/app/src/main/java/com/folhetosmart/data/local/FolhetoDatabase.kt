package com.folhetosmart.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Cache offline (regra 5 — offline-first): a última resposta de cada endpoint
 * relevante é guardada como JSON e lida quando não há internet.
 */
@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** Item da lista de compras (persistido localmente). */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val supermercado: String? = null,
    val preco: Double = 0.0,
    val quantity: Int
)

@Dao
interface CacheDao {

    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    suspend fun get(key: String): CacheEntry?

    /**
     * Versão reativa do [get]: emite a entrada atual e volta a emitir sempre que
     * a linha muda. É o que liga o Sincronizar ao Comparar — quando o Sincronizar
     * (re)escreve o feed na cache, o Comparar que observa este Flow re-renderiza
     * sozinho, mesmo estando noutro separador.
     */
    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    fun observe(key: String): Flow<CacheEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)
}

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items ORDER BY supermercado, display_name")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE product_id = :productId")
    suspend fun delete(productId: String)

    @Query("DELETE FROM shopping_items")
    suspend fun clear()
}

@Database(
    entities = [CacheEntry::class, ShoppingItemEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FolhetoDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun shoppingDao(): ShoppingDao
}
