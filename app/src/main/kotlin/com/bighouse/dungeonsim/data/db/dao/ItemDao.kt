package com.bighouse.dungeonsim.data.db.dao

import androidx.room.*
import com.bighouse.dungeonsim.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE ownerCharId = :charId AND inVault = 0")
    suspend fun getByOwner(charId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE inVault = 1")
    fun observeVault(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE inVault = 1")
    suspend fun getVault(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET ownerCharId = :charId, inVault = 0 WHERE id = :itemId")
    suspend fun assignToChar(itemId: Long, charId: Long)

    @Query("UPDATE items SET inVault = 1, ownerCharId = 0 WHERE id = :itemId")
    suspend fun moveToVault(itemId: Long)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}
