package com.bighouse.dungeonsim.engine

import com.bighouse.dungeonsim.data.model.*
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Deterministic combat simulation engine.
 *
 * All ticks are computed instantly; the UI plays back TickSnapshot lists.
 *
 * Formulas implemented:
 *  - physDR  = armor / (armor + 50*attackerLevel + 400)
 *  - magicDR = magicArmor / (magicArmor + 50*attackerLevel + 400)
 *  - crit multiplier = 1.5
 *  - Threat: damage*roleThreatCoef, heal*0.35 to all enemies
 *  - Taunt: CD 10 ticks, sets threat to highest+1 on current target
 *  - Healer ST: SpellDamage * 1.35 * healCoef
 *  - Healer AoE: CD 8 ticks, triggers if ≥3 allies <60% HP, heals for 70% of ST amount
 *  - Support buff: +8% party damage, 6 ticks, CD 18
 *  - Support debuff: -8% enemy damage, 6 ticks, CD 18
 *  - Mana cost: DPS 0.06*maxMana, Heal 0.08*maxMana; regen 0.006*maxMana/tick
 *  - If insufficient mana, ability fires at 45% effect
 */
class SimulationEngine(private val seed: Long = System.currentTimeMillis()) {

    private val rng = Random(seed)

    // ─── Public entry point ───────────────────────────────────────────────────

    fun simulateFullRun(
        party:      List<Character>,
        partyItems: Map<Long, List<Item>>,   // charId -> equipped items
        dungeon:    Dungeon,
        difficulty: Difficulty,
        lootPool:   List<Item>,
    ): RunSimResult {
        // Build mutable combat chars
        val initialCombatParty = party.map { char ->
            val equipped = partyItems[char.id] ?: emptyList()
            val bonusStats = equipped.fold(ItemStats()) { acc, item -> acc + item.stats }
            val base = char.baseStats
            CombatChar(
                id          = char.id,
                name        = char.name,
                classType   = char.classType,
                role        = char.role,
                level       = char.level,
                hp          = base.hp + bonusStats.hp,
                maxHp       = base.hp + bonusStats.hp,
                mana        = base.mana + bonusStats.mana,
                maxMana     = base.mana + bonusStats.mana,
                armor       = base.armor + bonusStats.armor,
                magicArmor  = base.magicArmor + bonusStats.magicArmor,
                physDamage  = base.physDamage + bonusStats.physDamage,
                spellDamage = base.spellDamage + bonusStats.spellDamage,
                critChance  = (base.critChance + bonusStats.critChance).coerceAtMost(0.75f),
            )
        }.toMutableList()

        val allEncounterResults = mutableListOf<EncounterSimResult>()
        var totalMeters = Meters()
        var totalXp     = 0
        var totalGold   = 0
        var totalTokens = 0
        val allLoot     = mutableListOf<LootDrop>()
        var wipedAt     = -1

        val Lr = dungeon.recommendedLevel

        for ((idx, template) in dungeon.encounters.withIndex()) {
            // Determine if rare slot spawns
            val effectiveType = if (template.isRareSlot) {
                if (rng.nextFloat() < 0.18f) EncounterType.RARE_BOSS else EncounterType.ELITE_PACK
            } else {
                template.type
            }

            val enemies = buildEnemies(effectiveType, Lr, difficulty)

            val encounterResult = simulateEncounter(
                combatParty    = initialCombatParty,
                enemies        = enemies,
                encounterIdx   = idx,
                encounterName  = template.name,
                encounterType  = effectiveType,
                dungeonLevel   = Lr,
                difficulty     = difficulty,
                lootPool       = lootPool,
            )

            allEncounterResults.add(encounterResult)
            totalMeters  = totalMeters.merge(encounterResult.meters)
            totalXp      += encounterResult.xpEarned
            totalGold    += encounterResult.goldEarned
            totalTokens  += encounterResult.tokensEarned
            allLoot      += encounterResult.lootDrops

            if (!encounterResult.won) {
                wipedAt = idx
                break
            }

            // Between encounters: restore HP 70%, mana 60%, reset cooldowns
            if (idx < dungeon.encounters.lastIndex) {
                for (c in initialCombatParty) {
                    if (!c.isAlive) {
                        // Soft-rez: characters dead for the run stay dead unless all survived
                        c.isAlive = false
                        c.hp = 0
                    } else {
                        c.hp   = (c.maxHp   * 0.70f).roundToInt().coerceAtLeast(1)
                        c.mana = (c.maxMana * 0.60f).roundToInt().coerceAtLeast(0)
                        c.tauntCd  = 0; c.aoeCd     = 0
                        c.healAoeCd = 0; c.buffCd   = 0; c.debuffCd = 0
                        c.buffTicks = 0; c.debuffTicks = 0
                    }
                }
            }
        }

        return RunSimResult(
            encounters        = allEncounterResults,
            totalMeters       = totalMeters,
            survived          = wipedAt == -1,
            totalXpEarned     = totalXp,
            totalGoldEarned   = totalGold,
            totalTokensEarned = totalTokens,
            allLoot           = allLoot,
            wipedEncounterIdx = wipedAt,
        )
    }

