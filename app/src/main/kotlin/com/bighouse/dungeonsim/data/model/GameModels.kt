package com.bighouse.dungeonsim.data.model

import androidx.compose.ui.graphics.Color

// ─── Enumerations ────────────────────────────────────────────────────────────

enum class DamageType { PHYS, MAGIC, MIXED }

enum class Role(val displayName: String, val shortLabel: String) {
    TANK("Tank", "T"),
    HEALER("Healer", "H"),
    HEALER_AOE("Healer", "H"),
    DPS_PHYS("DPS", "D"),
    DPS_SPELL("DPS", "D"),
    DPS_MIXED("DPS", "D"),
    SUPPORT("Support", "S");

    fun isTank() = this == TANK
    fun isHealer() = this == HEALER || this == HEALER_AOE
    fun isDPS() = this == DPS_PHYS || this == DPS_SPELL || this == DPS_MIXED
    fun isSupport() = this == SUPPORT
}

enum class ClassType(
    val displayName: String,
    val colorValue: Long,
    val role: Role,
    val damageType: DamageType,
) {
    GUARDIAN("Guardian", 0xFFB8860BL, Role.TANK,       DamageType.PHYS),
    MYSTIC  ("Mystic",   0xFF4169E1L, Role.TANK,       DamageType.MAGIC),
    CLERIC  ("Cleric",   0xFFFFD700L, Role.HEALER,     DamageType.MAGIC),
    DRUID   ("Druid",    0xFF228B22L, Role.HEALER_AOE, DamageType.MAGIC),
    ROGUE   ("Rogue",    0xFFFF8C00L, Role.DPS_PHYS,   DamageType.PHYS),
    MAGE    ("Mage",     0xFF9B59B6L, Role.DPS_SPELL,  DamageType.MAGIC),
    HUNTER  ("Hunter",   0xFF27AE60L, Role.DPS_MIXED,  DamageType.MIXED),
    BARD    ("Bard",     0xFFFF69B4L, Role.SUPPORT,    DamageType.MIXED);

    fun color() = Color(colorValue)
}

enum class ItemSlot(val displayName: String, val slotWeight: Float) {
    WEAPON ("Weapon",  1.60f),
    HEAD   ("Head",    1.05f),
    CHEST  ("Chest",   1.25f),
    LEGS   ("Legs",    1.15f),
    TRINKET("Trinket", 1.10f),
}

enum class ItemRarity(val displayName: String, val colorValue: Long, val rarityMult: Float) {
    COMMON  ("Common",   0xFFAAAAAA, 1.00f),
    UNCOMMON("Uncommon", 0xFF1EFF00, 1.15f),
    RARE    ("Rare",     0xFF0070DD, 1.35f),
    EPIC    ("Epic",     0xFFA335EE, 1.60f);

    fun color() = Color(colorValue)
}

enum class ItemBinding { BOP, BOE }

enum class ItemProfile { TANK, HEALER, PHYS_DPS, SPELL_DPS, MIXED, SUPPORT }

enum class Difficulty(
    val displayName: String,
    val hpMult: Float,
    val damageMult: Float,
    val lootMult: Float,
    val unlockRequires: Int,   // clears of previous tier required
) {
    NORMAL ("Normal", 1.0f, 1.00f, 1.00f, 0),
    HEROIC ("Heroic", 1.5f, 1.35f, 1.25f, 2),
    MYTHIC ("Mythic", 2.2f, 1.80f, 1.60f, 3),
}

enum class EnemyType(val displayName: String) {
    MOB       ("Mob"),
    ELITE     ("Elite"),
    MINI_BOSS ("Mini-Boss"),
    RARE_BOSS ("Rare Boss"),
    FINAL_BOSS("Final Boss"),
}

enum class EncounterType(
    val displayName: String,
    val enemyCount: Int,
    val hpMult: Float,
    val damageMult: Float,
    val enemyType: EnemyType,
    val dropChance: Float,       // 0.0 = guaranteed
    val minDrops: Int,
    val maxDrops: Int,
    val tokenReward: Int,
) {
    AOE_PACK  ("Assault Pack",   8, 0.65f, 0.75f, EnemyType.MOB,        0.12f, 0, 1, 0),
    ELITE_PACK("Elite Guards",   4, 1.35f, 1.15f, EnemyType.ELITE,      0.20f, 0, 1, 0),
    MINI_BOSS ("Ancient Warden", 1, 4.00f, 1.70f, EnemyType.MINI_BOSS,  0.00f, 1, 1, 1),
    RARE_BOSS ("Forsaken One",   1, 6.00f, 2.20f, EnemyType.RARE_BOSS,  0.00f, 2, 2, 2),
    FINAL_BOSS("Ashen Sovereign",1, 7.00f, 2.10f, EnemyType.FINAL_BOSS, 0.00f, 1, 2, 2),
}

