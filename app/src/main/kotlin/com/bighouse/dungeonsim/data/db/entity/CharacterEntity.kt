package com.bighouse.dungeonsim.data.db.entity

import androidx.room.*
import com.bighouse.dungeonsim.data.model.*

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id:           Long = 0,
    val name:         String,
    val classType:    ClassType,
    val level:        Int,
    val xp:           Long,
    // Equipped items stored as JSON: Map<ItemSlot.name, itemId>
    val equippedItemsJson: Map<String, Long> = emptyMap(),
    // Bag item IDs (unequipped, character-private non-vault items)
    val bagItemIdsJson: List<Long> = emptyList(),
)

fun CharacterEntity.toDomain(ownedItems: List<ItemEntity>): Character {
    val equippedBySlot = equippedItemsJson.mapKeys { ItemSlot.valueOf(it.key) }
    val equippedItemEntities = ownedItems.filter { it.id in equippedBySlot.values }

    // Compute base stats from class curve at this level
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
        equippedItems = equippedBySlot,
        bagItemIds   = bagItemIdsJson,
    )
}

fun Character.toEntity() = CharacterEntity(
    id                = id,
    name              = name,
    classType         = classType,
    level             = level,
    xp                = xp,
    equippedItemsJson = equippedItems.mapKeys { it.key.name },
    bagItemIdsJson    = bagItemIds,
)
