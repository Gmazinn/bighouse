package com.bighouse.dungeonsim.data.db

import androidx.room.TypeConverter
import com.bighouse.dungeonsim.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter fun fromItemStats(v: ItemStats): String = gson.toJson(v)
    @TypeConverter fun toItemStats(s: String): ItemStats  = gson.fromJson(s, ItemStats::class.java)

    @TypeConverter fun fromSlotMap(v: Map<String, Long>): String = gson.toJson(v)
    @TypeConverter fun toSlotMap(s: String): Map<String, Long> {
        val type = object : TypeToken<Map<String, Long>>() {}.type
        return gson.fromJson(s, type) ?: emptyMap()
    }

    @TypeConverter fun fromLongList(v: List<Long>): String = gson.toJson(v)
    @TypeConverter fun toLongList(s: String): List<Long> {
        val type = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(s, type) ?: emptyList()
    }

    @TypeConverter fun fromItemSlot(v: ItemSlot): String     = v.name
    @TypeConverter fun toItemSlot(s: String): ItemSlot       = ItemSlot.valueOf(s)

    @TypeConverter fun fromItemRarity(v: ItemRarity): String  = v.name
    @TypeConverter fun toItemRarity(s: String): ItemRarity    = ItemRarity.valueOf(s)

    @TypeConverter fun fromItemBinding(v: ItemBinding): String = v.name
    @TypeConverter fun toItemBinding(s: String): ItemBinding   = ItemBinding.valueOf(s)

    @TypeConverter fun fromItemProfile(v: ItemProfile): String = v.name
    @TypeConverter fun toItemProfile(s: String): ItemProfile   = ItemProfile.valueOf(s)

    @TypeConverter fun fromClassType(v: ClassType): String    = v.name
    @TypeConverter fun toClassType(s: String): ClassType      = ClassType.valueOf(s)
}