    // ─── Encounter simulation ─────────────────────────────────────────────────

    private fun simulateEncounter(
        combatParty:   MutableList<CombatChar>,
        enemies:       MutableList<CombatEnemy>,
        encounterIdx:  Int,
        encounterName: String,
        encounterType: EncounterType,
        dungeonLevel:  Int,
        difficulty:    Difficulty,
        lootPool:      List<Item>,
    ): EncounterSimResult {
        val meters = Meters()
        for (c in combatParty) {
            meters.entries[c.id] = CharMeterEntry(c.id, c.name, c.classType)
        }
        // Initialize threat tables
        for (e in enemies) {
            for (c in combatParty) e.threatTable[c.id] = 0f
        }

        val ticks     = mutableListOf<TickSnapshot>()
        val tickLog   = mutableListOf<LogEntry>()
        var tick      = 0
        val MAX_TICKS = 2000

        fun snapshot(extraLogs: List<LogEntry> = emptyList()): TickSnapshot {
            val logs = tickLog.toList() + extraLogs
            tickLog.clear()
            return TickSnapshot(
                tick        = tick,
                partyStates = combatParty.map { c ->
                    PartyMemberState(c.id, c.name, c.classType, c.role, c.hp, c.maxHp, c.mana, c.maxMana, c.isAlive)
                },
                enemyStates = enemies.map { e ->
                    val target = e.highestThreatTarget(combatParty.filter { it.isAlive })
                    EnemyState(e.id, e.name, e.type, e.hp, e.maxHp, e.isAlive, target?.id)
                },
                logLines    = logs,
            )
        }

        fun log(msg: String, type: LogType = LogType.NORMAL) =
            tickLog.add(LogEntry(tick, msg, type))

        while (tick < MAX_TICKS) {
            tick++

            val aliveParty  = combatParty.filter { it.isAlive }
            val aliveEnemies = enemies.filter { it.isAlive }
            if (aliveParty.isEmpty() || aliveEnemies.isEmpty()) break

            // ── Tick overhead: mana regen, countdown cooldowns ──────────────
            for (c in aliveParty) {
                val regen = (c.maxMana * 0.006f).roundToInt().coerceAtLeast(1)
                c.mana = (c.mana + regen).coerceAtMost(c.maxMana)
                if (c.tauntCd   > 0) c.tauntCd--
                if (c.aoeCd     > 0) c.aoeCd--
                if (c.healAoeCd > 0) c.healAoeCd--
                if (c.buffCd    > 0) c.buffCd--
                if (c.debuffCd  > 0) c.debuffCd--
                if (c.buffTicks > 0) c.buffTicks--
            }
            for (e in aliveEnemies) {
                if (e.debuffTicks > 0) e.debuffTicks--
            }

            // ── Party acts ────────────────────────────────────────────────────
            for (c in aliveParty) {
                val currentAliveEnemies = enemies.filter { it.isAlive }
                if (currentAliveEnemies.isEmpty()) break

                // Party-wide damage buff multiplier
                val partyBuffMult = if (c.buffTicks > 0) 1.08f else 1.0f

                when {
                    c.role.isTank()    -> performTankAction(c, currentAliveEnemies, combatParty, meters, partyBuffMult, ::log)
                    c.role.isHealer()  -> performHealerAction(c, combatParty, enemies.filter { it.isAlive }, meters, ::log)
                    c.role.isDPS()     -> performDPSAction(c, currentAliveEnemies, combatParty, enemies.filter { it.isAlive }, meters, partyBuffMult, ::log)
                    c.role.isSupport() -> performSupportAction(c, combatParty, enemies.filter { it.isAlive }, meters, partyBuffMult, ::log)
                }
            }

            // ── Enemies act ───────────────────────────────────────────────────
            val stillAliveParty = combatParty.filter { it.isAlive }
            for (e in enemies.filter { it.isAlive }) {
                val target = e.highestThreatTarget(stillAliveParty) ?: continue
                val rawDmg = e.damage
                val debuffMult = if (e.debuffTicks > 0) 0.92f else 1.0f
                val dr = target.armor.toFloat() / (target.armor + 50f * e.id.coerceAtLeast(1) + 400f)
                val effective = (rawDmg * debuffMult * (1f - dr)).roundToInt().coerceAtLeast(1)
                target.hp -= effective
                meters.taken(target.id, effective)
                log("${e.name} hits ${target.name} for $effective.", LogType.DAMAGE)
                if (target.hp <= 0) {
                    target.hp      = 0
                    target.isAlive = false
                    meters.death(target.id)
                    log("${target.name} has fallen!", LogType.DEATH)
                }
            }

            ticks.add(snapshot())

            // ── Check terminal conditions ────────────────────────────────────
            if (enemies.none { it.isAlive }) break
            if (combatParty.none { it.isAlive }) break
        }

        val won = enemies.none { it.isAlive }

        // Terminal log
        val finalMsg = if (won) "Victory! Encounter cleared." else "The party has been defeated."
        val finalType = if (won) LogType.SYSTEM else LogType.DEATH
        ticks.add(snapshot(listOf(LogEntry(tick, finalMsg, finalType))))

        // Gold per encounter by level
        val goldEarned = if (won) (dungeonLevel * 8 + difficulty.lootMult * 20).roundToInt() else 0
        val tokensEarned = if (won) encounterType.tokenReward else 0
        val xpPerEnemy   = (40 + dungeonLevel * 15)
        val xpEarned     = if (won) xpPerEnemy * encounterType.enemyCount else 0

        // Loot
        val lootDrops = if (won) rollLoot(encounterType, encounterIdx, dungeonLevel, difficulty, lootPool) else emptyList()

        return EncounterSimResult(
            encounterIndex = encounterIdx,
            encounterName  = encounterName,
            encounterType  = encounterType,
            ticks          = ticks,
            won            = won,
            meters         = meters,
            lootDrops      = lootDrops,
            goldEarned     = goldEarned,
            tokensEarned   = tokensEarned,
            xpEarned       = xpEarned,
        )
    }

