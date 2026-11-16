package com.kARThik.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.kARThik.vote.utils.fmtDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, millis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp))
        Text("$label: ${fmtDate(millis)}", Modifier.weight(1f))
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onChange); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp))
        Text(if (value.isBlank()) label else value)
    }
    if (open) {
        val parts = value.split(":")
        val state = rememberTimePickerState(parts.getOrNull(0)?.toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0, true)
        AlertDialog(
            onDismissRequest = { open = false }, title = { Text(label) }, text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { onChange("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = {
                Row { TextButton(onClick = { onChange(""); open = false }) { Text("Clear") }; TextButton(onClick = { open = false }) { Text("Cancel") } }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectionEditScreen(nav: NavHostController, editId: String?) {
    val vm = appVm(key = "edit-$editId") { ElectionEditVm(it, editId) }
    val form by vm.form.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val error by vm.error.collectAsState()
    val parties by vm.parties.collectAsState()
    val candidates by vm.candidates.collectAsState()
    val search by vm.candidateSearch.collectAsState()
    val structural by vm.structural.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    val partyById = parties.associateBy { it.id }

    MatamScaffold(if (editId == null) "Create election" else "Edit election", nav) { pad ->
        if (!loaded) { LoadingBox(Modifier.padding(pad)); return@MatamScaffold }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(form.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Election name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(form.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            ExposedDropdownMenuBox(catMenu, { catMenu = it }) {
                FormField(form.category, {}, readOnly = true, label = { Text("Category") }, modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catMenu) })
                ExposedDropdownMenu(catMenu, { catMenu = false }) {
                    ELECTION_CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { vm.update { it.copy(category = c) }; catMenu = false }) }
                }
            }
            DateField("Date", form.date, { d -> vm.update { it.copy(date = d) } })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Start time", form.start, { v -> vm.update { it.copy(start = v) } }, Modifier.weight(1f))
                TimeField("End time", form.end, { v -> vm.update { it.copy(end = v) } }, Modifier.weight(1f))
            }
            FormField(form.location, { v -> vm.update { it.copy(location = v) } }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            if (!structural) Text("Candidates and voting system can't be changed once voting has started.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (structural) {
            SectionTitle("Voting system")
            VotingMode.values().forEach { m ->
                Card(
                    onClick = { vm.update { it.copy(mode = m) } }, shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (form.mode == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(form.mode == m, { vm.update { it.copy(mode = m) } })
                        Column {
                            Text(m.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (m == VotingMode.SINGLE_PHONE) "One phone runs the whole session."
                                else "Voting Terminal + Election Controller over Bluetooth. Supervised, non-binding use only.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            }
            SectionTitle("Ballot appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                BallotStyle.values().forEach { st ->
                    val sel = form.ballotStyle == st
                    Card(
                        onClick = { vm.update { it.copy(ballotStyle = st) } }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BallotStylePreview(st)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(sel, { vm.update { it.copy(ballotStyle = st) } }, Modifier.size(32.dp)); Text(st.label, fontWeight = FontWeight.SemiBold)
                            }
                            Text(st.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (structural) {
            SectionTitle("Parties")
            OutlinedButton(onClick = { nav.navigate("party/edit/new") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add new party") }
            if (parties.isEmpty() && candidates.isEmpty()) Text("No parties yet. Candidates can still contest as independents.", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(parties, key = { it.id }) { p ->
                    val members = candidates.filter { it.partyId == p.id }
                    val all = members.isNotEmpty() && members.all { it.id in form.candidateIds }
                    FilterChip(all, { vm.toggleParty(p.id) }, { Text("${p.abbreviation.ifBlank { p.name }} (${members.size})") },
                        leadingIcon = { PartySymbol(p.logoFile, partyAbbr(p), p.colorArgb, 22.dp, emoji = p.emoji) })
                }
                item {
                    val ind = candidates.filter { it.partyId == null }
                    FilterChip(ind.isNotEmpty() && ind.all { it.id in form.candidateIds }, { vm.toggleParty(null) }, { Text("Independent (${ind.size})") })
                }
            }

            SectionTitle("Candidates (${form.candidateIds.size} selected)")
            SearchField(search, { vm.candidateSearch.value = it }, "Search candidates")
            OutlinedButton(onClick = { nav.navigate("candidate/edit/new") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Add new candidate") }
            if (candidates.isEmpty()) {
                EmptyState(Icons.Default.People, "No candidates registered", "Register candidates first.", action = {
                    Button(onClick = { nav.navigate("candidate/edit/new") }) { Text("Add candidate") }
                })
            }
            candidates.filter { search.isBlank() || it.name.contains(search, true) }.forEach { c ->
                val p = c.partyId?.let { partyById[it] }
                Card(onClick = { vm.toggleCandidate(c.id) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id in form.candidateIds, { vm.toggleCandidate(c.id) })
                        StoredImage(c.photoFile, Modifier.size(48.dp), emoji = c.emoji)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold); Text(p?.name ?: "Independent", style = MaterialTheme.typography.bodySmall)
                        }
                        PartySymbol(p?.logoFile, partyAbbr(p), p?.colorArgb ?: 0xFF757575, 30.dp, emoji = p?.emoji)
                    }
                }
            }

            }
            FormField(form.instructions, { v -> vm.update { it.copy(instructions = v) } }, label = { Text("Election instructions (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            SectionTitle(if (editId == null) "Election password" else "Change password (optional)")
            Text(
                if (editId == null) "Required to complete this election and to view or export its results. It cannot be recovered if forgotten; it can only be changed from this Edit screen after entering the current password."
                else "Leave both boxes empty to keep the current password.",
                style = MaterialTheme.typography.bodySmall
            )
            PasswordField(if (editId == null) "Password (min 4 characters)" else "New password (min 4 characters)", form.password, { v -> vm.update { it.copy(password = v) } })
            PasswordField("Confirm password", form.confirm, { v -> vm.update { it.copy(confirm = v) } }, error = form.confirm.isNotEmpty() && form.confirm != form.password)
            ErrorBanner(error)
            Button(
                onClick = { val e = vm.validate(); if (e != null) vm.error.value = e else { vm.error.value = null; confirm = true } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)
            ) { Text(if (editId == null) "Create election" else "Save changes") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) ConfirmDialog(
        if (editId == null) "Create this election?" else "Save changes?",
        "\"${form.name}\" with ${form.candidateIds.size} candidates, ${form.mode.label}.", if (editId == null) "Create" else "Save",
        onConfirm = { vm.save { id -> if (editId == null) nav.navigate("election/$id") { popUpTo("election/create") { inclusive = true }; launchSingleTop = true } else nav.popBackStack() } },
        onDismiss = { confirm = false }
    )
}
