package com.bighouse.dungeonsim.ui.vault

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.ui.theme.DungeonColors

@Composable
fun VaultScreen(vm: VaultViewModel) {
    val state by vm.state.collectAsState()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(state.snackMessage) {
        state.snackMessage?.let { snackHost.showSnackbar(it); vm.dismissSnack() }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Stats bar
            VaultHeader(state.progress)
            Spacer(Modifier.height(8.dp))

            // Token vendor
            TokenVendor(
                progress   = state.progress,
                characters = state.allCharacters,
                onBuy      = { slot, char -> vm.buyTokenItem(slot, char) },
            )
            Spacer(Modifier.height(12.dp))

            // Vault item list
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Vault Items (${state.vaultItems.size})", style = MaterialTheme.typography.titleSmall)
                // Slot filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = state.filterSlot == null,
                        onClick  = { vm.filterBySlot(null) },
                        label    = { Text("All", fontSize = 10.sp) },
                        modifier = Modifier.height(26.dp),
                    )
                    ItemSlot.values().forEach { slot ->
                        FilterChip(
                            selected = state.filterSlot == slot,
                            onClick  = { vm.filterBySlot(slot) },
                            label    = { Text(slot.displayName.take(4), fontSize = 10.sp) },
                            modifier = Modifier.height(26.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (state.vaultItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.filterSlot == null) "Vault is empty. Run dungeons to earn loot!"
                        else "No ${state.filterSlot?.displayName} items in vault",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.vaultItems, key = { it.id }) { item ->
                        VaultItemCard(
                            item       = item,
                            characters = state.allCharacters,
                            onSendTo   = { char -> vm.sendItemToCharacter(item, char) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultHeader(progress: GameProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
        border   = BorderStroke(1.dp, DungeonColors.panelBorder),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatDisplay("Gold", "${progress.gold}g", DungeonColors.gold)
            StatDisplay("Boss Tokens", "${progress.bossTokens}", DungeonColors.token)
            StatDisplay("Normal", "${progress.normalClears} clears", DungeonColors.xp)
            StatDisplay("Heroic", "${progress.heroicClears} clears", DungeonColors.manaBar)
        }
    }
}

@Composable
private fun StatDisplay(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TokenVendor(
    progress:   GameProgress,
    characters: List<Character>,
    onBuy:      (ItemSlot, Character) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<ItemSlot?>(null) }
    var selectedChar by remember { mutableStateOf<Character?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
        border   = BorderStroke(1.dp, DungeonColors.panelBorder),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Store, null, tint = DungeonColors.token, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Token Vendor", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text("${progress.bossTokens} tokens", style = MaterialTheme.typography.labelSmall, color = DungeonColors.token)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Buy 1 Rare item (ilvl 12, BOP):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))

                // Slot buttons with cost
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ItemSlot.values().forEach { slot ->
                        val cost    = progress.tokenCostForSlot(slot)
                        val canAfford = progress.bossTokens >= cost
                        val isSelected = selectedSlot == slot
                        OutlinedButton(
                            onClick  = { selectedSlot = if (isSelected) null else slot },
                            enabled  = canAfford,
                            border   = BorderStroke(1.dp, if (isSelected) DungeonColors.token else DungeonColors.panelBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(slot.displayName, fontSize = 11.sp)
                                Text("$cost T", fontSize = 9.sp, color = DungeonColors.token)
                            }
                        }
                    }
                }

                if (selectedSlot != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Send to:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        characters.take(8).forEach { char ->
                            val isSelChar = selectedChar?.id == char.id
                            FilterChip(
                                selected = isSelChar,
                                onClick  = { selectedChar = if (isSelChar) null else char },
                                label    = { Text("${char.name.split(" ").first()} Lv${char.level}", fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }

                    val ready = selectedSlot != null && selectedChar != null
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick  = {
                            val sl = selectedSlot; val ch = selectedChar
                            if (sl != null && ch != null) {
                                onBuy(sl, ch)
                                selectedSlot = null; selectedChar = null
                            }
                        },
                        enabled  = ready,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.ShoppingCart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Craft ${selectedSlot?.displayName ?: ""} for ${selectedChar?.name?.split(" ")?.first() ?: "..."}")
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item:       Item,
    characters: List<Character>,
    onSendTo:   (Character) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .border(1.dp, DungeonColors.panelBorder, RoundedCornerShape(6.dp)),
        shape  = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rarity dot
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(item.rarity.color())
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodySmall, color = item.rarity.color(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.slot.displayName} • ${item.rarity.displayName} • ilvl${item.ilvl} • ${item.binding.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(item.shortStatSummary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = DungeonColors.panelBorder)
                Spacer(Modifier.height(6.dp))
                Text("Equip on:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(characters) { char ->
                        OutlinedButton(
                            onClick  = { onSendTo(char) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            border   = BorderStroke(1.dp, char.classType.color()),
                        ) {
                            Text(
                                "${char.name.split(" ").first()} Lv${char.level}",
                                fontSize = 10.sp,
                                color    = char.classType.color(),
                            )
                        }
                    }
                }
            }
        }
    }
}