// ─── Stats & Items ────────────────────────────────────────────────────────────

data class ItemStats(
    val hp:          Int   = 0,
    val mana:        Int   = 0,
    val armor:       Int   = 0,
    val magicArmor:  Int   = 0,
    val physDamage:  Float = 0f,
    val spellDamage: Float = 0f,
    val critChance:  Float = 0f,
) {
    operator fun plus(other: ItemStats) = ItemStats(
        hp          = hp          + other.hp,
        mana        = mana        + other.mana,
        armor       = armor       + other.armor,
        magicArmor  = magicArmor  + other.magicArmor,
        physDamage  = physDamage  + other.physDamage,
        spellDamage = spellDamage + other.spellDamage,
        critChance  = critChance  + other.critChance,
    )
}

data class Item(
    val id:      Long,
    val name:    String,
    val slot:    ItemSlot,
    val ilvl:    Int,
    val rarity:  ItemRarity,
    val stats:   ItemStats,
    val binding: ItemBinding,
    val profile: ItemProfile,
) {
    fun gearScoreContribution(): Float =
        ilvl.toFloat() * rarity.rarityMult * slot.slotWeight

    fun shortStatSummary(): String = buildString {
        if (stats.hp > 0)          append("+${stats.hp} HP  ")
        if (stats.mana > 0)        append("+${stats.mana} MP  ")
        if (stats.armor > 0)       append("+${stats.armor} Arm  ")
        if (stats.magicArmor > 0)  append("+${stats.magicArmor} MAr  ")
        if (stats.physDamage > 0)  append("+${"%.1f".format(stats.physDamage)} PDmg  ")
        if (stats.spellDamage > 0) append("+${"%.1f".format(stats.spellDamage)} SDmg  ")
        if (stats.critChance > 0)  append("+${"%.1f".format(stats.critChance * 100)}% Crit")
    }.trimEnd()
}

// ─── Character ───────────────────────────────────────────────────────────────

data class ClassStatCurve(
    val baseHp: Int,          val linearHp: Float,          val quadHp: Float,
    val baseMana: Int,        val linearMana: Float,        val quadMana: Float,
    val baseArmor: Int,       val linearArmor: Float,       val quadArmor: Float,
    val baseMagicArmor: Int,  val linearMagicArmor: Float,  val quadMagicArmor: Float,
    val basePhysDmg: Float,   val linearPhysDmg: Float,     val quadPhysDmg: Float,
    val baseSpellDmg: Float,  val linearSpellDmg: Float,    val quadSpellDmg: Float,
    val baseCrit: Float,      val linearCrit: Float,        val quadCrit: Float,
) {
    private fun calc(base: Number, linear: Float, quad: Float, level: Int): Float {
        val L = (level - 1).toFloat()
        return base.toFloat() + linear * L + quad * L * L
    }

    fun maxHp(level: Int)         = calc(baseHp,         linearHp,         quadHp,         level).toInt()
    fun maxMana(level: Int)       = calc(baseMana,       linearMana,       quadMana,       level).toInt()
    fun armor(level: Int)         = calc(baseArmor,      linearArmor,      quadArmor,      level).toInt()
    fun magicArmor(level: Int)    = calc(baseMagicArmor, linearMagicArmor, quadMagicArmor, level).toInt()
    fun physDamage(level: Int)    = calc(basePhysDmg,    linearPhysDmg,    quadPhysDmg,    level)
    fun spellDamage(level: Int)   = calc(baseSpellDmg,   linearSpellDmg,   quadSpellDmg,   level)
    fun critChance(level: Int)    = calc(baseCrit,       linearCrit,       quadCrit,       level)
}