    // ─── Character actions ────────────────────────────────────────────────────

    private fun performTankAction(
        c:          CombatChar,
        enemies:    List<CombatEnemy>,
        party:      List<CombatChar>,
        meters:     Meters,
        buffMult:   Float,
        log:        (String, LogType) -> Unit,
    ) {
        val target = enemies.maxByOrNull { it.hp } ?: return
        val manaCost = (c.maxMana * 0.06f).roundToInt()
        val manaFactor = if (c.mana >= manaCost) { c.mana -= manaCost; 1.0f } else 0.45f

        val rawDmg   = c.physDamage * 1.05f * manaFactor * buffMult
        val isCrit   = rng.nextFloat() < c.critChance
        val finalDmg = (rawDmg * (if (isCrit) 1.5f else 1.0f)).roundToInt().coerceAtLeast(1)
        val dr       = target.armor.toFloat() / (target.armor + 50f * c.level + 400f)
        val effective = (finalDmg * (1f - dr)).roundToInt().coerceAtLeast(1)

        target.hp -= effective
        if (target.hp <= 0) { target.hp = 0; target.isAlive = false; log("${target.name} has been slain!", LogType.DEATH) }
        meters.damage(c.id, effective)
        target.threatTable[c.id] = (target.threatTable[c.id] ?: 0f) + effective * 2.2f
        if (isCrit) log("${c.name} strikes ${target.name} for $effective! (CRIT)", LogType.DAMAGE)
        else log("${c.name} attacks ${target.name} for $effective.", LogType.DAMAGE)

        // Taunt: every 10 ticks, set threat to highest+1 on this enemy
        if (c.tauntCd == 0) {
            val highestThreat = target.threatTable.values.maxOrNull() ?: 0f
            target.threatTable[c.id] = highestThreat + 1f
            c.tauntCd = 10
            log("${c.name} Taunts ${target.name}!", LogType.ABILITY)
        }
    }

