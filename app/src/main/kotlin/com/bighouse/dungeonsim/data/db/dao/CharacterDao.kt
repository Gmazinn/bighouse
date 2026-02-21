package com.bighouse.dungeonsim.data.db.dao

import androidx.room.*
import com.bighouse.dungeonsim.data.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY level DESC, name ASC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY level DESC, name ASC")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(characters: List<CharacterEntity>)

    @Update
    suspend fun update(character: CharacterEntity)

    @Query("DELETE FROM characters")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int
}
