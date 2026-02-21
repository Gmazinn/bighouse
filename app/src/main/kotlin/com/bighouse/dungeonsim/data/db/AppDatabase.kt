package com.bighouse.dungeonsim.data.db

import android.content.Context
import androidx.room.*
import com.bighouse.dungeonsim.data.db.dao.*
import com.bighouse.dungeonsim.data.db.entity.*

@Database(
    entities = [CharacterEntity::class, ItemEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun itemDao(): ItemDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dungeon_sim.db",
                ).build().also { INSTANCE = it }
            }
    }
}