    private fun performHealerAction(
        c:       CombatChar,
        party:   List<CombatChar>,
        enemies: List<CombatEnemy>,
        meters:  Meters,
        log:     (String, LogType) -> Unit,
    ) {
        val aliveAllies = party.filter { it.isAlive }
        val belowSixty  = aliveAllies.filter { it.hp.toFloat() / it.maxHp < 0.60f }

        val healCoef = 1.35f
        val rawHeal  = c.spellDamage * 1.35f * healCoef
        val manaCost = (c.maxMana * 0.08f).roundToInt()
        val manaFactor = if (c.mana >= manaCost) { c.mana -= manaCost; 1.0f } else 0.45f
        val effectiveRawHeal = rawHeal * manaFactor

        // AoE heal if ≥3 below 60%
        if (c.role == Role.HEALER_AOE && c.healAoeCd == 0 && belowSixty.size >= 3) {
            val aoeAmount = (effectiveRawHeal * 0.70f).roundToInt().coerceAtLeast(1)
            for (ally in belowSixty) {
                val preHp = ally.hp
                ally.hp = (ally.hp + aoeAmount).coerceAtMost(ally.maxHp)
                val effective = ally.hp - preHp
                if (effective > 0) {
                    meters.healing(c.id, effective)
                    // Apply healing threat to all enemies
                    for (e in enemies) {
                        e.threatTable[c.id] = (e.threatTable[c.id] ?: 0f) + effective * 0.35f
                    }
                }
            }
            c.healAoeCd = 8
            log("${c.name} channels an area heal for ~$aoeAmount each.", LogType.HEAL)
            return
        }

        // Single target: lowest %HP ally (prefer tank < 75%)
        val tanks = aliveAllies.filter { it.role.isTank() }
        val tankNeedsHeal = tanks.any { it.hp.toFloat() / it.maxHp < 0.75f }
        val healTarget = if (tankNeedsHeal) {
            tanks.minByOrNull { it.hp.toFloat() / it.maxHp }!!
        } else {
            aliveAllies.minByOrNull { it.hp.toFloat() / it.maxHp } ?: return
        }

        val preHp  = healTarget.hp
        val amount = effectiveRawHeal.roundToInt().coerceAtLeast(1)
        healTarget.hp = (healTarget.hp + amount).coerceAtMost(healTarget.maxHp)
        val effective = healTarget.hp - preHp
        if (effective > 0) {
            meters.healing(c.id, effective)
            for (e in enemies) {
                e.threatTable[c.id] = (e.threatTable[c.id] ?: 0f) + effective * 0.35f
            }
            log("${c.name} heals ${healTarget.name} for $effective.", LogType.HEAL)
        } else {
            log("${c.name} heals ${healTarget.name} — overheal.", LogType.HEAL)
        }
    }

