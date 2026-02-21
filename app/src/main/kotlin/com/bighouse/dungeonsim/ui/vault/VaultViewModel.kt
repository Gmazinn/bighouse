package com.bighouse.dungeonsim.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.data.repository.GameRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VaultUiState(
    val progress:       GameProgress    = GameProgress(),
    val vaultItems:     List<Item>      = emptyList(),
    val allCharacters:  List<Character> = emptyList(),
    val selectedItem:   Item?           = null,
    val selectedChar:   Character?      = null,
    val filterSlot:     ItemSlot?       = null,
    val isLoading:      Boolean         = true,
    val snackMessage:   String?         = null,
)

class VaultViewModel(private val repo: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeProgress(),
                repo.observeVaultItems(),
                repo.observeCharacters(),
            ) { prog, items, chars ->
                _state.update { s ->
                    s.copy(
                        progress      = prog,
                        vaultItems    = applyFilter(items, s.filterSlot),
                        allCharacters = chars,
                        isLoading     = false,
                    )
                }
            }.collect()
        }
    }

    fun filterBySlot(slot: ItemSlot?) {
        _state.update { s ->
            s.copy(
                filterSlot  = slot,
                vaultItems  = applyFilter(s.vaultItems, slot),
            )
        }
    }

    fun selectItem(item: Item?)  = _state.update { it.copy(selectedItem = item) }
    fun selectChar(char: Character?) = _state.update { it.copy(selectedChar = char) }

    fun sendItemToCharacter(item: Item, char: Character) {
        viewModelScope.launch {
            repo.equipItem(char.id, item)
            _state.update { s ->
                s.copy(
                    selectedItem = null,
                    selectedChar = null,
                    snackMessage = "${item.name} sent to ${char.name}",
                )
            }
        }
    }

    fun buyTokenItem(slot: ItemSlot, char: Character) {
        val progress = _state.value.progress
        val cost = progress.tokenCostForSlot(slot)
        if (progress.bossTokens < cost) {
            _state.update { it.copy(snackMessage = "Need $cost tokens (have ${progress.bossTokens})") }
            return
        }
        viewModelScope.launch {
            repo.spendTokensForItem(slot, char.id)
            _state.update { it.copy(snackMessage = "Token item crafted for ${char.name}!") }
        }
    }

    fun dismissSnack() = _state.update { it.copy(snackMessage = null) }

    private fun applyFilter(items: List<Item>, slot: ItemSlot?): List<Item> =
        if (slot == null) items else items.filter { it.slot == slot }
}
