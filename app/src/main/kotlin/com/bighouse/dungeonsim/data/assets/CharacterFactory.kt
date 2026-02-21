package com.bighouse.dungeonsim.data.assets

import com.bighouse.dungeonsim.data.model.*

/**
 * Generates the 24 starter characters (levels 1–12, 3 per class).
 * Names are drawn from fantasy name pools with no copyrighted references.
 */
object CharacterFactory {

    private val maleNames    = listOf("Aldric","Brennan","Cael","Dorin","Eamon","Farid","Gorin","Harlen","Idris","Jorven","Kael","Lordan","Maren","Nolath","Orin","Piran","Quell","Roven","Salar","Tarek","Ulric","Varen","Wren","Xael")
    private val femaleNames  = listOf("Aela","Briann","Cera","Dwyn","Elara","Fyra","Gwen","Hira","Issa","Jynn","Kira","Lyra","Mira","Nessa","Orla","Pyra","Quara","Rysa","Syra","Tara","Una","Vara","Wyra","Xara")
    private val surnames     = listOf("Ashveil","Blackthorn","Cindermoor","Duskwood","Emberfell","Frostmire","Grimwall","Hollowstone","Ironvale","Jadewing","Keldmere","Lichway","Moonveil","Nighthollow","Obsidian","Palewind","Quillmoor","Ravenmere","Shadowfen","Thornwall","Umbermist","Voidfen","Whiterock","Xenvale")

    /** Generate 24 starter characters: 3 per class, levels 1/5/8 within each class, staggered. */
    fun generateStarterRoster(): List<Character> {
        val levels = listOf(1, 4, 7, 10, 12, 1, 4, 7)   // spread across 8 classes × 3 chars = 24
        var nameIdx = 0

        return ClassType.values().flatMapIndexed { classIdx, classType ->
            val classLevels = listOf(
                levels[(classIdx * 3 + 0) % levels.size],
                levels[(classIdx * 3 + 1) % levels.size],
                levels[(classIdx * 3 + 2) % levels.size],
            )
            classLevels.mapIndexed { i, level ->
                val firstName = if ((classIdx + i) % 2 == 0) maleNames[nameIdx % maleNames.size]
                                else femaleNames[nameIdx % femaleNames.size]
                val surname   = surnames[nameIdx % surnames.size]
                nameIdx++

                buildCharacter(
                    id        = (classIdx * 3 + i + 1).toLong(),
                    name      = "$firstName $surname",
                    classType = classType,
                    level     = level,
                )
            }
        }
    }

    fun buildCharacter(
        id:        Long,
        name:      String,
        classType: ClassType,
        level:     Int,
        xp:        Long = 0L,
    ): Character {
        val curve = CLASS_CURVES[classType]!!
        val baseStats = ItemStats(
            hp          = curve.maxHp(level),
            mana        = curve.maxMana(level),
            armor       = curve.armor(level),
            magicArmor  = curve.magicArmor(level),
            physDamage  = curve.physDamage(level),
            spellDamage = curve.spellDamage(level),
            critChance  = curve.critChance(level),
        )
        return Character(
            id           = id,
            name         = name,
            classType    = classType,
            level        = level,
            xp           = xp,
            baseStats    = baseStats,
            equippedItems = emptyMap(),
            bagItemIds   = emptyList(),
        )
    }
}
