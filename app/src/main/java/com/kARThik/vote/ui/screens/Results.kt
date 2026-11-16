package com.kARThik.vote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.kARThik.vote.data.*
import com.kARThik.vote.ui.*
import com.kARThik.vote.utils.fmtDate
import com.kARThik.vote.utils.pct
import kotlinx.coroutines.launch

@Composable
fun ResultsListScreen(nav: NavHostController) {
    val vm = appVm { ElectionsVm(it) }
    val all by vm.all.collectAsState()
    val q by vm.query.collectAsState()
    val list = all.filter { (it.voteCount > 0 || it.status == "COMPLETED" || it.status == "ONGOING" || it.status == "PAUSED") &&
        (q.isBlank() || it.name.contains(q, true)) }
    MatamScaffold("Results", actions = {
        IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            SearchField(q, { vm.query.value = it }, "Search results", Modifier.padding(horizontal = 16.dp))
            if (list.isEmpty()) EmptyState(Icons.Default.BarChart, "No results yet", "Results appear once an election has started recording votes.", Modifier.weight(1f))
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { e -> ElectionCard(e, onClick = { nav.navigate("results/${e.id}") }) }
            }
        }
    }
}

private val layoutNames = listOf("Candidates", "Parties", "Bar chart", "Pie chart", "Table")

