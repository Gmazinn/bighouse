package com.bighouse.dungeonsim.data.db.entity

import androidx.room.*
import com.bighouse.dungeonsim.data.model.GameProgress

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey
    val id:            Int = 1,  // single-row sentinel
    val normalClears:  Int = 0,
    val heroicClears:  Int = 0,
    val mythicClears:  Int = 0,
    val gold:          Int = 500,
    val bossTokens:    Int = 0,
    val schemaVersion: Int = 1,
)

fun ProgressEntity.toDomain() = GameProgress(
    normalClears  = normalClears,
    heroicClears  = heroicClears,
    mythicClears  = mythicClears,
    gold          = gold,
    bossTokens    = bossTokens,
    schemaVersion = schemaVersion,
)

fun GameProgress.toEntity() = ProgressEntity(
    normalClears  = normalClears,
    heroicClears  = heroicClears,
    mythicClears  = mythicClears,
    gold          = gold,
    bossTokens    = bossTokens,
    schemaVersion = schemaVersion,
)
