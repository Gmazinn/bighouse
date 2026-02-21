package com.bighouse.dungeonsim

import android.app.Application
import com.bighouse.dungeonsim.data.assets.AssetLoader
import com.bighouse.dungeonsim.data.db.AppDatabase
import com.bighouse.dungeonsim.data.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DungeonSimApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val assetLoader: AssetLoader by lazy { AssetLoader(this) }

    val repository: GameRepository by lazy {
        GameRepository(database, assetLoader)
    }

    val viewModelFactory: ViewModelFactory by lazy {
        ViewModelFactory(repository)
    }

    override fun onCreate() {
        super.onCreate()
        // Seed the DB with starter data on first launch
        CoroutineScope(Dispatchers.IO).launch {
            repository.initIfEmpty()
        }
    }
}
