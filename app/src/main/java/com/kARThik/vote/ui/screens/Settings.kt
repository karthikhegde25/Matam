package com.kARThik.vote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kARThik.vote.data.VotingMode
import com.kARThik.vote.settings.ThemeMode
import com.kARThik.vote.ui.*
import com.kARThik.vote.voting.Buzzer
import com.kARThik.vote.utils.AppInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm = appVm { SettingsVm(it) }
    val s by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var volume by remember(s.buzzerVolume) { mutableFloatStateOf(s.buzzerVolume) }

    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value = null } }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.backupTo { bytes -> ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot open file") }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text != null) vm.restore(text) else vm.message.value = "Could not read file"
        }
    }

    MatamScaffold("Settings", nav, snackbar = snack) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Group("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.values().forEach { t -> FilterChip(s.theme == t, { vm.setTheme(t) }, { Text(t.label) }) }
                }
            }
            Group("Default voting mode") {
                VotingMode.values().forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.defaultMode == m.name, { vm.launch { vm.s.setDefaultMode(m.name) } }); Text(m.label)
                    }
                }
            }
            Group("Buzzer") {
                SwitchRow("Play buzzer after a vote", s.buzzerEnabled) { v -> vm.launch { vm.s.setBuzzer(v) } }
                Text("Sound", style = MaterialTheme.typography.labelLarge)
                Buzzer.sounds.forEachIndexed { i, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.buzzerSound == i, { vm.launch { vm.s.setSound(i) } }); Text(name, Modifier.weight(1f))
                    }
                }
                Text("Volume", style = MaterialTheme.typography.labelLarge)
                Slider(volume, { volume = it }, onValueChangeFinished = { vm.launch { vm.s.setVolume(volume) } })
                FilledTonalButton(onClick = { vm.preview() }) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("Preview sound") }
            }
            Group("Vibration") { SwitchRow("Vibrate after a vote", s.vibration) { v -> vm.launch { vm.s.setVibration(v) } } }
            Group("Results display") {
                SwitchRow("Show percentages", s.showPercentages) { v -> vm.launch { vm.s.setShowPercent(v) } }
                Text("Default layout", style = MaterialTheme.typography.labelLarge)
                listOf("Candidate cards", "Party summary", "Bar chart", "Pie chart", "Table").forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.defaultResultLayout == i, { vm.launch { vm.s.setResultLayout(i) } }); Text(n)
                    }
                }
            }
            Group("Bluetooth") {
                SwitchRow("Auto-reconnect terminal", s.btAutoReconnect) { v -> vm.launch { vm.s.setReconnect(v) } }
                SwitchRow("Keep screen on while connected", s.btKeepScreenOn) { v -> vm.launch { vm.s.setScreenOn(v) } }
                OutlinedButton(onClick = { nav.navigate("bt/terminal") }) { Icon(Icons.Default.Bluetooth, null); Spacer(Modifier.width(6.dp)); Text("Open voting terminal") }
            }
            Group("Backup and restore") {
                Text("Backs up parties, candidates, elections, votes, audit logs and images to a JSON file.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { backupLauncher.launch("matam-backup.json") }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Create full backup") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(6.dp)); Text("Restore from file") }
            }
            Group("Database management") {
                OutlinedButton(onClick = { typed = ""; dialog = "history" }) { Text("Delete election history") }
                Button(onClick = { typed = ""; dialog = "all" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Clear all application data") }
            }
            Group("Privacy") {
                Text("Matam works fully offline. Everything stays on this device. Votes store only an anonymous transaction ID, candidate, session, time and an app-generated device ID. No voter identity is collected. Bluetooth is used only to link two phones for supervised voting and is not voter authentication. Exports are shared only when you choose.", style = MaterialTheme.typography.bodyMedium)
            }
            Group("About") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.foundation.Image(
                        painterResource(com.kARThik.vote.R.drawable.app_logo), "Matam logo",
                        Modifier.size(96.dp).clip(RoundedCornerShape(22.dp))
                    )
                    Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Version ${AppInfo.VERSION}", style = MaterialTheme.typography.bodyMedium)
                    Text("Developer: ${AppInfo.DEVELOPER}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Open source app licensed under the ${AppInfo.LICENSE}.",
                        style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(
                        onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(AppInfo.REPO_URL))) } },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(painterResource(com.kARThik.vote.R.drawable.ic_github), "Open GitHub repository", Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("github.com/karthikhegde25/Matam", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Device ID: ${s.deviceId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (dialog == "history") AlertDialog(
        onDismissRequest = { dialog = null }, title = { Text("Delete election history?") },
        text = {
            Column {
                Text("All completed elections and their results will be permanently deleted. Because this covers many elections at once, their individual passwords are not asked. Type DELETE to confirm.")
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = typed == "DELETE", onClick = { vm.deleteHistory(); dialog = null }) { Text("Delete history", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
    )
    if (dialog == "all") AlertDialog(
        onDismissRequest = { dialog = null }, title = { Text("Clear all data?") },
        text = {
            Column {
                Text("This permanently deletes every election, vote, party, candidate, image and audit record. Consider a backup first. Type DELETE to confirm.")
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = typed == "DELETE", onClick = { vm.clearAll(); dialog = null }) { Text("Clear everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
    )
}