val CLASS_CURVES: Map<ClassType, ClassStatCurve> = mapOf(
    ClassType.GUARDIAN to ClassStatCurve(220,18f,0.80f,  80,4f,0.10f,  40,3.5f,0.15f, 15,1.5f,0.05f, 12f,1.80f,0.040f,  4f,0.50f,0.010f, 0.050f,0.0020f,0.000020f),
    ClassType.MYSTIC   to ClassStatCurve(200,16f,0.70f, 120,8f,0.20f,  20,2.0f,0.08f, 40,3.5f,0.15f,  6f,0.80f,0.020f, 12f,2.00f,0.050f, 0.050f,0.0020f,0.000020f),
    ClassType.CLERIC   to ClassStatCurve(160,12f,0.50f, 160,12f,0.30f, 20,2.0f,0.06f, 25,2.5f,0.08f,  6f,0.80f,0.010f, 16f,2.50f,0.060f, 0.050f,0.0020f,0.000020f),
    ClassType.DRUID    to ClassStatCurve(170,13f,0.55f, 150,11f,0.28f, 22,2.2f,0.07f, 22,2.2f,0.07f,  8f,1.00f,0.020f, 14f,2.20f,0.055f, 0.050f,0.0020f,0.000020f),
    ClassType.ROGUE    to ClassStatCurve(150,11f,0.45f,  60,3f,0.08f,  18,1.8f,0.05f, 10,1.0f,0.03f, 20f,3.00f,0.080f,  4f,0.40f,0.010f, 0.080f,0.0030f,0.000040f),
    ClassType.MAGE     to ClassStatCurve(130, 9f,0.35f, 200,15f,0.40f,  8,0.8f,0.02f, 15,1.5f,0.04f,  4f,0.50f,0.010f, 22f,3.50f,0.100f, 0.060f,0.0025f,0.000030f),
    ClassType.HUNTER   to ClassStatCurve(155,11f,0.45f, 100,7f,0.15f,  16,1.6f,0.04f, 12,1.2f,0.03f, 16f,2.50f,0.060f, 12f,2.00f,0.040f, 0.070f,0.0028f,0.000035f),
    ClassType.BARD     to ClassStatCurve(145,10f,0.40f, 140,10f,0.25f, 14,1.4f,0.04f, 16,1.6f,0.045f,10f,1.50f,0.030f, 12f,1.80f,0.040f, 0.050f,0.0020f,0.000020f),
)

data class Character(
    val id:           Long,
    val name:         String,
    val classType:    ClassType,
    val level:        Int,
    val xp:           Long,
    val baseStats:    ItemStats,       // from class curve at this level
    val equippedItems: Map<ItemSlot, Long> = emptyMap(),   // slot -> item id
    val bagItemIds:   List<Long> = emptyList(),            // unequipped items
) {
    val role: Role get() = classType.role

    fun xpToNext(): Long = (120 + 45 * level + 18 * level * level).toLong()

    fun gearScore(equippedItemList: List<Item>): Float =
        if (equippedItemList.isEmpty()) 0f
        else equippedItemList.sumOf { it.gearScoreContribution().toDouble() }.toFloat() /
                equippedItemList.size
}

// ─── Dungeon / Encounter content ─────────────────────────────────────────────

data class EncounterTemplate(
    val name:          String,
    val type:          EncounterType,
    val rareBossChance: Float = 0.18f, // only used when type is RARE slot
    val isRareSlot:    Boolean = false,
)

data class Dungeon(
    val id:                 String,
    val name:               String,
    val recommendedLevel:   Int,
    val encounters:         List<EncounterTemplate>,
) {
    companion object {
        val CINDER_CRYPT = Dungeon(
            id = "cinder_crypt",
            name = "Cinder Crypt",
            recommendedLevel = 10,
            encounters = listOf(
                EncounterTemplate("Ember Sentinels",   EncounterType.AOE_PACK),
                EncounterTemplate("Ashen Warband",     EncounterType.ELITE_PACK),
                EncounterTemplate("The Vault Warden",  EncounterType.MINI_BOSS),
                EncounterTemplate("Rare Encounter",    EncounterType.ELITE_PACK, isRareSlot = true),
                EncounterTemplate("The Ashen Sovereign",EncounterType.FINAL_BOSS),
            ),
        )
    }
}

// ─── Progress / Economy ───────────────────────────────────────────────────────

data class GameProgress(
    val normalClears:  Int = 0,
    val heroicClears:  Int = 0,
    val mythicClears:  Int = 0,
    val gold:          Int = 0,
    val bossTokens:    Int = 0,
    val schemaVersion: Int = 1,
) {
    fun availableDifficulties(): List<Difficulty> = buildList {
        add(Difficulty.NORMAL)
        if (normalClears >= Difficulty.HEROIC.unlockRequires) add(Difficulty.HEROIC)
        if (heroicClears >= Difficulty.MYTHIC.unlockRequires) add(Difficulty.MYTHIC)
    }

    fun tokenCostForSlot(slot: ItemSlot): Int = when (slot) {
        ItemSlot.WEAPON  -> 8
        ItemSlot.CHEST   -> 6
        ItemSlot.LEGS    -> 6
        ItemSlot.HEAD    -> 5
        ItemSlot.TRINKET -> 6
    }

    fun repairCost(avgLevel: Int, currentGold: Int): Int =
        ((currentGold * 0.05f).toInt() + 2 * avgLevel).coerceAtLeast(1)

    fun mercCost(recommendedLevel: Int): Int = 10 + 3 * recommendedLevel
}
