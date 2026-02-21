package com.bighouse.dungeonsim.ui.instances

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
fun InstancesScreen(
    vm:       InstancesViewModel,
    onStart:  (List<Long>, Difficulty) -> Unit,
) {
    val state by vm.state.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Dungeon header
        DungeonHeader(state.dungeon, state.progress)
        Spacer(Modifier.height(12.dp))

        // Difficulty selector
        DifficultySelector(
            available  = state.progress.availableDifficulties(),
            selected   = state.selectedDifficulty,
            onSelect   = vm::selectDifficulty,
        )
        Spacer(Modifier.height(12.dp))

        // Party composition
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text("Party (${state.selectedParty.size}/5)", style = MaterialTheme.typography.titleSmall)
            Row {
                TextButton(onClick = vm::autoCompose) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Auto", fontSize = 12.sp)
                }
                TextButton(onClick = vm::clearParty) {
                    Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Selected party row
        SelectedPartyRow(state.selectedParty)
        Spacer(Modifier.height(8.dp))

        // Character picker
        Text("Roster", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LazyColumn(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding      = PaddingValues(vertical = 4.dp),
        ) {
            items(state.allCharacters, key = { it.id }) { char ->
                val isSelected = state.selectedParty.any { it.id == char.id }
                CharPickerRow(
                    char       = char,
                    isSelected = isSelected,
                    onClick    = { vm.toggleCharacterInParty(char) },
                )
            }
        }

        // Error & Start
        Spacer(Modifier.height(8.dp))
        state.partyError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick  = { onStart(state.selectedParty.map { it.id }, state.selectedDifficulty) },
            enabled  = state.canStartRun,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Enter ${state.dungeon.name} — ${state.selectedDifficulty.displayName}")
        }
    }
}

@Composable
private fun DungeonHeader(dungeon: Dungeon, progress: GameProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
        border   = BorderStroke(1.dp, DungeonColors.panelBorder),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(dungeon.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Recommended Level ${dungeon.recommendedLevel} • 5-Man", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("Normal Clears", "${progress.normalClears}", DungeonColors.xp)
                StatChip("Heroic Clears", "${progress.heroicClears}", DungeonColors.manaBar)
                StatChip("Mythic Clears", "${progress.mythicClears}", DungeonColors.token)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip("Gold", "${progress.gold}g", DungeonColors.gold)
                StatChip("Tokens", "${progress.bossTokens}", DungeonColors.token)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DifficultySelector(
    available: List<Difficulty>,
    selected:  Difficulty,
    onSelect:  (Difficulty) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.values().forEach { diff ->
            val isLocked   = diff !in available
            val isSelected = diff == selected
            val container  = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isLocked   -> Color(0xFF2A2A2A)
                else       -> DungeonColors.panelBg
            }
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isLocked) { onSelect(diff) },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else DungeonColors.panelBorder,
                ),
                colors = CardDefaults.outlinedCardColors(containerColor = container),
            ) {
                Column(
                    Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        diff.displayName,
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLocked) {
                        Text(
                            "Need ${diff.unlockRequires} clears",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 9.sp,
                        )
                    } else {
                        Text(
                            "HP×${"%.1f".format(diff.hpMult)} DMG×${"%.1f".format(diff.damageMult)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPartyRow(party: List<com.bighouse.dungeonsim.data.model.Character>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        party.forEach { char ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(char.classType.color()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(char.role.shortLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${char.level}", color = Color.White, fontSize = 9.sp)
                }
            }
        }
        // Empty slots
        repeat((5 - party.size).coerceAtLeast(0)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, DungeonColors.panelBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CharPickerRow(
    char:       com.bighouse.dungeonsim.data.model.Character,
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
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else DungeonColors.panelBg,
        ),
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(char.classType.color()),
                contentAlignment = Alignment.Center,
            ) {
                Text(char.role.shortLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(char.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Lv${char.level} ${char.classType.displayName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
