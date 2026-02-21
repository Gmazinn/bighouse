package com.bighouse.dungeonsim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bighouse.dungeonsim.data.repository.GameRepository
import com.bighouse.dungeonsim.ui.instances.InstancesViewModel
import com.bighouse.dungeonsim.ui.roster.RosterViewModel
import com.bighouse.dungeonsim.ui.run.RunViewModel
import com.bighouse.dungeonsim.ui.vault.VaultViewModel

class ViewModelFactory(private val repo: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(RosterViewModel::class.java)    -> RosterViewModel(repo)    as T
        modelClass.isAssignableFrom(InstancesViewModel::class.java) -> InstancesViewModel(repo) as T
        modelClass.isAssignableFrom(RunViewModel::class.java)       -> RunViewModel(repo)       as T
        modelClass.isAssignableFrom(VaultViewModel::class.java)     -> VaultViewModel(repo)     as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