@Composable
fun ResultsScreen(nav: NavHostController, id: String) {
    val vm = appVm(key = "results-$id") { ResultsVm(it, id) }
    val r by vm.results.collectAsState()
    val layout by vm.layout.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var exportFormat by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(id in UnlockedResults.ids) }
    var prompt by remember { mutableStateOf(true) }

    suspend fun bytes(fmt: String): ByteArray? = when (fmt) {
        "pdf" -> vm.exporter.pdfFor(id)
        "csv" -> vm.exporter.csvFor(id)?.toByteArray()
        else -> vm.exporter.electionJson(id)?.toString(2)?.toByteArray()
    }

    fun saveTo(uri: Uri?) {
        val fmt = pendingSave
        if (uri == null || fmt == null) return
        scope.launch {
            val b = bytes(fmt)
            val ok = b != null && runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) } }.isSuccess
            snack.showSnackbar(if (ok) "Saved ${fmt.uppercase()}" else "Export failed")
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { saveTo(it) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { saveTo(it) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { saveTo(it) }

    fun share(fmt: String) {
        scope.launch {
            val b = bytes(fmt) ?: return@launch
            val safe = (r?.election?.name ?: "election").replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = vm.exporter.cacheFile("$safe.$fmt", b)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val mime = when (fmt) { "pdf" -> "application/pdf"; "csv" -> "text/csv"; else -> "application/json" }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, "${r?.election?.name} results")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Share results"))
        }
    }

    MatamScaffold("Results", nav, snackbar = snack, actions = {
        if (unlocked) IconButton(onClick = { exportFormat = "menu" }) { Icon(Icons.Default.Share, "Export") }
    }) { pad ->
        if (!unlocked) {
            LockedPane(Modifier.padding(pad)) { prompt = true }
            if (prompt) PasswordPrompt(id, "View results", onSuccess = { unlocked = true; UnlockedResults.ids.add(id); prompt = false }, onDismiss = { prompt = false })
            return@MatamScaffold
        }
        val res = r
        if (res == null || layout < 0) { LoadingBox(Modifier.padding(pad)); return@MatamScaffold }
        Column(Modifier.padding(pad).fillMaxSize().swipeTabs({ if (layout < layoutNames.lastIndex) vm.layout.value = layout + 1 }, { if (layout > 0) vm.layout.value = layout - 1 }).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(res.election.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(50), color = if (res.isFinal) Color(0xFF2E7D32) else Color(0xFFEF6C00)) {
                    Text(if (res.isFinal) "FINAL" else "PROVISIONAL", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(fmtDate(res.election.dateMillis))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Total votes", "${res.totalVotes}", Icons.Default.Ballot, Modifier.weight(1f))
                if (res.cancelledCount > 0) StatCard("Cancelled/invalid", "${res.cancelledCount}", Icons.Default.Block, Modifier.weight(1f))
                if (res.pendingCount > 0) StatCard("Unconfirmed", "${res.pendingCount}", Icons.Default.HourglassTop, Modifier.weight(1f))
            }
            if (res.winners.isNotEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3C4))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StoredImage(res.winners.first().candidate.photoFile, Modifier.size(64.dp), emoji = res.winners.first().candidate.emoji)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (res.isFinal) "Winner" else "Currently leading", color = Color(0xFF5D4037), style = MaterialTheme.typography.labelLarge)
                            Text(res.winners.joinToString(" & ") { it.candidate.name }, color = Color(0xFF3E2723), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${res.winners.first().votes} votes" + if (res.winners.size > 1) " (tie)" else "", color = Color(0xFF5D4037))
                        }
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(layoutNames.size) { i -> FilterChip(layout == i, { vm.layout.value = i }, { Text(layoutNames[i]) }) }
            }
            if (res.totalVotes == 0) EmptyState(Icons.Default.Ballot, "No votes recorded", "Counts will appear as soon as votes are recorded.")
            else when (layout) {
                0 -> res.candidates.forEach { CandidateResultCard(it, res.winners.any { w -> w.candidate.candidateId == it.candidate.candidateId }, settings.showPercentages) }
                1 -> res.parties.forEach { PartyResultCard(it, settings.showPercentages) }
                2 -> BarChart(res)
                3 -> PieChart(res)
                else -> ResultTable(res)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (exportFormat != null) {
        AlertDialog(
            onDismissRequest = { exportFormat = null }, title = { Text("Export results") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (r?.isFinal == false) Text("This election is not finalised. Exports will be marked PROVISIONAL.", style = MaterialTheme.typography.bodySmall)
                    listOf("pdf" to "PDF report", "csv" to "CSV spreadsheet", "json" to "JSON backup").forEach { (fmt, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            TextButton(onClick = {
                                val safe = (r?.election?.name ?: "election").replace(Regex("[^A-Za-z0-9_-]"), "_")
                                pendingSave = fmt
                                when (fmt) { "pdf" -> pdfLauncher.launch("$safe.pdf"); "csv" -> csvLauncher.launch("$safe.csv"); else -> jsonLauncher.launch("$safe.json") }
                                exportFormat = null
                            }) { Text("Save") }
                            TextButton(onClick = { share(fmt); exportFormat = null }) { Text("Share") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exportFormat = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun CandidateResultCard(c: CandidateResult, winner: Boolean, showPct: Boolean) {
    val color = Color(c.candidate.partyColor.toInt())
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${c.rank}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color, modifier = Modifier.width(44.dp))
                StoredImage(c.candidate.photoFile, Modifier.size(60.dp), emoji = c.candidate.emoji)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.candidate.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        if (winner) Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF9A825), modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(c.candidate.partyName, style = MaterialTheme.typography.bodySmall)
                }
                PartySymbol(c.candidate.partyLogoFile, c.candidate.partyAbbr, c.candidate.partyColor, 38.dp, emoji = c.candidate.partyEmoji)
            }
            Spacer(Modifier.height(10.dp))
            val prog by animateFloatAsState((c.percent / 100.0).toFloat(), label = "bar")
            LinearProgressIndicator(progress = { prog }, Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = color, trackColor = color.copy(alpha = 0.15f))
            Spacer(Modifier.height(4.dp))
            Text("${c.votes} votes" + if (showPct) "  •  ${pct(c.percent)}" else "", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PartyResultCard(p: PartyResult, showPct: Boolean) {
    val color = Color(p.color.toInt())
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PartySymbol(p.logoFile, p.abbr, p.color, 48.dp, emoji = p.emoji); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold); Text("${p.candidateCount} candidate(s)", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${p.votes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (showPct) Text(pct(p.percent), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (p.percent / 100.0).toFloat() }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = color, trackColor = color.copy(alpha = 0.15f))
        }
    }
}

@Composable
private fun BarChart(r: ElectionResults) {
    val max = (r.candidates.maxOfOrNull { it.votes } ?: 1).coerceAtLeast(1)
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            r.candidates.forEach { c ->
                Column {
                    Row { Text(c.candidate.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${c.votes}", fontWeight = FontWeight.Bold) }
                    val frac by animateFloatAsState(c.votes.toFloat() / max, label = "h")
                    Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.005f)).fillMaxHeight().background(Color(c.candidate.partyColor.toInt())))
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChart(r: ElectionResults) {
    val slices = r.candidates.filter { it.votes > 0 }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val palette = PartyColors.map { Color(it.toInt()) }
            val colors = slices.mapIndexed { i, _ -> palette[i % palette.size] }
            val sweepProgress by animateFloatAsState(1f, label = "pie")
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    var start = -90f
                    val stroke = 48.dp.toPx()
                    val inset = stroke / 2
                    slices.forEachIndexed { i, s ->
                        val sweep = (s.votes.toFloat() / r.totalVotes) * 360f * sweepProgress
                        drawArc(colors[i], start, sweep - 1.5f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.totalVotes}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("votes")
                }
            }
            Spacer(Modifier.height(12.dp))
            slices.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(colors[i])); Spacer(Modifier.width(8.dp))
                    Text(s.candidate.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.votes} (${pct(s.percent)})")
                }
            }
        }
    }
}

@Composable
private fun ResultTable(r: ElectionResults) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.padding(vertical = 6.dp)) {
                Text("#", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                Text("Candidate", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Party", Modifier.width(56.dp), fontWeight = FontWeight.Bold)
                Text("Votes", Modifier.width(48.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("%", Modifier.width(52.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
            HorizontalDivider()
            r.candidates.forEach { c ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.rank}", Modifier.width(28.dp))
                    Text(c.candidate.name, Modifier.weight(1f), maxLines = 2)
                    Text(c.candidate.partyAbbr, Modifier.width(56.dp), maxLines = 1)
                    Text("${c.votes}", Modifier.width(48.dp), textAlign = TextAlign.End)
                    Text(pct(c.percent), Modifier.width(52.dp), textAlign = TextAlign.End)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${r.totalVotes}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