    private fun performDPSAction(
        c:        CombatChar,
        enemies:  List<CombatEnemy>,
        party:    List<CombatChar>,
        allEnemies: List<CombatEnemy>,
        meters:   Meters,
        buffMult: Float,
        log:      (String, LogType) -> Unit,
    ) {
        val manaCost = (c.maxMana * 0.06f).roundToInt()
        val manaFactor = if (c.mana >= manaCost) { c.mana -= manaCost; 1.0f } else 0.45f

        // AoE DPS (Druid DPS, Mage AoE, Hunter spread): use when aoeCd == 0 and ≥2 enemies
        val useAoe = c.aoeCd == 0 && enemies.size >= 2
        val abilityCoef = if (useAoe) 0.80f else 1.25f

        val rawDmg = when (c.classType.damageType) {
            DamageType.PHYS  -> c.physDamage  * abilityCoef * manaFactor * buffMult
            DamageType.MAGIC -> c.spellDamage * abilityCoef * manaFactor * buffMult
            DamageType.MIXED -> ((c.physDamage + c.spellDamage) / 2f) * abilityCoef * manaFactor * buffMult
        }

        val isCrit = rng.nextFloat() < c.critChance
        val dmgWithCrit = rawDmg * (if (isCrit) 1.5f else 1.0f)

        if (useAoe) {
            val aoeTargets = enemies.take(3)
            for (target in aoeTargets) {
                val dr = when (c.classType.damageType) {
                    DamageType.PHYS  -> target.armor.toFloat() / (target.armor + 50f * c.level + 400f)
                    DamageType.MAGIC -> target.magicArmor.toFloat() / (target.magicArmor + 50f * c.level + 400f)
                    DamageType.MIXED -> ((target.armor + target.magicArmor) / 2f) / ((target.armor + target.magicArmor) / 2f + 50f * c.level + 400f)
                }
                val effective = (dmgWithCrit * (1f - dr)).roundToInt().coerceAtLeast(1)
                target.hp -= effective
                if (target.hp <= 0) { target.hp = 0; target.isAlive = false; log("${target.name} is slain!", LogType.DEATH) }
                meters.damage(c.id, effective)
                target.threatTable[c.id] = (target.threatTable[c.id] ?: 0f) + effective * 1.0f
            }
            c.aoeCd = 4
            val critStr = if (isCrit) " (CRIT)" else ""
            log("${c.name} unleashes AoE hitting ${aoeTargets.size} enemies.$critStr", LogType.DAMAGE)
        } else {
            val target = enemies.minByOrNull { it.hp } ?: return
            val dr = when (c.classType.damageType) {
                DamageType.PHYS  -> target.armor.toFloat() / (target.armor + 50f * c.level + 400f)
                DamageType.MAGIC -> target.magicArmor.toFloat() / (target.magicArmor + 50f * c.level + 400f)
                DamageType.MIXED -> ((target.armor + target.magicArmor) / 2f) / ((target.armor + target.magicArmor) / 2f + 50f * c.level + 400f)
            }
            val effective = (dmgWithCrit * (1f - dr)).roundToInt().coerceAtLeast(1)
            target.hp -= effective
            if (target.hp <= 0) { target.hp = 0; target.isAlive = false; log("${target.name} is slain!", LogType.DEATH) }
            meters.damage(c.id, effective)
            target.threatTable[c.id] = (target.threatTable[c.id] ?: 0f) + effective * 1.0f
            val critStr = if (isCrit) " (CRIT)" else ""
            log("${c.name} deals $effective to ${target.name}.$critStr", LogType.DAMAGE)
        }
    }

    private fun performSupportAction(
        c:        CombatChar,
        party:    List<CombatChar>,
        enemies:  List<CombatEnemy>,
        meters:   Meters,
        buffMult: Float,
        log:      (String, LogType) -> Unit,
    ) {
        val aliveParty = party.filter { it.isAlive }

        // Apply party damage buff (CD 18 ticks, lasts 6)
        if (c.buffCd == 0) {
            for (ally in aliveParty) ally.buffTicks = 6
            c.buffCd = 18
            log("${c.name} empowers the party (+8% damage for 6 ticks)!", LogType.ABILITY)
        }
        // Apply enemy damage debuff (CD 18 ticks, lasts 6)
        if (c.debuffCd == 0 && enemies.isNotEmpty()) {
            for (e in enemies) e.debuffTicks = 6
            c.debuffCd = 18
            log("${c.name} weakens all enemies (-8% damage for 6 ticks)!", LogType.ABILITY)
        }

        // Fallback attack if nothing to buff/debuff
        val manaCost = (c.maxMana * 0.06f).roundToInt()
        val manaFactor = if (c.mana >= manaCost) { c.mana -= manaCost; 1.0f } else 0.45f
        val target = enemies.minByOrNull { it.hp } ?: return
        val rawDmg  = ((c.physDamage + c.spellDamage) / 2f) * 1.0f * manaFactor * buffMult
        val isCrit  = rng.nextFloat() < c.critChance
        val dmg     = rawDmg * (if (isCrit) 1.5f else 1.0f)
        val dr      = target.armor.toFloat() / (target.armor + 50f * c.level + 400f)
        val eff     = (dmg * (1f - dr)).roundToInt().coerceAtLeast(1)
        target.hp -= eff
        if (target.hp <= 0) { target.hp = 0; target.isAlive = false; log("${target.name} is slain!", LogType.DEATH) }
        meters.damage(c.id, eff)
        target.threatTable[c.id] = (target.threatTable[c.id] ?: 0f) + eff * 0.8f
    }

