package com.bighouse.dungeonsim.ui.roster

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.ui.theme.DungeonColors

@Composable
fun RosterScreen(vm: RosterViewModel) {
    val state by vm.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let { snackHost.showSnackbar(it); vm.dismissSnack() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Left: character list
            Column(
                Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
            ) {
                RosterFilterBar(state.filterClass, state.filterRole, vm)
                CharacterList(
                    characters    = state.characters,
                    selectedChar  = state.selectedChar,
                    onSelect      = vm::selectCharacter,
                )
            }

            // Right: detail / equip panel
            Box(
                Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                val char = state.selectedChar
                if (char == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a character", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    CharacterDetailPanel(
                        character = char,
                        allItems  = state.allItems,
                        upgrades  = vm.upgradeCandidates(char, state.allItems),
                        onEquip   = { item -> vm.equipItem(char, item) },
                        onVault   = { itemId -> vm.moveItemToVault(itemId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RosterFilterBar(
    filterClass: ClassType?,
    filterRole:  Role?,
    vm:          RosterViewModel,
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        // Role filters
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(null to "All", Role.TANK to "Tank", Role.HEALER to "Heal", Role.DPS_PHYS to "DPS", Role.SUPPORT to "Supp")
                .forEach { (role, label) ->
                    val selected = filterRole == role || (role == Role.DPS_PHYS && filterRole?.isDPS() == true)
                    FilterChip(
                        selected = selected,
                        onClick  = { vm.filterByRole(role) },
                        label    = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                }
        }
    }
}

@Composable
private fun CharacterList(
    characters:   List<Character>,
    selectedChar: Character?,
    onSelect:     (Character) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(characters, key = { it.id }) { char ->
            CharacterRow(
                char       = char,
                isSelected = selectedChar?.id == char.id,
                onClick    = { onSelect(char) },
            )
        }
    }
}

@Composable
private fun CharacterRow(
    char:       Character,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else DungeonColors.panelBorder
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Class color square
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(char.classType.color()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    char.role.shortLabel,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        char.name,
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Lv${char.level}", fontSize = 10.sp)
                    }
                }
                Text(
                    "${char.classType.displayName} • ${char.role.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // XP bar
                val xpFraction = (char.xp.toFloat() / char.xpToNext().toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress      = { xpFraction },
                    modifier      = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp),
                    color         = DungeonColors.xp,
                    trackColor    = DungeonColors.panelBorder,
                )
            }
        }
    }
}

@Composable
private fun CharacterDetailPanel(
    character: Character,
    allItems:  List<Item>,
    upgrades:  List<Pair<ItemSlot, Item>>,
    onEquip:   (Item) -> Unit,
    onVault:   (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(character.classType.color()),
                contentAlignment = Alignment.Center,
            ) {
                Text(character.role.shortLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(character.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Lv${character.level} ${character.classType.displayName} (${character.role.displayName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Base stats
        Text("Stats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        val s = character.baseStats
        val bonusStats = allItems
            .filter { it.id in character.equippedItems.values }
            .fold(ItemStats()) { acc, item -> acc + item.stats }

        StatGrid(s, bonusStats)
        Spacer(Modifier.height(12.dp))

        // Equipped items
        Text("Equipment", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        ItemSlot.values().forEach { slot ->
            val equippedId = character.equippedItems[slot]
            val equipped   = allItems.find { it.id == equippedId }
            EquipSlotRow(slot = slot, item = equipped)
        }
        Spacer(Modifier.height(12.dp))

        // Bag items (upgrade candidates highlighted)
        val bagItems = allItems.filter { it.id in character.bagItemIds }
        if (bagItems.isNotEmpty()) {
            Text("Bag (${bagItems.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            bagItems.forEach { item ->
                val isUpgrade = upgrades.any { (_, up) -> up.id == item.id }
                BagItemRow(item = item, isUpgrade = isUpgrade, onEquip = onEquip, onVault = onVault)
            }
        }
    }
}

@Composable
private fun StatGrid(base: ItemStats, bonus: ItemStats) {
    val stats = listOf(
        "HP"        to "${base.hp} + ${bonus.hp}",
        "Mana"      to "${base.mana} + ${bonus.mana}",
        "Armor"     to "${base.armor} + ${bonus.armor}",
        "MagicArmor"to "${base.magicArmor} + ${bonus.magicArmor}",
        "PhysDmg"   to "${"%.1f".format(base.physDamage)} + ${"%.1f".format(bonus.physDamage)}",
        "SpellDmg"  to "${"%.1f".format(base.spellDamage)} + ${"%.1f".format(bonus.spellDamage)}",
        "Crit"      to "${"%.1f".format((base.critChance + bonus.critChance) * 100)}%",
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EquipSlotRow(slot: ItemSlot, item: Item?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            slot.displayName,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
        )
        if (item != null) {
            Text(
                item.name,
                style  = MaterialTheme.typography.bodySmall,
                color  = item.rarity.color(),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BagItemRow(
    item:      Item,
    isUpgrade: Boolean,
    onEquip:   (Item) -> Unit,
    onVault:   (Long) -> Unit,
) {
    val border = if (isUpgrade) MaterialTheme.colorScheme.primary else DungeonColors.panelBorder
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, border, RoundedCornerShape(4.dp)),
        shape  = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.bodySmall, color = item.rarity.color())
                    if (isUpgrade) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Upgrade",
                            tint     = DungeonColors.xp,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    "${item.slot.displayName} • ilvl${item.ilvl} • ${item.binding.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(item.shortStatSummary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                TextButton(onClick = { onEquip(item) }, modifier = Modifier.height(32.dp)) {
                    Text("Equip", fontSize = 11.sp)
                }
                if (item.binding == ItemBinding.BOE) {
                    TextButton(onClick = { onVault(item.id) }, modifier = Modifier.height(32.dp)) {
                        Text("Vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
