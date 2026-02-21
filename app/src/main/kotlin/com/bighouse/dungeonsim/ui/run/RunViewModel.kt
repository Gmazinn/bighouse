package com.bighouse.dungeonsim.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.data.repository.GameRepository
import com.bighouse.dungeonsim.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class RunScreenTab { LOG, METERS, SUMMARY }
enum class MeterType { DAMAGE_DONE, DAMAGE_TAKEN, HEALING_DONE }
enum class PlaybackState { IDLE, SIMULATING, PLAYING, PAUSED, ENCOUNTER_BREAK, DONE }

data class RunUiState(
    val playback:            PlaybackState           = PlaybackState.IDLE,
    val speed:               Int                     = 1,              // 1, 2, or 4
    val currentEncounterIdx: Int                     = 0,
    val totalEncounters:     Int                     = 5,
    val encounterName:       String                  = "",
    val encounterType:       EncounterType?          = null,
    val currentTick:         Int                     = 0,
    val partyStates:         List<PartyMemberState>  = emptyList(),
    val enemyStates:         List<EnemyState>        = emptyList(),
    val log:                 List<LogEntry>          = emptyList(),
    val meters:              Meters                  = Meters(),
    val activeTab:           RunScreenTab            = RunScreenTab.LOG,
    val activeMeterType:     MeterType               = MeterType.DAMAGE_DONE,
    val result:              RunSimResult?           = null,
    val errorMessage:        String?                 = null,
)

class RunViewModel(private val repo: GameRepository) : ViewModel() {

    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    private var simResult: RunSimResult? = null
    private var playbackJob: Job? = null
    private var currentDifficulty: Difficulty = Difficulty.NORMAL

    fun startRun(party: List<Character>, dungeon: Dungeon, difficulty: Difficulty) {
        if (_state.value.playback != PlaybackState.IDLE) return
        currentDifficulty = difficulty

        _state.update { it.copy(
            playback        = PlaybackState.SIMULATING,
            totalEncounters = dungeon.encounters.size,
            encounterName   = dungeon.encounters.firstOrNull()?.name ?: "",
        ) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val items      = party.associate { char ->
                    char.id to repo.getAllItems().filter { it.id in char.equippedItems.values }
                }
                val lootPool   = repo.loadLootPool()
                val engine     = SimulationEngine()
                val result     = engine.simulateFullRun(party, items, dungeon, difficulty, lootPool)
                simResult      = result

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(playback = PlaybackState.PLAYING) }
                    startPlayback(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(
                        playback     = PlaybackState.DONE,
                        errorMessage = "Simulation error: ${e.message}",
                    ) }
                }
            }
        }
    }

    private fun startPlayback(result: RunSimResult) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var accumulatedLog = mutableListOf<LogEntry>()
            var combinedMeters = Meters()

            for ((encIdx, enc) in result.encounters.withIndex()) {
                _state.update { s ->
                    s.copy(
                        currentEncounterIdx = encIdx,
                        encounterName       = enc.encounterName,
                        encounterType       = enc.encounterType,
                        playback            = PlaybackState.PLAYING,
                    )
                }

                for (snap in enc.ticks) {
                    if (!isActive) return@launch
                    // Merge meters from this tick
                    combinedMeters = combinedMeters.merge(enc.meters)
                    accumulatedLog.addAll(snap.logLines)
                    // Keep log bounded
                    if (accumulatedLog.size > 500) {
                        accumulatedLog = accumulatedLog.takeLast(400).toMutableList()
                    }

                    _state.update { s ->
                        s.copy(
                            currentTick  = snap.tick,
                            partyStates  = snap.partyStates,
                            enemyStates  = snap.enemyStates,
                            log          = accumulatedLog.toList(),
                            meters       = enc.meters,
                        )
                    }

                    val delayMs = (600L / _state.value.speed)
                    delay(delayMs)

                    // Pause check (re-check after delay)
                    while (_state.value.playback == PlaybackState.PAUSED) {
                        delay(50)
                    }
                    if (!isActive) return@launch
                }

                // Between encounters: short break
                if (encIdx < result.encounters.lastIndex) {
                    _state.update { it.copy(playback = PlaybackState.ENCOUNTER_BREAK) }
                    delay(2000L / _state.value.speed)
                    _state.update { it.copy(playback = PlaybackState.PLAYING) }
                }
            }

            // Done
            _state.update { s ->
                s.copy(
                    playback     = PlaybackState.DONE,
                    result       = result,
                    activeTab    = RunScreenTab.SUMMARY,
                    meters       = result.totalMeters,
                )
            }

            // Persist rewards
            val partyIds = _state.value.partyStates.map { it.characterId }
            repo.applyRunRewards(
                result       = result,
                partyCharIds = partyIds,
                difficulty   = currentDifficulty,
                lootPool     = repo.loadLootPool(),
            )
        }
    }

    fun skipToResult() {
        val result = simResult ?: return
        playbackJob?.cancel()

        // Show final state of last completed encounter
        val lastEnc = result.encounters.last()
        val lastSnap = lastEnc.ticks.lastOrNull()

        _state.update { s ->
            s.copy(
                playback            = PlaybackState.DONE,
                currentEncounterIdx = result.encounters.lastIndex,
                encounterName       = lastEnc.encounterName,
                encounterType       = lastEnc.encounterType,
                partyStates         = lastSnap?.partyStates ?: s.partyStates,
                enemyStates         = lastSnap?.enemyStates ?: s.enemyStates,
                log                 = lastEnc.ticks.flatMap { it.logLines }.takeLast(200),
                meters              = result.totalMeters,
                result              = result,
                activeTab           = RunScreenTab.SUMMARY,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val partyIds = _state.value.partyStates.map { it.characterId }
            repo.applyRunRewards(result, partyIds, Difficulty.NORMAL, repo.loadLootPool())
        }
    }

    fun togglePause() {
        _state.update { s ->
            s.copy(playback = if (s.playback == PlaybackState.PLAYING) PlaybackState.PAUSED else PlaybackState.PLAYING)
        }
    }

    fun setSpeed(speed: Int) = _state.update { it.copy(speed = speed) }

    fun setTab(tab: RunScreenTab)       = _state.update { it.copy(activeTab = tab) }
    fun setMeterType(mt: MeterType)     = _state.update { it.copy(activeMeterType = mt) }

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
