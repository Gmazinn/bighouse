package com.bighouse.dungeonsim.ui.run

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.bighouse.dungeonsim.data.model.ClassType
import com.bighouse.dungeonsim.engine.*
import com.bighouse.dungeonsim.ui.theme.DungeonColors

@Composable
fun RunScreen(
    vm:      RunViewModel,
    onBack:  () -> Unit,
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        RunTopBar(state = state, vm = vm, onBack = onBack)

        when (state.playback) {
            PlaybackState.IDLE, PlaybackState.SIMULATING -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Computing encounter...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            PlaybackState.ENCOUNTER_BREAK -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Encounter cleared — next area...", style = MaterialTheme.typography.titleMedium, color = DungeonColors.xp)
                }
            }
            else -> {
                // ── Main content ──────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Party frames (left)
                    Column(Modifier.weight(0.5f)) {
                        Text("Party", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                        state.partyStates.forEach { member ->
                            PartyFrame(member)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    // Enemy frames (right)
                    Column(Modifier.weight(0.5f)) {
                        Text("Enemies", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
                        val aliveEnemies  = state.enemyStates.filter { it.isAlive }
                        val deadEnemies   = state.enemyStates.filter { !it.isAlive }
                        aliveEnemies.forEach { enemy ->
                            EnemyFrame(enemy)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (deadEnemies.isNotEmpty()) {
                            Text("${deadEnemies.size} defeated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                HorizontalDivider(color = DungeonColors.panelBorder)

                // ── Bottom: Log / Meters / Summary tabs ───────────────────────
                TabRow(
                    selectedTabIndex = state.activeTab.ordinal,
                    containerColor   = DungeonColors.panelBg,
                ) {
                    RunScreenTab.values().forEach { tab ->
                        Tab(
                            selected = state.activeTab == tab,
                            onClick  = { vm.setTab(tab) },
                            text     = { Text(tab.name, fontSize = 12.sp) },
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    when (state.activeTab) {
                        RunScreenTab.LOG     -> LogPanel(state.log)
                        RunScreenTab.METERS  -> MetersPanel(state.meters, state.activeMeterType, vm::setMeterType)
                        RunScreenTab.SUMMARY -> SummaryPanel(state.result)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunTopBar(state: RunUiState, vm: RunViewModel, onBack: () -> Unit) {
    Surface(color = DungeonColors.panelBg) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Encounter info
                Column(Modifier.weight(1f)) {
                    Text(
                        state.encounterName.ifEmpty { "Cinder Crypt" },
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                    )
                    Text(
                        "Encounter ${state.currentEncounterIdx + 1}/${state.totalEncounters}  •  Tick ${state.currentTick}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Controls
                if (state.playback == PlaybackState.PLAYING || state.playback == PlaybackState.PAUSED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Speed buttons
                        listOf(1, 2, 4).forEach { s ->
                            val selected = state.speed == s
                            TextButton(
                                onClick  = { vm.setSpeed(s) },
                                colors   = ButtonDefaults.textButtonColors(
                                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text("×$s", fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // Pause/Resume
                        IconButton(onClick = vm::togglePause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (state.playback == PlaybackState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (state.playback == PlaybackState.PAUSED) "Resume" else "Pause",
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Skip
                        IconButton(onClick = vm::skipToResult, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.SkipNext, "Skip to result", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (state.playback == PlaybackState.DONE) {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            }

            // Encounter progress bar
            if (state.totalEncounters > 0) {
                LinearProgressIndicator(
                    progress   = { (state.currentEncounterIdx + 1).toFloat() / state.totalEncounters },
                    modifier   = Modifier.fillMaxWidth().height(3.dp),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = DungeonColors.panelBorder,
                )
            }
        }
    }
}

@Composable
private fun PartyFrame(member: PartyMemberState) {
    val hpFraction = if (member.maxHp > 0) member.hp.toFloat() / member.maxHp else 0f
    val mpFraction = if (member.maxMana > 0) member.mana.toFloat() / member.maxMana else 0f
    val alpha      = if (member.isAlive) 1f else 0.35f

    val classColor = try { ClassType.valueOf(member.classType.name).color() }
    catch (_: Exception) { Color.Gray }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape  = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
        border = BorderStroke(1.dp, DungeonColors.panelBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Role colored square
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(classColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(member.role.shortLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    member.name,
                    style    = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // HP bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hpColor = when {
                        hpFraction > 0.6f -> DungeonColors.hpBar
                        hpFraction > 0.3f -> DungeonColors.hpMedium
                        else              -> DungeonColors.hpLow
                    }
                    LinearProgressIndicator(
                        progress      = { hpFraction.coerceIn(0f, 1f) },
                        modifier      = Modifier.weight(1f).height(8.dp),
                        color         = hpColor,
                        trackColor    = Color(0xFF1A0000),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (member.isAlive) "${member.hp}/${member.maxHp}" else "DEAD",
                        fontSize = 9.sp,
                        color    = if (member.isAlive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(60.dp),
                    )
                }
                // Mana bar (only if has mana)
                if (member.maxMana > 80) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress   = { mpFraction.coerceIn(0f, 1f) },
                            modifier   = Modifier.weight(1f).height(4.dp),
                            color      = DungeonColors.manaBar,
                            trackColor = Color(0xFF000A1A),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${member.mana}", fontSize = 9.sp, color = DungeonColors.manaBar, modifier = Modifier.width(60.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnemyFrame(enemy: EnemyState) {
    val hpFraction = if (enemy.maxHp > 0) enemy.hp.toFloat() / enemy.maxHp else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(4.dp),
        colors   = CardDefaults.cardColors(containerColor = DungeonColors.panelBg),
        border   = BorderStroke(1.dp, DungeonColors.panelBorder),
    ) {
        Column(Modifier.padding(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    enemy.name,
                    style    = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color    = when (enemy.type) {
                        com.bighouse.dungeonsim.data.model.EnemyType.FINAL_BOSS -> Color(0xFFFF4444)
                        com.bighouse.dungeonsim.data.model.EnemyType.RARE_BOSS  -> Color(0xFFA335EE)
                        com.bighouse.dungeonsim.data.model.EnemyType.MINI_BOSS  -> Color(0xFFFF8C00)
                        com.bighouse.dungeonsim.data.model.EnemyType.ELITE      -> Color(0xFF0070DD)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${enemy.hp}/${enemy.maxHp}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress   = { hpFraction.coerceIn(0f, 1f) },
                modifier   = Modifier.fillMaxWidth().height(6.dp),
                color      = DungeonColors.enemyHp,
                trackColor = Color(0xFF1A0000),
            )
        }
    }
}

@Composable
private fun LogPanel(log: List<LogEntry>) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.lastIndex)
    }
    LazyColumn(
        state           = listState,
        modifier        = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 8.dp),
        contentPadding  = PaddingValues(vertical = 4.dp),
        reverseLayout   = false,
    ) {
        items(log) { entry ->
            Text(
                "[${entry.tick}] ${entry.message}",
                style    = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color    = when (entry.type) {
                    LogType.DAMAGE  -> Color(0xFFFF6B6B)
                    LogType.HEAL    -> Color(0xFF6BFF9B)
                    LogType.DEATH   -> Color(0xFFFFD700)
                    LogType.ABILITY -> Color(0xFF6BB5FF)
                    LogType.LOOT    -> Color(0xFFA335EE)
                    LogType.SYSTEM  -> Color(0xFFFFFFFF)
                    LogType.NORMAL  -> Color(0xFFAAAAAA)
                },
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun MetersPanel(meters: Meters, meterType: MeterType, onTypeChange: (MeterType) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // Meter type selector
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MeterType.values().forEach { mt ->
                FilterChip(
                    selected = meterType == mt,
                    onClick  = { onTypeChange(mt) },
                    label    = {
                        Text(
                            when (mt) {
                                MeterType.DAMAGE_DONE  -> "Dmg Done"
                                MeterType.DAMAGE_TAKEN -> "Dmg Taken"
                                MeterType.HEALING_DONE -> "Healing"
                            },
                            fontSize = 10.sp,
                        )
                    },
                    modifier = Modifier.height(28.dp),
                )
            }
        }

        val entries = when (meterType) {
            MeterType.DAMAGE_DONE  -> meters.sortedByDamage()
            MeterType.DAMAGE_TAKEN -> meters.sortedByTaken()
            MeterType.HEALING_DONE -> meters.sortedByHealing()
        }
        val total = when (meterType) {
            MeterType.DAMAGE_DONE  -> meters.totalDamage().toFloat()
            MeterType.DAMAGE_TAKEN -> entries.sumOf { it.damageTaken }.toFloat()
            MeterType.HEALING_DONE -> meters.totalHealing().toFloat()
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries) { entry ->
                val value = when (meterType) {
                    MeterType.DAMAGE_DONE  -> entry.damageDone
                    MeterType.DAMAGE_TAKEN -> entry.damageTaken
                    MeterType.HEALING_DONE -> entry.healingDone
                }
                val fraction = if (total > 0) value / total else 0f
                MeterRow(
                    name      = entry.characterName,
                    classType = entry.classType,
                    value     = value,
                    fraction  = fraction,
                )
            }
        }
    }
}

@Composable
private fun MeterRow(name: String, classType: ClassType, value: Int, fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        // Background bar
        LinearProgressIndicator(
            progress   = { fraction.coerceIn(0f, 1f) },
            modifier   = Modifier.fillMaxSize(),
            color      = classType.color().copy(alpha = 0.35f),
            trackColor = Color(0xFF111111),
        )
        // Text overlay
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$value (${"%.0f".format(fraction * 100)}%)",
                style  = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = classType.color(),
            )
        }
    }
}

@Composable
private fun SummaryPanel(result: RunSimResult?) {
    if (result == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Run in progress...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            // Outcome header
            val outcomeText  = if (result.survived) "Dungeon Cleared!" else "Party Wiped"
            val outcomeColor = if (result.survived) DungeonColors.xp else MaterialTheme.colorScheme.error
            Text(outcomeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = outcomeColor)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat("XP",      "+${result.totalXpEarned}",    DungeonColors.xp)
                SummaryStat("Gold",    "+${result.totalGoldEarned}g", DungeonColors.gold)
                SummaryStat("Tokens",  "+${result.totalTokensEarned}",DungeonColors.token)
            }
        }
        // Loot
        if (result.allLoot.isNotEmpty()) {
            item {
                Text("Loot (${result.allLoot.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(result.allLoot) { drop ->
                LootDropRow(drop)
            }
        }
        // Per-encounter results
        item {
            Text("Encounters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        items(result.encounters) { enc ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(enc.encounterName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (enc.won) "Clear" else "Wipe",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enc.won) DungeonColors.xp else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LootDropRow(drop: com.bighouse.dungeonsim.engine.LootDrop) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(drop.item.rarity.color())
        )
        Column(Modifier.weight(1f)) {
            Text(drop.item.name, style = MaterialTheme.typography.bodySmall, color = drop.item.rarity.color(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${drop.item.slot.displayName} • ${drop.item.rarity.displayName} • ${drop.item.binding.name} • ilvl${drop.item.ilvl}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Extension to allow alpha on Modifier (avoids import issues). */
private fun Modifier.alpha(alpha: Float) = this.then(
    Modifier.graphicsLayer { this.alpha = alpha }
)
