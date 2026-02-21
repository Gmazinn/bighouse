package com.bighouse.dungeonsim.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.bighouse.dungeonsim.data.model.*
import com.bighouse.dungeonsim.ui.instances.*
import com.bighouse.dungeonsim.ui.roster.*
import com.bighouse.dungeonsim.ui.run.*
import com.bighouse.dungeonsim.ui.vault.*
import com.bighouse.dungeonsim.ViewModelFactory

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Roster    : Screen("roster",    "Roster",    Icons.Filled.Group)
    object Instances : Screen("instances", "Instances", Icons.Filled.Castle)
    object Run       : Screen("run/{partyIds}/{difficulty}", "Run", Icons.Filled.PlayArrow) {
        fun createRoute(partyIds: List<Long>, difficulty: Difficulty) =
            "run/${partyIds.joinToString(",")}_${difficulty.name}"
    }
    object Vault     : Screen("vault",     "Vault",     Icons.Filled.Inventory2)
}

private val bottomNavItems = listOf(Screen.Roster, Screen.Instances, Screen.Vault)

@Composable
fun AppNavigation(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Roster.route,
            modifier         = Modifier.padding(padding),
        ) {
            composable(Screen.Roster.route) {
                val vm: RosterViewModel = viewModel(factory = factory)
                RosterScreen(vm)
            }

            composable(Screen.Instances.route) {
                val vm: InstancesViewModel = viewModel(factory = factory)
                InstancesScreen(
                    vm      = vm,
                    onStart = { partyIds, difficulty ->
                        navController.navigate(
                            "run_screen/${partyIds.joinToString(",")}_${difficulty.name}"
                        )
                    },
                )
            }

            composable(
                route = "run_screen/{params}",
                arguments = listOf(navArgument("params") { type = NavType.StringType }),
            ) { back ->
                val params    = back.arguments?.getString("params") ?: ""
                val parts     = params.split("_")
                val partyIds  = parts.dropLast(1).joinToString(",").split(",")
                    .mapNotNull { it.toLongOrNull() }
                val diff      = try { Difficulty.valueOf(parts.last()) } catch (_: Exception) { Difficulty.NORMAL }

                val vm: RunViewModel      = viewModel(factory = factory)
                val instVm: InstancesViewModel = viewModel(factory = factory)
                val instanceState by instVm.state.collectAsState()
                val allChars = instanceState.allCharacters
                val party    = allChars.filter { it.id in partyIds }

                LaunchedEffect(party.isNotEmpty()) {
                    if (party.isNotEmpty() && vm.state.value.playback == PlaybackState.IDLE) {
                        vm.startRun(party, Dungeon.CINDER_CRYPT, diff)
                    }
                }

                RunScreen(
                    vm     = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Vault.route) {
                val vm: VaultViewModel = viewModel(factory = factory)
                VaultScreen(vm)
            }
        }
    }
}
