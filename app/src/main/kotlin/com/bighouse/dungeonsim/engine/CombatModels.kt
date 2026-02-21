package com.bighouse.dungeonsim.engine

import com.bighouse.dungeonsim.data.model.*

// ─── Mutable working state during simulation ──────────────────────────────────

data class CombatChar(
    val id:          Long,
    val name:        String,
    val classType:   ClassType,
    val role:        Role,
    val level:       Int,
    var hp:          Int,
    val maxHp:       Int,
    var mana:        Int,
    val maxMana:     Int,
    val armor:       Int,
    val magicArmor:  Int,
    val physDamage:  Float,
    val spellDamage: Float,
    val critChance:  Float,
    // Cooldowns (ticks remaining until usable again)
    var tauntCd:     Int = 0,
    var aoeCd:       Int = 0,
    var healAoeCd:   Int = 0,
    var buffCd:      Int = 0,
    var debuffCd:    Int = 0,
    // Active buff / debuff durations
    var buffTicks:   Int = 0,    // support buff active
    var debuffTicks: Int = 0,    // support debuff on self (not used server-side)
    var isAlive:     Boolean = true,
)

data class CombatEnemy(
    val id:         Int,
    val name:       String,
    val type:       EnemyType,
    var hp:         Int,
    val maxHp:      Int,
    val damage:     Float,
    val armor:      Int,
    val magicArmor: Int,
    val threatTable: MutableMap<Long, Float> = mutableMapOf(),
    var debuffTicks: Int = 0,    // support debuff active on this enemy
    var isAlive:    Boolean = true,
) {
    fun highestThreatTarget(aliveParty: List<CombatChar>): CombatChar? =
        aliveParty.maxByOrNull { threatTable[it.id] ?: 0f }
}

// ─── Snapshot types (for UI playback) ────────────────────────────────────────

data class PartyMemberState(
    val characterId: Long,
    val name:        String,
    val classType:   ClassType,
    val role:        Role,
    val hp:          Int,
    val maxHp:       Int,
    val mana:        Int,
    val maxMana:     Int,
    val isAlive:     Boolean,
)

data class EnemyState(
    val enemyId:         Int,
    val name:            String,
    val type:            EnemyType,
    val hp:              Int,
    val maxHp:           Int,
    val isAlive:         Boolean,
    val currentTargetId: Long?,     // characterId being attacked (for display)
)

enum class LogType { NORMAL, DAMAGE, HEAL, DEATH, ABILITY, LOOT, SYSTEM }

data class LogEntry(
    val tick:    Int,
    val message: String,
    val type:    LogType = LogType.NORMAL,
)

data class TickSnapshot(
    val tick:        Int,
    val partyStates: List<PartyMemberState>,
    val enemyStates: List<EnemyState>,
    val logLines:    List<LogEntry>,
)

// ─── Meters ───────────────────────────────────────────────────────────────────

data class CharMeterEntry(
    val characterId:   Long,
    val characterName: String,
    val classType:     ClassType,
    var damageDone:    Int = 0,
    var damageTaken:   Int = 0,
    var healingDone:   Int = 0,
    var deaths:        Int = 0,
)

data class Meters(
    val entries: MutableMap<Long, CharMeterEntry> = mutableMapOf(),
) {
    fun damage(charId: Long, amount: Int)  { entries[charId]?.let { it.damageDone  += amount } }
    fun taken(charId: Long, amount: Int)   { entries[charId]?.let { it.damageTaken += amount } }
    fun healing(charId: Long, amount: Int) { entries[charId]?.let { it.healingDone += amount } }
    fun death(charId: Long)                { entries[charId]?.let { it.deaths      += 1 } }

    fun sortedByDamage()  = entries.values.sortedByDescending { it.damageDone }
    fun sortedByTaken()   = entries.values.sortedByDescending { it.damageTaken }
    fun sortedByHealing() = entries.values.sortedByDescending { it.healingDone }
    fun totalDamage()     = entries.values.sumOf { it.damageDone }
    fun totalHealing()    = entries.values.sumOf { it.healingDone }
}

fun Meters.merge(other: Meters): Meters {
    val out = Meters()
    for ((id, e) in entries) {
        out.entries[id] = e.copy(
            damageDone  = e.damageDone  + (other.entries[id]?.damageDone  ?: 0),
            damageTaken = e.damageTaken + (other.entries[id]?.damageTaken ?: 0),
            healingDone = e.healingDone + (other.entries[id]?.healingDone ?: 0),
            deaths      = e.deaths      + (other.entries[id]?.deaths      ?: 0),
        )
    }
    for ((id, e) in other.entries) {
        if (id !in out.entries) out.entries[id] = e.copy()
    }
    return out
}

// ─── Loot & encounter results ─────────────────────────────────────────────────

data class LootDrop(
    val item:            Item,
    val fromEncounterIdx: Int,
)

data class EncounterSimResult(
    val encounterIndex:  Int,
    val encounterName:   String,
    val encounterType:   EncounterType,
    val ticks:           List<TickSnapshot>,
    val won:             Boolean,
    val meters:          Meters,
    val lootDrops:       List<LootDrop>,
    val goldEarned:      Int,
    val tokensEarned:    Int,
    val xpEarned:        Int,
)

data class RunSimResult(
    val encounters:         List<EncounterSimResult>,
    val totalMeters:        Meters,
    val survived:           Boolean,
    val totalXpEarned:      Int,
    val totalGoldEarned:    Int,
    val totalTokensEarned:  Int,
    val allLoot:            List<LootDrop>,
    val wipedEncounterIdx:  Int,    // -1 if full clear
)
