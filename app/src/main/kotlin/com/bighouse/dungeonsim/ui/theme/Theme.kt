package com.bighouse.dungeonsim.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark dungeon-crawl palette
private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFFD4A017),   // parchment gold
    onPrimary        = Color(0xFF1A1005),
    primaryContainer = Color(0xFF3D2C00),
    secondary        = Color(0xFF8B4513),   // saddle brown
    onSecondary      = Color(0xFFFFEDD0),
    background       = Color(0xFF0D0D0D),   // near black
    onBackground     = Color(0xFFE0D8CC),
    surface          = Color(0xFF1A1A1A),
    onSurface        = Color(0xFFE0D8CC),
    surfaceVariant   = Color(0xFF2A2318),
    onSurfaceVariant = Color(0xFFC4B89A),
    error            = Color(0xFFCF6679),
    outline          = Color(0xFF4A3F2F),
)

@Composable
fun DungeonSimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(),
        content     = content,
    )
}

// Shared color utilities
object DungeonColors {
    val hpBar       = Color(0xFF2ECC71)
    val hpLow       = Color(0xFFE74C3C)
    val hpMedium    = Color(0xFFF39C12)
    val manaBar     = Color(0xFF3498DB)
    val enemyHp     = Color(0xFFE74C3C)
    val gold        = Color(0xFFFFD700)
    val token       = Color(0xFF9B59B6)
    val xp          = Color(0xFF1ABC9C)
    val panelBg     = Color(0xFF1E1E1E)
    val panelBorder = Color(0xFF3D3223)
}
