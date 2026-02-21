package com.bighouse.dungeonsim.data.repository

import com.bighouse.dungeonsim.data.assets.AssetLoader
import com.bighouse.dungeonsim.data.assets.CharacterFactory
import com.bighouse.dungeonsim.data.db.AppDatabase
import com.bighouse.dungeonsim.data.db.entity.*
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class GameRepository(
    private val db:          AppDatabase,
    private val assetLoader: AssetLoader,
) {
    private val characterDao = db.characterDao()
    private val itemDao      = db.itemDao()
    private val progressDao  = db.progressDao()

    // ─── Seed / init ─────────────────────────────────────────────────────────

    suspend fun initIfEmpty() = withContext(Dispatchers.IO) {
        if (characterDao.count() == 0) {
            val starters = CharacterFactory.generateStarterRoster()
            characterDao.upsertAll(starters.map { it.toEntity() })
        }
        if (progressDao.get() == null) {
            progressDao.upsert(ProgressEntity())
        }
    }

    // ─── Characters ──────────────────────────────────────────────────────────

    fun observeCharacters(): Flow<List<Character>> =
        characterDao.observeAll()
            .combine(itemDao.observeAll()) { chars, items ->
                chars.map { ce -> ce.toDomain(items.filter { it.ownerCharId == ce.id || it.id in (ce.equippedItemsJson.values) }) }
            }
            .flowOn(Dispatchers.IO)

    suspend fun getCharacters(): List<Character> = withContext(Dispatchers.IO) {
        val items = itemDao.getAll()
        characterDao.getAll().map { ce ->
            ce.toDomain(items.filter { it.ownerCharId == ce.id || it.id in ce.equippedItemsJson.values })
        }
    }

    suspend fun getCharacter(id: Long): Character? = withContext(Dispatchers.IO) {
        val ce = characterDao.getById(id) ?: return@withContext null
        val items = itemDao.getAll()
        ce.toDomain(items.filter { it.ownerCharId == id || it.id in ce.equippedItemsJson.values })
    }

    suspend fun saveCharacter(character: Character) = withContext(Dispatchers.IO) {
        characterDao.upsert(character.toEntity())
    }

    suspend fun saveCharacters(characters: List<Character>) = withContext(Dispatchers.IO) {
        characterDao.upsertAll(characters.map { it.toEntity() })
    }

    // ─── Items ────────────────────────────────────────────────────────────────

    suspend fun getAllItems(): List<Item> = withContext(Dispatchers.IO) {
        itemDao.getAll().map { it.toDomain() }
    }

    suspend fun getItemsForCharacter(charId: Long): List<Item> = withContext(Dispatchers.IO) {
        itemDao.getByOwner(charId).map { it.toDomain() }
    }

    fun observeVaultItems(): Flow<List<Item>> =
        itemDao.observeVault()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    suspend fun getVaultItems(): List<Item> = withContext(Dispatchers.IO) {
        itemDao.getVault().map { it.toDomain() }
    }

    suspend fun addItemToCharacterBag(item: Item, charId: Long) = withContext(Dispatchers.IO) {
        val newId = itemDao.upsert(item.toEntity(ownerCharId = charId))
        val ce    = characterDao.getById(charId) ?: return@withContext
        val newBag = ce.bagItemIdsJson + newId
        characterDao.upsert(ce.copy(bagItemIdsJson = newBag))
    }

    suspend fun equipItem(charId: Long, item: Item) = withContext(Dispatchers.IO) {
        val ce = characterDao.getById(charId) ?: return@withContext
        val slotKey = item.slot.name
        val existing = ce.equippedItemsJson[slotKey]
        val newEquipped = ce.equippedItemsJson.toMutableMap()

        if (existing != null) {
            // Move old item to bag
            val newBag = (ce.bagItemIdsJson + existing).distinct()
            newEquipped[slotKey] = item.id
            characterDao.upsert(ce.copy(equippedItemsJson = newEquipped, bagItemIdsJson = newBag))
        } else {
            newEquipped[slotKey] = item.id
            characterDao.upsert(ce.copy(equippedItemsJson = newEquipped))
        }
        itemDao.assignToChar(item.id, charId)
    }

    suspend fun moveToVault(itemId: Long) = withContext(Dispatchers.IO) {
        itemDao.moveToVault(itemId)
        // Remove from any character's bag
        for (ce in characterDao.getAll()) {
            if (itemId in ce.bagItemIdsJson) {
                characterDao.upsert(ce.copy(bagItemIdsJson = ce.bagItemIdsJson - itemId))
            }
        }
    }

    suspend fun saveNewItems(items: List<Item>, runPartyIds: Set<Long>) = withContext(Dispatchers.IO) {
        for (item in items) {
            val isBoP = item.binding == ItemBinding.BOP
            if (isBoP) {
                // BOP: assign to bag of first run participant (player must equip manually)
                val charId = runPartyIds.firstOrNull() ?: 0L
                val newId = itemDao.upsert(item.toEntity(ownerCharId = charId))
                if (charId != 0L) {
                    val ce = characterDao.getById(charId)
                    if (ce != null) {
                        characterDao.upsert(ce.copy(bagItemIdsJson = ce.bagItemIdsJson + newId))
                    }
                }
            } else {
                // BOE: goes straight to vault
                itemDao.upsert(item.toEntity(inVault = true))
            }
        }
    }

    // ─── Progress / Economy ───────────────────────────────────────────────────

    fun observeProgress(): Flow<GameProgress> =
        progressDao.observe()
            .map { it?.toDomain() ?: GameProgress() }
            .flowOn(Dispatchers.IO)

    suspend fun getProgress(): GameProgress = withContext(Dispatchers.IO) {
        progressDao.get()?.toDomain() ?: GameProgress()
    }

    suspend fun saveProgress(progress: GameProgress) = withContext(Dispatchers.IO) {
        progressDao.upsert(progress.toEntity())
    }

    suspend fun applyRunRewards(
        result:       RunSimResult,
        partyCharIds: List<Long>,
        difficulty:   Difficulty,
        lootPool:     List<Item>,
    ) = withContext(Dispatchers.IO) {
        val current = progressDao.get()?.toDomain() ?: GameProgress()
        val updated = if (result.survived) {
            val newNormal  = if (difficulty == Difficulty.NORMAL)  current.normalClears  + 1 else current.normalClears
            val newHeroic  = if (difficulty == Difficulty.HEROIC)  current.heroicClears  + 1 else current.heroicClears
            val newMythic  = if (difficulty == Difficulty.MYTHIC)  current.mythicClears  + 1 else current.mythicClears
            current.copy(
                normalClears = newNormal,
                heroicClears = newHeroic,
                mythicClears = newMythic,
                gold        = current.gold + result.totalGoldEarned,
                bossTokens  = current.bossTokens + result.totalTokensEarned,
            )
        } else {
            val avgLevel = partyCharIds.mapNotNull { id ->
                characterDao.getById(id)?.level
            }.average().toInt().coerceAtLeast(1)
            val repairCost = current.repairCost(avgLevel, current.gold)
            current.copy(gold = (current.gold - repairCost).coerceAtLeast(0))
        }
        progressDao.upsert(updated.toEntity())

        // Save loot and award XP
        if (result.survived) {
            saveNewItems(result.allLoot.map { it.item }, partyCharIds.toSet())
        }
        // Award XP to party
        for (charId in partyCharIds) {
            val ce = characterDao.getById(charId) ?: continue
            val xpGain = result.totalXpEarned / partyCharIds.size
            val newXp  = ce.xp + xpGain
            val xpToNext = (120 + 45 * ce.level + 18 * ce.level * ce.level).toLong()
            if (newXp >= xpToNext && ce.level < 20) {
                characterDao.upsert(ce.copy(level = ce.level + 1, xp = newXp - xpToNext))
            } else {
                characterDao.upsert(ce.copy(xp = newXp))
            }
        }
    }

    suspend fun spendTokensForItem(slot: ItemSlot, charId: Long) = withContext(Dispatchers.IO) {
        val progress = progressDao.get()?.toDomain() ?: return@withContext
        val cost = progress.tokenCostForSlot(slot)
        if (progress.bossTokens < cost) return@withContext
        // Generate a Rare item of the chosen slot
        val item = assetLoader.run {
            val stats = computeItemStats(slot, 12, ItemRarity.RARE, ItemProfile.MIXED)
            Item(
                id      = System.currentTimeMillis(),
                name    = "Token-Crafted ${slot.displayName}",
                slot    = slot,
                ilvl    = 12,
                rarity  = ItemRarity.RARE,
                stats   = stats,
                binding = ItemBinding.BOP,
                profile = ItemProfile.MIXED,
            )
        }
        val newId = itemDao.upsert(item.toEntity(ownerCharId = charId))
        val ce = characterDao.getById(charId)
        if (ce != null) {
            characterDao.upsert(ce.copy(bagItemIdsJson = ce.bagItemIdsJson + newId))
        }
        progressDao.upsert(progress.copy(bossTokens = progress.bossTokens - cost).toEntity())
    }

    suspend fun resetSave() = withContext(Dispatchers.IO) {
        characterDao.deleteAll()
        itemDao.deleteAll()
        progressDao.deleteAll()
        initIfEmpty()
    }

    // ─── Loot pool ────────────────────────────────────────────────────────────

    fun loadLootPool(): List<Item> = assetLoader.loadLootPool()
}
