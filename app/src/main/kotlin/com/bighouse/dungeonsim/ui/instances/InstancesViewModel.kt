package com.bighouse.dungeonsim.ui.instances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.data.repository.GameRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class InstancesUiState(
    val progress:          GameProgress      = GameProgress(),
    val dungeon:           Dungeon           = Dungeon.CINDER_CRYPT,
    val allCharacters:     List<Character>   = emptyList(),
    val selectedParty:     List<Character>   = emptyList(),    // max 5
    val selectedDifficulty: Difficulty       = Difficulty.NORMAL,
    val canStartRun:       Boolean           = false,
    val partyError:        String?           = null,
    val isLoading:         Boolean           = true,
)

class InstancesViewModel(private val repo: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(InstancesUiState())
    val state: StateFlow<InstancesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeProgress(),
                repo.observeCharacters(),
            ) { prog, chars ->
                _state.update { s ->
                    val available = prog.availableDifficulties()
                    val diff = if (s.selectedDifficulty in available) s.selectedDifficulty else available.first()
                    s.copy(
                        progress      = prog,
                        allCharacters = chars,
                        selectedDifficulty = diff,
                        isLoading     = false,
                    )
                }
                validateParty()
            }.collect()
        }
    }

    fun selectDifficulty(d: Difficulty) {
        _state.update { it.copy(selectedDifficulty = d) }
    }

    fun toggleCharacterInParty(char: Character) {
        _state.update { s ->
            val current = s.selectedParty
            val newParty = if (current.any { it.id == char.id }) {
                current.filter { it.id != char.id }
            } else if (current.size < 5) {
                current + char
            } else {
                current // at max
            }
            s.copy(selectedParty = newParty)
        }
        validateParty()
    }

    fun autoCompose() {
        val chars = _state.value.allCharacters
        val party = mutableListOf<Character>()
        // Fill roles: 1 tank, 1 healer, 3 DPS/support
        val tanks    = chars.filter { it.role.isTank() }.sortedByDescending { it.level }
        val healers  = chars.filter { it.role.isHealer() }.sortedByDescending { it.level }
        val dps      = chars.filter { it.role.isDPS() || it.role.isSupport() }.sortedByDescending { it.level }
        if (tanks.isNotEmpty())  party.add(tanks.first())
        if (healers.isNotEmpty()) party.add(healers.first())
        val remainDps = dps.filter { it !in party }
        party.addAll(remainDps.take((5 - party.size).coerceAtLeast(0)))
        _state.update { it.copy(selectedParty = party) }
        validateParty()
    }

    fun clearParty() {
        _state.update { it.copy(selectedParty = emptyList(), partyError = null, canStartRun = false) }
    }

    private fun validateParty() {
        _state.update { s ->
            val p = s.selectedParty
            val error = when {
                p.isEmpty()  -> "Select at least 1 character"
                p.size > 5   -> "Maximum 5 characters"
                else         -> null
            }
            s.copy(partyError = error, canStartRun = error == null && p.isNotEmpty())
        }
    }
}
