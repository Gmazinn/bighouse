package com.bighouse.dungeonsim.data.db.entity

import androidx.room.*
import com.bighouse.dungeonsim.data.model.*

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id:      Long = 0,
    val name:    String,
    val slot:    ItemSlot,
    val ilvl:    Int,
    val rarity:  ItemRarity,
    val stats:   ItemStats,
    val binding: ItemBinding,
    val profile: ItemProfile,
    /** Non-zero when item is in a character's bag (not vault). */
    val ownerCharId: Long = 0L,
    /** True when item has been moved to the shared vault. */
    val inVault: Boolean = false,
)

fun ItemEntity.toDomain() = Item(
    id      = id,
    name    = name,
    slot    = slot,
    ilvl    = ilvl,
    rarity  = rarity,
    stats   = stats,
    binding = binding,
    profile = profile,
)

fun Item.toEntity(ownerCharId: Long = 0L, inVault: Boolean = false) = ItemEntity(
    id          = id,
    name        = name,
    slot        = slot,
    ilvl        = ilvl,
    rarity      = rarity,
    stats       = stats,
    binding     = binding,
    profile     = profile,
    ownerCharId = ownerCharId,
    inVault     = inVault,
)
