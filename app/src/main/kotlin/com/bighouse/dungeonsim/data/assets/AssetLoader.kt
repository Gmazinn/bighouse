package com.bighouse.dungeonsim.data.assets

import android.content.Context
import com.bighouse.dungeonsim.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlin.math.roundToInt

/** Loads and parses JSON assets into domain objects. */
class AssetLoader(private val context: Context) {

    private val gson = Gson()

    fun loadLootPool(): List<Item> {
        val json = context.assets.open("loot_tables.json")
            .bufferedReader().use { it.readText() }
        val root = gson.fromJson(json, JsonObject::class.java)
        val items = root.getAsJsonArray("items")
        return items.map { elem ->
            val obj = elem.asJsonObject
            val slot    = ItemSlot.valueOf(obj.get("slot").asString)
            val ilvl    = obj.get("ilvl").asInt
            val rarity  = ItemRarity.valueOf(obj.get("rarity").asString)
            val profile = ItemProfile.valueOf(obj.get("profile").asString)
            val binding = ItemBinding.valueOf(obj.get("binding").asString)
            Item(
                id      = obj.get("id").asLong,
                name    = obj.get("name").asString,
                slot    = slot,
                ilvl    = ilvl,
                rarity  = rarity,
                stats   = computeItemStats(slot, ilvl, rarity, profile),
                binding = binding,
                profile = profile,
            )
        }
    }

    /**
     * Budget formula:
     *   budget(ilvl) = round(6 + 1.8*ilvl + 0.12*ilvl^2)
     *   finalBudget  = budget * slotWeight * rarityMult
     * Distribution by profile (% of finalBudget converted per stat):
     *   HP +12 pts, Mana +10, Armor/MagicArmor +3, PhysDmg/SpellDmg +1.0, CritChance +0.0015
     */
    fun computeItemStats(slot: ItemSlot, ilvl: Int, rarity: ItemRarity, profile: ItemProfile): ItemStats {
        val budget = (6 + 1.8f * ilvl + 0.12f * ilvl * ilvl).roundToInt()
        val finalBudget = (budget * slot.slotWeight * rarity.rarityMult).roundToInt()

        // Profile allocations: map stat -> fraction of budget
        val alloc: Map<String, Float> = when (profile) {
            ItemProfile.TANK      -> mapOf("hp" to 0.40f, "armor" to 0.25f, "magicArmor" to 0.15f, "physDamage" to 0.10f, "mana" to 0.10f)
            ItemProfile.HEALER    -> mapOf("mana" to 0.35f, "spellDamage" to 0.30f, "hp" to 0.15f, "magicArmor" to 0.15f, "crit" to 0.05f)
            ItemProfile.PHYS_DPS  -> mapOf("physDamage" to 0.45f, "crit" to 0.25f, "hp" to 0.15f, "armor" to 0.15f)
            ItemProfile.SPELL_DPS -> mapOf("spellDamage" to 0.50f, "crit" to 0.20f, "mana" to 0.20f, "hp" to 0.10f)
            ItemProfile.MIXED     -> mapOf("physDamage" to 0.25f, "spellDamage" to 0.25f, "crit" to 0.20f, "hp" to 0.15f, "armor" to 0.15f)
            ItemProfile.SUPPORT   -> mapOf("mana" to 0.30f, "spellDamage" to 0.20f, "hp" to 0.20f, "armor" to 0.15f, "crit" to 0.15f)
        }

        fun pts(key: String) = ((alloc[key] ?: 0f) * finalBudget).roundToInt()

        // Convert budget points to actual stats
        val hpPts         = pts("hp")
        val manaPts       = pts("mana")
        val armorPts      = pts("armor")
        val mArmorPts     = pts("magicArmor")
        val physDmgPts    = pts("physDamage")
        val spellDmgPts   = pts("spellDamage")
        val critPts       = pts("crit")

        return ItemStats(
            hp          = hpPts    * 12,
            mana        = manaPts  * 10,
            armor       = armorPts * 3,
            magicArmor  = mArmorPts * 3,
            physDamage  = physDmgPts.toFloat(),
            spellDamage = spellDmgPts.toFloat(),
            critChance  = critPts * 0.0015f,
        )
    }
}
