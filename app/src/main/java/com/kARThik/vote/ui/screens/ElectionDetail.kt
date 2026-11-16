package com.kARThik.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kARThik.vote.utils.fmtDateTime

@Composable
fun ElectionDetailScreen(nav: NavHostController, id: String) {
    val vm = appVm(key = "detail-$id") { ElectionDetailVm(it, id) }
    val election by vm.election.collectAsState()
    val cands by vm.candidates.collectAsState()
    val results by vm.results.collectAsState()
    val votes by vm.votes.collectAsState()
    val audit by vm.audit.collectAsState()
    val pending by vm.pending.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmFinalize by remember { mutableStateOf(false) }
    var confirmDeleteResult by remember { mutableStateOf(false) }
    var voidTx by remember { mutableStateOf<String?>(null) }
    var gate by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(id in UnlockedResults.ids) }
    var removeCand by remember { mutableStateOf<ElectionCandidateEntity?>(null) }

    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMessage() } }

    val e = election
    MatamScaffold(e?.name ?: "Election", nav, snackbar = snack, actions = {
        if (e != null) {
            IconButton(onClick = { gate = "edit" }) { Icon(Icons.Default.Edit, "Edit election") }
            IconButton(onClick = { gate = "delete" }) { Icon(Icons.Default.Delete, "Delete") }
        }
    }) { pad ->
        if (e == null) { LoadingBox(Modifier.padding(pad)); return@MatamScaffold }
        val status = ElectionStatus.valueOf(e.status)
        val bt = e.votingMode == VotingMode.BLUETOOTH.name
        Column(Modifier.padding(pad)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(e.status); Spacer(Modifier.width(8.dp))
                    Text("${fmtDate(e.dateMillis)} • ${e.category}", style = MaterialTheme.typography.bodyMedium)
                }
                if (pending > 0) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$pending vote(s) awaiting confirmation from the controller.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.acceptPending() }) { Text("Accept here") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when (status) {
                        ElectionStatus.DRAFT -> {
                            OutlinedButton({ gate = "edit" }, Modifier.weight(1f)) { Text("Edit") }
                            Button({ vm.setStatus(ElectionStatus.READY) }, Modifier.weight(1f)) { Text("Mark ready") }
                        }
                        ElectionStatus.READY -> {
                            OutlinedButton({ gate = "edit" }, Modifier.weight(1f)) { Text("Edit") }
                            Button({ nav.navigate(if (bt) "bt/controller/$id" else "vote/$id") }, Modifier.weight(1.5f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text(if (bt) "Open controller" else "Start voting", maxLines = 1)
                            }
                        }
                        ElectionStatus.ONGOING, ElectionStatus.PAUSED -> {
                            Button({ gate = "continue" }, Modifier.weight(1.3f)) {
                                Text(if (status == ElectionStatus.PAUSED) "Resume" else "Continue", maxLines = 1)
                            }
                            FilledTonalButton({ gate = "finalize" }, Modifier.weight(1f)) { Text("Complete") }
                        }
                        ElectionStatus.COMPLETED -> {
                            Button({ nav.navigate("results/$id") }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("View results", maxLines = 1) }
                            OutlinedButton({ gate = "deleteResult" }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Delete result", maxLines = 1) }
                        }
                    }
                }
                if (status == ElectionStatus.READY) TextButton({ vm.setStatus(ElectionStatus.DRAFT) }) { Text("Move back to draft") }
            }
            ScrollableTabRow(tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Info", "Candidates", "Parties", "Results", "Votes", "Audit").forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t) })
                }
            }
            val tabCount = 6
            Box(Modifier.fillMaxSize().swipeTabs({ if (tab < tabCount - 1) tab++ }, { if (tab > 0) tab-- })) {
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    LabeledText("Description", e.description)
                    LabeledText("Location", e.location)
                    LabeledText("Time", listOf(e.startTime, e.endTime).filter { it.isNotBlank() }.joinToString(" – "))
                    LabeledText("Voting system", VotingMode.valueOf(e.votingMode).label)
                    LabeledText("Instructions", e.instructions)
                    LabeledText("Candidates", "${cands.size}")
                    LabeledText("Valid votes", "${results?.totalVotes ?: 0}")
                    LabeledText("Created", fmtDateTime(e.createdAt))
                    e.finalizedAt?.let { LabeledText("Finalised", fmtDateTime(it)) }
                    LabeledText("Election ID", e.id)
                }
                1 -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(cands, key = { it.candidateId }) { c ->
                        CandidateRow(c) {
                            if (status == ElectionStatus.DRAFT || status == ElectionStatus.READY)
                                IconButton(onClick = { removeCand = c }) { Icon(Icons.Default.RemoveCircleOutline, "Remove from election") }
                        }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results?.parties.orEmpty(), key = { it.key }) { p ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                PartySymbol(p.logoFile, p.abbr, p.color, 44.dp, emoji = p.emoji); Spacer(Modifier.width(12.dp))
                                Column { Text(p.name, fontWeight = FontWeight.SemiBold); Text("${p.candidateCount} candidate(s)", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                3 -> if (!unlocked) LockedPane { gate = "unlock" } else Column(Modifier.padding(16.dp)) {
                    results?.let { r ->
                        Text(if (r.isFinal) "Final result" else "Provisional result", fontWeight = FontWeight.Bold)
                        Text("Total valid votes: ${r.totalVotes}")
                        r.candidates.forEach { c -> Text("#${c.rank}  ${c.candidate.name} — ${c.votes} (${"%.1f".format(c.percent)}%)") }
                        Spacer(Modifier.height(12.dp))
                        Button({ nav.navigate("results/$id") }) { Text("Open full results") }
                    }
                }
                4 -> if (!unlocked) LockedPane { gate = "unlock" } else {
                    val names = cands.associate { it.candidateId to it.name }
                    if (votes.isEmpty()) EmptyState(Icons.Default.Ballot, "No votes yet", "Recorded votes appear here without any voter identity.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(votes, key = { it.transactionId }) { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(names[v.candidateId] ?: v.candidateId, fontWeight = FontWeight.SemiBold)
                                    Text("${fmtDateTime(v.timestamp)} • tx ${v.transactionId.take(8)} • ${v.status}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (status != ElectionStatus.COMPLETED && (v.status == "VALID" || v.status == "PENDING"))
                                    TextButton(onClick = { voidTx = v.transactionId }) { Text("Void") }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                5 -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(audit, key = { it.id }) { a ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(a.action.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                            Text(a.details, style = MaterialTheme.typography.bodySmall)
                            Text(fmtDateTime(a.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete election?", "The election, its candidates snapshot, votes and results will be permanently removed.", "Delete", true,
        onConfirm = { vm.delete { nav.popBackStack() } }, onDismiss = { confirmDelete = false })
    gate?.let { g ->
        PasswordPrompt(
            id, when (g) { "finalize" -> "Complete election"; "deleteResult" -> "Delete result"; "delete" -> "Delete election"; "edit" -> "Edit election"; "continue" -> "Continue election"; else -> "Unlock results and votes" },
            onSuccess = { when (g) { "finalize" -> confirmFinalize = true; "deleteResult" -> confirmDeleteResult = true; "delete" -> confirmDelete = true
                "edit" -> nav.navigate("election/edit/$id")
                "continue" -> nav.navigate(if (election?.votingMode == VotingMode.BLUETOOTH.name) "bt/controller/$id" else "vote/$id")
                else -> { unlocked = true; UnlockedResults.ids.add(id) } }; gate = null },
            onDismiss = { gate = null }
        )
    }
    if (confirmFinalize) ConfirmDialog("Complete election?", "Results will be finalised and no more votes can be recorded.", "Complete",
        onConfirm = { vm.finalize() }, onDismiss = { confirmFinalize = false })
    if (confirmDeleteResult) ConfirmDialog("Delete finalised result?", "The final result snapshot is removed and the election reopens as paused. This is recorded in the audit log.", "Delete result", true,
        onConfirm = { vm.deleteResult() }, onDismiss = { confirmDeleteResult = false })
    removeCand?.let { c ->
        ConfirmDialog("Remove candidate?", "Remove ${c.name} from this election?", "Remove", true,
            onConfirm = { vm.removeCandidate(c.candidateId) }, onDismiss = { removeCand = null })
    }
    voidTx?.let { tx ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidTx = null }, title = { Text("Void this vote?") },
            text = {
                Column {
                    Text("The record is kept but no longer counted. A reason is required and is written to the audit log.")
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { vm.voidVote(tx, reason.trim()); voidTx = null }) { Text("Void vote") } },
            dismissButton = { TextButton(onClick = { voidTx = null }) { Text("Cancel") } }
        )
    }
}
