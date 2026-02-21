package com.bighouse.dungeonsim.data.db.dao

import androidx.room.*
import com.bighouse.dungeonsim.data.db.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE id = 1 LIMIT 1")
    suspend fun get(): ProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}
