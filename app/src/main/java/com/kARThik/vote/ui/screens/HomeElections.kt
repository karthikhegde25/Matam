package com.kARThik.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kARThik.vote.data.*
import com.kARThik.vote.ui.*

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appVm { HomeVm(it) }
    val elections by vm.elections.collectAsState()
    val total by vm.totalElections.collectAsState()
    val completed by vm.completed.collectAsState()
    val cands by vm.candidates.collectAsState()
    val votes by vm.votes.collectAsState()

    val startable = elections.firstOrNull { it.status == "ONGOING" || it.status == "PAUSED" }
        ?: elections.firstOrNull { it.status == "READY" }
    val latestResult = elections.firstOrNull { it.voteCount > 0 || it.status == "COMPLETED" }

    MatamScaffold("Matam", actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Elections", "$total", Icons.Default.HowToVote, Modifier.weight(1f))
                    StatCard("Candidates", "$cands", Icons.Default.People, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Votes recorded", "$votes", Icons.Default.Ballot, Modifier.weight(1f))
                    StatCard("Completed", "$completed", Icons.Default.TaskAlt, Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = { nav.navigate("election/create") }, modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create Election", style = MaterialTheme.typography.titleMedium) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        onClick = { startable?.let { nav.navigate("election/${it.id}") } }, enabled = startable != null,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Start voting", maxLines = 1) }
                    FilledTonalButton(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        onClick = { latestResult?.let { nav.navigate("results/${it.id}") } ?: nav.goTab("results") },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Default.BarChart, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("View results", maxLines = 1) }
                }
            }
            item {
                OutlinedButton(
                    onClick = { nav.navigate("bt/start") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Bluetooth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Bluetooth voting (two phones)", maxLines = 1) }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent elections", Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("history") }) { Text("History") }
                }
            }
            if (elections.isEmpty()) {
                item {
                    EmptyState(Icons.Default.HowToVote, "No elections yet", "Add parties and candidates, then create your first election.")
                }
            } else {
                items(elections.take(5), key = { it.id }) { e -> ElectionCard(e, onClick = { nav.navigate("election/${e.id}") }) }
            }
        }
    }
}

@Composable
fun ElectionsScreen(nav: NavHostController) {
    val vm = appVm { ElectionsVm(it) }
    val list by vm.filtered.collectAsState()
    val all by vm.all.collectAsState()
    val q by vm.query.collectAsState()
    val st by vm.status.collectAsState()
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmId by remember { mutableStateOf<String?>(null) }

    MatamScaffold("Elections", actions = {
        IconButton(onClick = { nav.navigate("history") }) { Icon(Icons.Default.History, "History") }
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }, fab = {
        ExtendedFloatingActionButton(onClick = { nav.navigate("election/create") }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New") })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { vm.query.value = it }, "Search elections", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            FilterChipsRow(ElectionStatus.values().toList(), st, { it.label }) { vm.status.value = it }
            Spacer(Modifier.height(8.dp))
            if (all.isEmpty()) EmptyState(Icons.Default.HowToVote, "No elections", "Tap New to create your first election.", Modifier.weight(1f))
            else if (list.isEmpty()) EmptyState(Icons.Default.SearchOff, "No matches", "Try a different search or filter.", Modifier.weight(1f))
            else LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { e ->
                    ElectionCard(e, onClick = { nav.navigate("election/${e.id}") }, trailing = {
                        IconButton(onClick = { deleteId = e.id }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    })
                }
            }
        }
    }
    deleteId?.let { id ->
        PasswordPrompt(id, "Delete election", onSuccess = { confirmId = id; deleteId = null }, onDismiss = { deleteId = null })
    }
    confirmId?.let { id ->
        ConfirmDialog("Delete election?", "This permanently deletes the election, its votes and results from this device.", "Delete", true,
            onConfirm = { vm.delete(id) }, onDismiss = { confirmId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm = appVm { ElectionsVm(it) }
    val list by vm.filtered.collectAsState()
    val q by vm.query.collectAsState()
    val st by vm.status.collectAsState()
    val cat by vm.category.collectAsState()
    val date by vm.date.collectAsState()
    val sort by vm.sort.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }

    MatamScaffold("Election history", nav, actions = {
        Box {
            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
            DropdownMenu(sortMenu, { sortMenu = false }) {
                ElectionSort.values().forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.sort.value = s; sortMenu = false },
                        trailingIcon = { if (s == sort) Icon(Icons.Default.Check, null) })
                }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { vm.query.value = it }, "Search history", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            FilterChipsRow(listOf(ElectionStatus.COMPLETED, ElectionStatus.ONGOING, ElectionStatus.DRAFT), st, { it.label }) { vm.status.value = it }
            Spacer(Modifier.height(4.dp))
            FilterChipsRow(DateFilter.values().toList(), date, { it.label }, "Any date") { vm.date.value = it }
            Spacer(Modifier.height(4.dp))
            FilterChipsRow(ELECTION_CATEGORIES, cat, { it.removeSuffix(" election") }, "Any category") { vm.category.value = it }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) EmptyState(Icons.Default.History, "Nothing here", "Elections matching these filters will appear here.", Modifier.weight(1f))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { e -> ElectionCard(e, onClick = { nav.navigate("election/${e.id}") }) }
            }
        }
    }
}
