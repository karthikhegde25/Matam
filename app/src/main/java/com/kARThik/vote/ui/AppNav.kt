package com.kARThik.vote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.kARThik.vote.ui.screens.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("elections", "Elections", Icons.Default.HowToVote),
    Tab("parties", "Parties", Icons.Default.Flag),
    Tab("candidates", "Candidates", Icons.Default.People),
    Tab("results", "Results", Icons.Default.BarChart)
)

fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route, onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        val idx = tabs.indexOfFirst { it.route == route }
        val swipe = if (showBar) Modifier.swipeTabs(
            onNext = { if (idx in 0 until tabs.lastIndex) nav.goTab(tabs[idx + 1].route) },
            onPrev = { if (idx > 0) nav.goTab(tabs[idx - 1].route) }
        ) else Modifier
        CompositionLocalProvider(LocalBottomBarShown provides showBar) {
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(if (showBar) pad else androidx.compose.foundation.layout.PaddingValues()).then(swipe)) {
            composable("home") { HomeScreen(nav) }
            composable("elections") { ElectionsScreen(nav) }
            composable("history") { HistoryScreen(nav) }
            composable("parties") { PartiesScreen(nav) }
            composable("candidates") { CandidatesScreen(nav) }
            composable("results") { ResultsListScreen(nav) }
            composable("settings") { SettingsScreen(nav) }

            composable("election/create") { ElectionEditScreen(nav, null) }
            composable("election/edit/{id}") { ElectionEditScreen(nav, it.arguments?.getString("id")) }
            composable("election/{id}") { ElectionDetailScreen(nav, it.arguments!!.getString("id")!!) }
            composable("results/{id}") { ResultsScreen(nav, it.arguments!!.getString("id")!!) }
            composable("vote/{id}") { VotingScreen(nav, it.arguments!!.getString("id")!!) }

            composable("party/edit/{id}") { PartyEditScreen(nav, it.arguments?.getString("id")?.takeIf { s -> s != "new" }) }
            composable("candidate/edit/{id}") { CandidateEditScreen(nav, it.arguments?.getString("id")?.takeIf { s -> s != "new" }) }

            composable("bt/start") { BtStartScreen(nav) }
            composable("bt/controller") { BtControllerScreen(nav) }
            composable("bt/controller/{id}") { BtControllerScreen(nav, it.arguments!!.getString("id")!!) }
            composable("bt/terminal") { BtTerminalScreen(nav) }
        }
        }
    }
}
