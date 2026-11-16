package com.kARThik.vote.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kARThik.vote.bluetooth.*
import com.kARThik.vote.ui.*
import kotlinx.coroutines.launch

private fun btPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** Handles availability check, permission request and enabling Bluetooth. Calls [onReady] when usable. */
@Composable
private fun BtGate(link: BluetoothLink, onReady: @Composable () -> Unit) {
    var perms by remember { mutableStateOf(link.hasPermissions()) }
    var enabled by remember { mutableStateOf(link.enabled) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms = link.hasPermissions() }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { enabled = link.enabled }

    when {
        !link.supported -> BigMessage(Icons.Default.BluetoothDisabled, "Bluetooth not available", "This device does not support Bluetooth.", MaterialTheme.colorScheme.error)
        !perms -> BigMessage(Icons.Default.Bluetooth, "Bluetooth permission needed", "Matam needs nearby-device permission to connect the two phones. It is used only for this connection.", MaterialTheme.colorScheme.primary) {
            Button(onClick = { permLauncher.launch(btPermissions()) }) { Text("Grant permission") }
        }
        !enabled -> BigMessage(Icons.Default.BluetoothDisabled, "Bluetooth is off", "Turn Bluetooth on to continue.", Color(0xFFEF6C00)) {
            Button(onClick = { enableLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) { Text("Turn on Bluetooth") }
        }
        else -> onReady()
    }
}

@Composable
private fun ConnectionBanner(ui: BtUiState) {
    val (text, color) = when {
        ui.interrupted -> "Connection interrupted – reconnecting" to Color(0xFFD84315)
        ui.link == LinkState.CONNECTED && ui.electionId.isBlank() -> "Connected to ${ui.peerName ?: "device"}" to Color(0xFF2E7D32)
        ui.link == LinkState.CONNECTED && ui.synced -> "Connected to ${ui.peerName ?: "device"} • synchronised" to Color(0xFF2E7D32)
        ui.link == LinkState.CONNECTED -> "Connected to ${ui.peerName ?: "device"} • synchronising…" to Color(0xFF1565C0)
        ui.link == LinkState.LISTENING -> "Waiting for the voting machine…" to Color(0xFF1565C0)
        ui.link == LinkState.CONNECTING -> "Connecting…" to Color(0xFF1565C0)
        else -> "Not connected" to Color(0xFF616161)
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ui.link == LinkState.CONNECTED) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text, color = color, fontWeight = FontWeight.SemiBold)
                if (ui.link != LinkState.CONNECTED) ui.error?.let { Text(it, color = Color(0xFFB71C1C), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun FeedbackSnackbar(vm: BtVm, snack: SnackbarHostState) {
    LaunchedEffect(Unit) { vm.bt.feedback.collect { snack.showSnackbar(it) } }
}

// ---------------- Role chooser ----------------
@Composable
fun BtStartScreen(nav: NavHostController) {
    MatamScaffold("Bluetooth voting", nav) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth > maxHeight
            @Composable
            fun RoleCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, color: Color, modifier: Modifier, route: String) {
                Card(onClick = { nav.navigate(route) }, modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
                    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icon, null, Modifier.size(56.dp), tint = color)
                        Spacer(Modifier.height(10.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val controller: @Composable (Modifier) -> Unit = { m ->
                RoleCard(Icons.Default.AdminPanelSettings, "Use this phone as Controller", "Pick the election, approve each voter and see the results. Open this on the phone first.", Color(0xFF1565C0), m, "bt/controller")
            }
            val machine: @Composable (Modifier) -> Unit = { m ->
                RoleCard(Icons.Default.HowToVote, "Use this phone as Voting machine", "Voters cast their vote here. The controller decides which election it shows.", Color(0xFF2E7D32), m, "bt/terminal")
            }
            if (wide) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                controller(Modifier.weight(1f).fillMaxHeight()); machine(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose what this phone does. Start the Controller first, then connect the Voting machine to it.", style = MaterialTheme.typography.bodyMedium)
                controller(Modifier.weight(1f).fillMaxWidth()); machine(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

// ---------------- Controller ----------------
@Composable
fun BtControllerScreen(nav: NavHostController, preselect: String = "") {
    val vm = appVm(key = "btc") { BtVm(it) }
    val ui by vm.ui.collectAsState()
    val log by vm.log.collectAsState()
    val elections by vm.elections.collectAsState()
    val snack = remember { SnackbarHostState() }
    var exit by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf(false) }
    var gateResume by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val electionId = ui.electionId

    FeedbackSnackbar(vm, snack)
    KeepScreenOn()
    BackHandler { exit = true }

    MatamScaffold("Election Controller", snackbar = snack, showBack = false, actions = {
        IconButton(onClick = { exit = true }) { Icon(Icons.Default.Close, "Close") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            BtGate(vm.bt.link) {
                LaunchedEffect(Unit) {
                    if (ui.role != BtRole.CONTROLLER) {
                        vm.bt.startController()
                        if (preselect.isNotBlank()) vm.bt.selectElection(preselect)
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionBanner(ui)
                    if (ui.link != LinkState.CONNECTED && electionId.isBlank()) {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Connect the voting machine", fontWeight = FontWeight.Bold)
                                Text("1. Keep this screen open.\n2. On the other phone choose \"Use this phone as Voting machine\" and tap this phone in the list.\n3. If this phone is not listed, make it discoverable below or pair the phones in Android Bluetooth settings.", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = {
                                    discoverable.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
                                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text("Make this phone discoverable (5 min)") }
                            }
                        }
                    }
                    if (electionId.isBlank()) {
                        run {
                            SectionTitle(if (ui.link == LinkState.CONNECTED) "Choose the election for the voting machine" else "Choose the election (it is sent when the voting machine connects)")
                            val shown = elections.filter { it.status != "COMPLETED" }
                            if (shown.isEmpty()) Text(
                                if (elections.isEmpty()) "This phone has no elections yet. Create one on this phone (Elections tab), add parties and candidates, mark it Ready, then come back."
                                else "All elections on this phone are completed. Create a new election first.",
                                style = MaterialTheme.typography.bodyMedium)
                            shown.forEach { e ->
                                val ok = e.status == "READY" || e.status == "ONGOING" || e.status == "PAUSED"
                                Card(onClick = { if (ok) pick = e.id }, enabled = ok, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.HowToVote, null); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                e.status.lowercase().replaceFirstChar { it.uppercase() } + if (!ok) " – open it in Elections and tap Mark ready first" else if (e.location.isNotBlank()) " • ${e.location}" else "",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (ok) Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text("The election password is needed to start it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                LabeledText("Election", ui.electionName)
                                LabeledText("Session status", when {
                                    ui.paused -> "Paused"; ui.activeSessionId != null -> "Voter is voting…"
                                    ui.terminalReady -> "Voting machine ready for next voter"; ui.synced -> "Waiting for voting machine" else -> "Not synchronised"
                                })
                                LabeledText("Votes recorded", "${ui.votesRecorded}")
                            }
                        }
                        if (ui.interrupted) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Text("The connection was interrupted. Votes already recorded are safe. If a voter was mid-vote, their vote will be confirmed automatically when the voting machine reconnects.", Modifier.padding(12.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (ui.terminalReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ui.terminalReady) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline)
                            Text(if (ui.terminalReady) "Ready for next voting session" else "Voting machine not ready", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { vm.bt.approveNext() }, enabled = ui.synced && ui.terminalReady && !ui.paused && ui.activeSessionId == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp), shape = RoundedCornerShape(22.dp)
                        ) { Icon(Icons.Default.HowToVote, null); Spacer(Modifier.width(8.dp)); Text("Approve next session", fontSize = 20.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (ui.paused) FilledTonalButton({ gateResume = true }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Resume") }
                            else FilledTonalButton({ vm.bt.pause() }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Pause") }
                            OutlinedButton({ vm.bt.cancelActiveSession() }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = ui.activeSessionId != null) { Text("Cancel session", maxLines = 1) }
                        }
                        OutlinedButton(onClick = { vm.bt.setLandscape(!ui.landscape) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ScreenRotation, null); Spacer(Modifier.width(6.dp))
                            Text(if (ui.landscape) "Voting machine: landscape (tap for portrait)" else "Voting machine: portrait (tap for landscape)")
                        }
                        OutlinedButton(onClick = { vm.bt.clearElection() }, Modifier.fillMaxWidth(), enabled = ui.activeSessionId == null) {
                            Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Change election")
                        }
                        OutlinedButton(onClick = { gate = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.TaskAlt, null); Spacer(Modifier.width(6.dp)); Text("Complete election") }
                    }
                    Text("Bluetooth pairing is not voter authentication. This mode is for supervised, non-binding voting.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionTitle("Activity")
                    log.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (exit) ConfirmDialog("Close controller?", "The Bluetooth connection will be closed. Recorded votes remain saved and the election stays open.", "Close",
        onConfirm = {
            val id = electionId
            vm.bt.stop()
            if (id.isNotBlank()) vm.c.appScope.launch { vm.c.repo.getElection(id)?.let { if (it.status == "ONGOING") vm.c.repo.setStatus(id, com.kARThik.vote.data.ElectionStatus.PAUSED) } }
            nav.popBackStack()
        }, onDismiss = { exit = false })
    pick?.let { pid ->
        PasswordPrompt(pid, "Start election on voting machine", onSuccess = { pick = null; vm.bt.selectElection(pid) }, onDismiss = { pick = null })
    }
    if (gateResume && electionId.isNotBlank()) PasswordPrompt(electionId, "Resume voting", onSuccess = { gateResume = false; vm.bt.resume() }, onDismiss = { gateResume = false })
    if (gate && electionId.isNotBlank()) PasswordPrompt(electionId, "Complete election", onSuccess = { gate = false; finish = true }, onDismiss = { gate = false })
    if (finish && electionId.isNotBlank()) {
        val scope = rememberCoroutineScope()
        ConfirmDialog("Complete election?", "Results will be finalised and the voting machine will stop accepting votes.", "Complete", onConfirm = {
            scope.launch {
                val err = vm.c.repo.finalizeElection(electionId)
                if (err != null) snack.showSnackbar(err) else { vm.bt.stop(); nav.popBackStack(); nav.navigate("results/$electionId") }
            }
        }, onDismiss = { finish = false })
    }
}

// ---------------- Terminal ----------------
@Composable
fun BtTerminalScreen(nav: NavHostController) {
    val vm = appVm(key = "btt") { BtVm(it) }
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val snack = remember { SnackbarHostState() }
    var exit by remember { mutableStateOf(false) }
    val cands by remember(ui.electionId) { vm.electionCandidates(ui.electionId) }.collectAsState(initial = emptyList())
    val btElection by remember(ui.electionId) { vm.c.repo.observeElection(ui.electionId) }.collectAsState(initial = null)
    var picked by remember { mutableStateOf<com.kARThik.vote.data.ElectionCandidateEntity?>(null) }

    val landscape = rememberLandscapeMode()
    LaunchedEffect(ui.landscape) { landscape.value = ui.landscape }
    FeedbackSnackbar(vm, snack)
    KeepScreenOn(settings.btKeepScreenOn)
    BackHandler { if (ui.role == BtRole.NONE) nav.popBackStack() else exit = true }

    // After success/reject, show the message briefly then return to waiting state.
    LaunchedEffect(ui.phase) {
        if (ui.phase == TerminalPhase.SUCCESS || ui.phase == TerminalPhase.REJECTED) {
            kotlinx.coroutines.delay(2500); picked = null; vm.bt.terminalNextVoter()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (ui.role != BtRole.TERMINAL) {
                TerminalPicker(vm, nav)
            } else {
                var menu by remember { mutableStateOf(false) }
                val healthy = ui.link == LinkState.CONNECTED && ui.electionId.isNotBlank() && !ui.interrupted && ui.synced
                Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // The status banner only takes space while something needs attention.
                    if (!healthy) Box(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 104.dp)) { ConnectionBanner(ui) }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            ui.phase == TerminalPhase.BALLOT && picked != null -> ConfirmVote(picked!!, { vm.bt.submitVote(picked!!.candidateId) }, { picked = null })
                            ui.phase == TerminalPhase.BALLOT -> Column(Modifier.fillMaxSize()) {
                                Text("Select your candidate", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 104.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                BallotView(btElection?.ballotStyle, cands, { picked = it }, Modifier.weight(1f))
                            }
                            ui.phase == TerminalPhase.AWAITING_ACK -> BigMessage(Icons.Default.HourglassTop, "Saving your vote…",
                                if (ui.link == LinkState.CONNECTED) "Waiting for the controller to confirm." else "Your vote is stored safely and will be confirmed when the connection returns.", MaterialTheme.colorScheme.primary)
                            ui.phase == TerminalPhase.SUCCESS -> BigMessage(Icons.Default.CheckCircle, "Vote recorded", "Thank you. Please step away.", Color(0xFF2E7D32))
                            ui.phase == TerminalPhase.REJECTED -> BigMessage(Icons.Default.ErrorOutline, "Vote not accepted", ui.rejectReason.ifBlank { "Please ask the operator." }, MaterialTheme.colorScheme.error)
                            ui.paused -> BigMessage(Icons.Default.PauseCircle, "Voting paused", "The controller has paused voting.", Color(0xFFEF6C00))
                            ui.phase == TerminalPhase.WAITING_APPROVAL -> BigMessage(Icons.Default.HowToVote, ui.electionName, "Waiting for the controller to approve the next voter.", MaterialTheme.colorScheme.primary) {
                                if (ui.unconfirmedVotes > 0) Text("${ui.unconfirmedVotes} vote(s) awaiting confirmation", style = MaterialTheme.typography.labelLarge)
                            }
                            ui.link == LinkState.CONNECTED && ui.electionId.isBlank() -> BigMessage(Icons.Default.HowToVote, "Connected", "Waiting for the controller to choose an election…", MaterialTheme.colorScheme.primary)
                            else -> BigMessage(Icons.Default.Sync, "Connecting", if (ui.interrupted) "Reconnecting to the controller…" else "Synchronising election data…", MaterialTheme.colorScheme.primary) {
                                OutlinedButton(onClick = { vm.bt.reconnect() }) { Text("Retry now") }
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                    RotateButton(landscape)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (ui.link == LinkState.CONNECTED) "Connected to ${ui.peerName ?: "controller"}" else "Not connected") }, onClick = {}, enabled = false)
                            DropdownMenuItem(text = { Text("Disconnect (operator)") }, onClick = { menu = false; exit = true })
                        }
                    }
                }
                }
            }
        }
    }
    if (exit) ConfirmDialog("Disconnect terminal?", if (ui.unconfirmedVotes > 0) "${ui.unconfirmedVotes} vote(s) are saved but not yet confirmed by the controller. Reconnect later to sync them." else "Leave the voting terminal?", "Disconnect",
        onConfirm = { vm.bt.stop(); nav.popBackStack() }, onDismiss = { exit = false })
}

@Composable
private fun TerminalPicker(vm: BtVm, nav: NavHostController) {
    val link = vm.bt.link
    val discovered by link.discovered.collectAsState()
    val discovering by link.discovering.collectAsState()
    var bonded by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val pairings by vm.pairings.collectAsState()

    Column(Modifier.fillMaxSize()) {
        MatamScaffold("Voting machine", nav) { pad ->
            Column(Modifier.padding(pad)) {
                BtGate(link) {
                    LaunchedEffect(Unit) { bonded = link.bonded() }
                    DisposableEffect(Unit) { onDispose { link.stopDiscovery() } }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text("Choose the phone that is set up as Controller. On that phone choose \"Use this phone as Controller\" first and keep it waiting.", style = MaterialTheme.typography.bodyMedium)
                        }
                        item { SectionTitle("Paired devices") }
                        if (bonded.isEmpty()) item { Text("No paired devices. Pair in Android settings or scan below.", style = MaterialTheme.typography.bodySmall) }
                        items(bonded, key = { it.address }) { d -> DeviceRow(d, vm, pairings.any { it.address == d.address }) }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionTitle("Nearby devices", Modifier.weight(1f))
                                if (discovering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                TextButton(onClick = { link.startDiscovery() }) { Text(if (discovering) "Scanning" else "Scan") }
                            }
                        }
                        items(discovered.filter { d -> bonded.none { it.address == d.address } }, key = { it.address }) { d -> DeviceRow(d, vm, false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: BluetoothDevice, vm: BtVm, previous: Boolean) {
    val name = try { d.name } catch (e: SecurityException) { null } ?: "Unknown device"
    Card(onClick = { vm.bt.link.stopDiscovery(); vm.bt.startTerminal(d) }, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PhoneAndroid, null); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold); Text(d.address, style = MaterialTheme.typography.bodySmall)
            }
            if (previous) Text("Used before", style = MaterialTheme.typography.labelSmall)
        }
    }
}