    // ─── Enemy generation ─────────────────────────────────────────────────────

    private fun buildEnemies(
        type:       EncounterType,
        Lr:         Int,
        difficulty: Difficulty,
    ): MutableList<CombatEnemy> {
        val baseHp   = (80 + 22 * (Lr - 1) + 0.35f * (Lr - 1) * (Lr - 1))
        val baseDmg  = (6 + 1.2f * (Lr - 1) + 0.02f * (Lr - 1) * (Lr - 1))
        val baseArmor = (20 + 2.2f * (Lr - 1) + 0.05f * (Lr - 1) * (Lr - 1)).toInt()

        val enemyNames = listOf(
            "Ashen Sentinel","Cinder Revenant","Ember Warden","Hollow Enforcer",
            "Void Specter","Forsaken Construct","Grim Shade","Iron Abomination",
            "Burning Golem","Dusty Wraith",
        )

        return (0 until type.enemyCount).map { i ->
            val name = if (type.enemyCount == 1) type.displayName
                       else "${enemyNames[i % enemyNames.size]} ${i + 1}"
            CombatEnemy(
                id         = i,
                name       = name,
                type       = type.enemyType,
                hp         = (baseHp * type.hpMult * difficulty.hpMult).roundToInt().coerceAtLeast(10),
                maxHp      = (baseHp * type.hpMult * difficulty.hpMult).roundToInt().coerceAtLeast(10),
                damage     = baseDmg * type.damageMult * difficulty.damageMult,
                armor      = (baseArmor * 0.7f).toInt(),
                magicArmor = (baseArmor * 0.7f).toInt(),
            )
        }.toMutableList()
    }

    // ─── Loot rolling ─────────────────────────────────────────────────────────

    private fun rollLoot(
        type:        EncounterType,
        encounterIdx: Int,
        dungeonLevel: Int,
        difficulty:  Difficulty,
        lootPool:    List<Item>,
    ): List<LootDrop> {
        if (lootPool.isEmpty()) return emptyList()

        val drops = mutableListOf<LootDrop>()
        val dropRoll = rng.nextFloat()
        val shouldDrop = when {
            type.dropChance == 0.0f -> true          // guaranteed
            dropRoll < type.dropChance -> true
            else -> false
        }
        if (!shouldDrop) return emptyList()

        val count = rng.nextInt(type.minDrops, type.maxDrops + 1).coerceAtLeast(1)
        val rarityWeights = when (type) {
            EncounterType.RARE_BOSS  -> listOf(20, 35, 35, 10)
            EncounterType.FINAL_BOSS -> listOf(30, 33, 28, 9)
            else                     -> listOf(55, 28, 14, 3)
        }
        val rarities = listOf(ItemRarity.COMMON, ItemRarity.UNCOMMON, ItemRarity.RARE, ItemRarity.EPIC)

        val shuffled = lootPool.shuffled(rng)
        var picked = 0
        for (item in shuffled) {
            if (picked >= count) break
            // Rarity gate: roll against rarity distribution
            val rarIdx  = rarities.indexOf(item.rarity)
            val weight  = rarityWeights[rarIdx]
            val total   = rarityWeights.sum()
            val rollVal = rng.nextInt(total)
            if (rollVal < weight) {
                drops.add(LootDrop(item, encounterIdx))
                picked++
            }
        }
        // Guarantee at least 1 if none picked (for guaranteed encounters)
        if (drops.isEmpty() && type.dropChance == 0.0f) {
            val fallback = shuffled.firstOrNull()
            if (fallback != null) drops.add(LootDrop(fallback, encounterIdx))
        }
        return drops
    }
}
