package com.bighouse.dungeonsim.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.data.repository.GameRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RosterUiState(
    val characters:      List<Character> = emptyList(),
    val allItems:        List<Item>      = emptyList(),
    val filterClass:     ClassType?      = null,
    val filterRole:      Role?           = null,
    val selectedChar:    Character?      = null,
    val isLoading:       Boolean         = true,
    val snackMessage:    String?         = null,
)

class RosterViewModel(private val repo: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(RosterUiState())
    val state: StateFlow<RosterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeCharacters()
                .combine(flowOf(repo.getAllItems())) { chars, items ->
                    _state.update { s ->
                        s.copy(
                            characters = applyFilter(chars, s.filterClass, s.filterRole),
                            allItems   = items,
                            isLoading  = false,
                        )
                    }
                }.collect()
        }
    }

    fun filterByClass(cls: ClassType?) {
        _state.update { s ->
            s.copy(
                filterClass = cls,
                characters  = applyFilter(s.characters, cls, s.filterRole),
            )
        }
        reload()
    }

    fun filterByRole(role: Role?) {
        _state.update { s -> s.copy(filterRole = role) }
        reload()
    }

    fun selectCharacter(char: Character?) {
        _state.update { s -> s.copy(selectedChar = char) }
    }

    fun equipItem(char: Character, item: Item) {
        viewModelScope.launch {
            repo.equipItem(char.id, item)
            _state.update { s -> s.copy(snackMessage = "Equipped ${item.name} on ${char.name}") }
        }
    }

    fun moveItemToVault(itemId: Long) {
        viewModelScope.launch {
            repo.moveToVault(itemId)
            _state.update { s -> s.copy(snackMessage = "Item moved to Vault") }
        }
    }

    fun dismissSnack() = _state.update { it.copy(snackMessage = null) }

    private fun reload() {
        viewModelScope.launch {
            val chars = repo.getCharacters()
            val items = repo.getAllItems()
            _state.update { s ->
                s.copy(
                    characters = applyFilter(chars, s.filterClass, s.filterRole),
                    allItems   = items,
                )
            }
        }
    }

    private fun applyFilter(
        chars: List<Character>,
        cls:   ClassType?,
        role:  Role?,
    ): List<Character> {
        var list = chars
        if (cls  != null) list = list.filter { it.classType == cls }
        if (role != null) list = list.filter { it.role      == role }
        return list
    }

    /** Returns items in a character's bag that would be upgrades (higher gear score). */
    fun upgradeCandidates(char: Character, allItems: List<Item>): List<Pair<ItemSlot, Item>> {
        val bagItems = allItems.filter { it.id in char.bagItemIds }
        return bagItems.mapNotNull { item ->
            val equippedId = char.equippedItems[item.slot]
            val equipped   = allItems.find { it.id == equippedId }
            if (equipped == null || item.gearScoreContribution() > equipped.gearScoreContribution()) {
                item.slot to item
            } else null
        }
    }
}
