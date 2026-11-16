package com.kARThik.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kARThik.vote.data.*
import com.kARThik.vote.ui.*

@Composable
fun PartiesScreen(nav: NavHostController) {
    val vm = appVm { PartiesVm(it) }
    val list by vm.filtered.collectAsState()
    val all by vm.all.collectAsState()
    val q by vm.query.collectAsState()
    val usage by vm.usage.collectAsState()
    var deleteFree by remember { mutableStateOf<PartyEntity?>(null) }

    MatamScaffold("Parties", actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }, fab = {
        ExtendedFloatingActionButton(onClick = { nav.navigate("party/edit/new") }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add party") })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { vm.query.value = it }, "Search parties", Modifier.padding(horizontal = 16.dp))
            if (all.isEmpty()) EmptyState(Icons.Default.Flag, "No parties yet", "Add a party or organisation. Independent candidates don't need one.", Modifier.weight(1f))
            else if (list.isEmpty()) EmptyState(Icons.Default.SearchOff, "No matches", "No party matches your search.", Modifier.weight(1f))
            else LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { p ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column {
                            if (p.bannerFile != null) StoredImage(p.bannerFile, Modifier.fillMaxWidth().height(90.dp), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), Icons.Default.Image)
                            else Box(Modifier.fillMaxWidth().height(6.dp).background(Color(p.colorArgb.toInt())))
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                PartySymbol(p.logoFile, partyAbbr(p), p.colorArgb, 52.dp, emoji = p.emoji)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(p.abbreviation, p.leader).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { nav.navigate("party/edit/${p.id}") }) { Icon(Icons.Default.Edit, "Edit") }
                                IconButton(onClick = { vm.requestDelete(p) { deleteFree = p } }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteFree?.let { p -> ConfirmDialog("Delete party?", "Delete ${p.name}?", "Delete", true, onConfirm = { vm.delete(p.id, null) }, onDismiss = { deleteFree = null }) }
    usage?.let { (p, u) ->
        var target by remember { mutableStateOf<String?>(null) }
        var menu by remember { mutableStateOf(false) }
        val others = all.filter { it.id != p.id }
        AlertDialog(
            onDismissRequest = { vm.usage.value = null }, title = { Text("${p.name} is in use") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${u.candidateCount} candidate(s) belong to this party" +
                        if (u.electionNames.isNotEmpty()) " and it appears in: ${u.electionNames.joinToString()}." else ".")
                    Text("Existing election records keep their own copy of the party details. Choose where its candidates should move:")
                    Box {
                        OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                            Text(others.firstOrNull { it.id == target }?.name ?: "Independent (no party)")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Independent (no party)") }, onClick = { target = null; menu = false })
                            others.forEach { o -> DropdownMenuItem(text = { Text(o.name) }, onClick = { target = o.id; menu = false }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.delete(p.id, target) }) { Text("Reassign & delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.usage.value = null }) { Text("Keep party") } }
        )
    }
}

@Composable
fun PartyEditScreen(nav: NavHostController, id: String?) {
    val vm = appVm(key = "party-$id") { PartyEditVm(it, id) }
    val p by vm.party.collectAsState()
    val loaded by vm.loaded.collectAsState()
    MatamScaffold(if (id == null) "Add party" else "Edit party", nav) { pad ->
        if (!loaded) { LoadingBox(Modifier.padding(pad)); return@MatamScaffold }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ImageField("logo", p.logoFile, { f -> vm.update { it.copy(logoFile = f) } }, { vm.update { it.copy(logoFile = null) } })
                ImageField("banner", p.bannerFile, { f -> vm.update { it.copy(bannerFile = f) } }, { vm.update { it.copy(bannerFile = null) } })
            }
            EmojiField("Add party emoji", p.emoji) { v -> vm.update { it.copy(emoji = v) } }
            Text("Use a logo, an emoji, or both. The emoji is shown as the party symbol when there is no logo.", style = MaterialTheme.typography.bodySmall)
            FormField(p.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Party / organisation name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(p.abbreviation, { v -> vm.update { it.copy(abbreviation = v.take(6)) } }, label = { Text("Abbreviation (blank = initials of name)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(p.leader, { v -> vm.update { it.copy(leader = v) } }, label = { Text("Leader / representative (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(p.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text("Identification colour", style = MaterialTheme.typography.labelLarge)
            ColorPickerRow(p.colorArgb) { c -> vm.update { it.copy(colorArgb = c) } }
            Button(onClick = { vm.save { nav.popBackStack() } }, enabled = p.name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Save party") }
        }
    }
}

// ---------------- Candidates ----------------
@Composable
fun CandidateCardPreview(c: CandidateEntity, party: PartyEntity?, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(3.dp, Color((party?.colorArgb ?: 0xFF757575).toInt()))) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StoredImage(c.photoFile, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp), emoji = c.emoji)
            Spacer(Modifier.height(8.dp))
            Text(c.name.ifBlank { "Candidate name" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            if (c.designation.isNotBlank()) Text(c.designation, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PartySymbol(party?.logoFile, partyAbbr(party), party?.colorArgb ?: 0xFF757575, 32.dp, emoji = party?.emoji)
                Spacer(Modifier.width(8.dp))
                Column { Text(party?.name ?: "Independent", style = MaterialTheme.typography.labelLarge); Text("Serial ${c.serial.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
fun CandidatesScreen(nav: NavHostController) {
    val vm = appVm { CandidatesVm(it) }
    val list by vm.filtered.collectAsState()
    val all by vm.all.collectAsState()
    val parties by vm.parties.collectAsState()
    val q by vm.query.collectAsState()
    val pf by vm.partyFilter.collectAsState()
    val used by vm.usedIn.collectAsState()
    var preview by remember { mutableStateOf<CandidateEntity?>(null) }
    val pmap = parties.associateBy { it.id }

    MatamScaffold("Candidates", actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }, fab = {
        ExtendedFloatingActionButton(onClick = { nav.navigate("candidate/edit/new") }, icon = { Icon(Icons.Default.PersonAdd, null) }, text = { Text("Add") })
    }) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { vm.query.value = it }, "Search candidates", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                item { FilterChip(pf == "__all", { vm.partyFilter.value = "__all" }, { Text("All") }) }
                item { FilterChip(pf == null, { vm.partyFilter.value = null }, { Text("Independent") }) }
                items(parties, key = { it.id }) { p -> FilterChip(pf == p.id, { vm.partyFilter.value = p.id }, { Text(p.abbreviation.ifBlank { p.name }) }) }
            }
            if (all.isEmpty()) EmptyState(Icons.Default.People, "No candidates yet", "Register candidates with photos and parties.", Modifier.weight(1f))
            else if (list.isEmpty()) EmptyState(Icons.Default.SearchOff, "No matches", "Try another search or filter.", Modifier.weight(1f))
            else LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { c ->
                    val p = c.partyId?.let { pmap[it] }
                    Card(shape = RoundedCornerShape(18.dp), onClick = { preview = c }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StoredImage(c.photoFile, Modifier.size(64.dp), emoji = c.emoji)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("#${c.serial.ifBlank { "-" }} • ${p?.name ?: "Independent"}", style = MaterialTheme.typography.bodySmall)
                                if (c.designation.isNotBlank()) Text(c.designation, style = MaterialTheme.typography.labelSmall)
                            }
                            PartySymbol(p?.logoFile, partyAbbr(p), p?.colorArgb ?: 0xFF757575, 34.dp, emoji = p?.emoji)
                            IconButton(onClick = { nav.navigate("candidate/edit/${c.id}") }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { vm.requestDelete(c) }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    preview?.let { c ->
        AlertDialog(onDismissRequest = { preview = null }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } },
            title = { Text("Candidate card preview") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CandidateCardPreview(c, c.partyId?.let { pmap[it] }, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally))
                    LabeledText("Description", c.description); LabeledText("Manifesto", c.manifesto); LabeledText("Additional information", c.extraInfo)
                }
            })
    }
    used?.let { (c, names) ->
        ConfirmDialog("Delete ${c.name}?",
            if (names.isEmpty()) "This candidate profile will be deleted." else "Election records (${names.joinToString()}) keep their own copy of this candidate, so results are unaffected. Only the profile is removed.",
            "Delete", true, onConfirm = { vm.delete(c.id) }, onDismiss = { vm.usedIn.value = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateEditScreen(nav: NavHostController, id: String?) {
    val vm = appVm(key = "cand-$id") { CandidateEditVm(it, id) }
    val c by vm.candidate.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val parties by vm.parties.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val party = parties.firstOrNull { it.id == c.partyId }

    MatamScaffold(if (id == null) "Add candidate" else "Edit candidate", nav) { pad ->
        if (!loaded) { LoadingBox(Modifier.padding(pad)); return@MatamScaffold }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ImageField("photo", c.photoFile, { f -> vm.update { it.copy(photoFile = f) } }, { vm.update { it.copy(photoFile = null) } }, size = 120.dp)
                CandidateCardPreview(c, party, Modifier.weight(1f))
            }
            EmojiField("Add emoji", c.emoji) { v -> vm.update { it.copy(emoji = v) } }
            Text("Use a photo, an emoji, or both.", style = MaterialTheme.typography.bodySmall)
            FormField(c.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Full name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(c.serial, { v -> vm.update { it.copy(serial = v) } }, label = { Text("Candidate ID / serial number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            ExposedDropdownMenuBox(menu, { menu = it }) {
                FormField(party?.name ?: "Independent (no party)", {}, readOnly = true, label = { Text("Party") }, modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) })
                ExposedDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Independent (no party)") }, onClick = { vm.update { it.copy(partyId = null) }; menu = false })
                    parties.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.update { it.copy(partyId = p.id) }; menu = false }) }
                }
            }
            FormField(c.designation, { v -> vm.update { it.copy(designation = v) } }, label = { Text("Designation (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FormField(c.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            FormField(c.manifesto, { v -> vm.update { it.copy(manifesto = v) } }, label = { Text("Manifesto (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            FormField(c.extraInfo, { v -> vm.update { it.copy(extraInfo = v) } }, label = { Text("Additional information (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Changes affect new elections only. Elections already started keep the details they were created with.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.save { nav.popBackStack() } }, enabled = c.name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Save candidate") }
        }
    }
}
