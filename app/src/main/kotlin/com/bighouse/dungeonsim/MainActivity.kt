package com.bighouse.dungeonsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bighouse.dungeonsim.ui.navigation.AppNavigation
import com.bighouse.dungeonsim.ui.theme.DungeonSimTheme

class MainActivity : ComponentActivity() {

    private val app: DungeonSimApp by lazy { application as DungeonSimApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DungeonSimTheme {
                AppNavigation(factory = app.viewModelFactory)
            }
        }
    }
}
