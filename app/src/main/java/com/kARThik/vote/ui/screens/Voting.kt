package com.kARThik.vote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kARThik.vote.data.ElectionCandidateEntity
import com.kARThik.vote.ui.*

@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Voter-facing ballot. Card height is computed from the screen height and candidate count so that
 * every candidate is visible without scrolling; only when cards would get too small does the list scroll.
 */
@Composable
fun Ballot(candidates: List<ElectionCandidateEntity>, onSelect: (ElectionCandidateEntity) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) { LandscapeCards(candidates, onSelect, maxWidth, maxHeight); return@BoxWithConstraints }
        val n = candidates.size.coerceAtLeast(1)
        val gap = if (n > 6) 6.dp else if (n > 4) 8.dp else 12.dp
        val vPad = 8.dp
        val h = ((maxHeight - vPad * 2 - gap * (n - 1)) / n).coerceIn(68.dp, 150.dp)
        val compact = h < 100.dp
        val photo = (h - 16.dp).coerceIn(48.dp, 120.dp)
        val nameSp = (h.value * 0.17f).coerceIn(14f, 22f)
        val symbol = (h * 0.5f).coerceIn(30.dp, 64.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, vPad), verticalArrangement = Arrangement.spacedBy(gap)) {
            items(candidates, key = { it.candidateId }) { c ->
                val color = Color(c.partyColor.toInt())
                Card(
                    onClick = { onSelect(c) }, shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
                    border = BorderStroke(if (compact) 3.dp else 4.dp, color),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(3.dp), modifier = Modifier.fillMaxWidth().height(h)
                ) {
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = color) {
                            Text(c.serial.ifBlank { "•" }, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (nameSp - 3f).coerceAtLeast(11f).sp, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        StoredImage(c.photoFile, Modifier.size(photo), RoundedCornerShape(if (compact) 12.dp else 18.dp), emoji = c.emoji)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = nameSp.sp, fontWeight = FontWeight.Bold, lineHeight = (nameSp + 3f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!compact && c.designation.isNotBlank()) Text(c.designation, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(c.partyName, fontSize = (nameSp - 4f).coerceAtLeast(11f).sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PartySymbol(c.partyLogoFile, c.partyAbbr, c.partyColor, symbol, emoji = c.partyEmoji)
                    }
                }
            }
        }
    }
}

/** Landscape: modern cards laid out across the screen in a grid sized so all candidates fit. */
@Composable
private fun LandscapeCards(candidates: List<ElectionCandidateEntity>, onSelect: (ElectionCandidateEntity) -> Unit, maxW: Dp, maxH: Dp) {
    val n = candidates.size.coerceAtLeast(1)
    val cols = if (n <= 3) n else maxOf(kotlin.math.ceil(n / 2.0).toInt(), 3).coerceAtMost(6)
    val rows = kotlin.math.ceil(n / cols.toDouble()).toInt()
    val gap = 10.dp
    val pad = 12.dp
    val cardW = (maxW - pad * 2 - gap * (cols - 1)) / cols
    val cardH = ((maxH - pad * 2 - gap * (rows - 1)) / rows).coerceIn(124.dp, 240.dp)
    val photo = minOf(cardW - 16.dp, cardH - 112.dp).coerceIn(40.dp, 150.dp)
    val nameSp = (cardW.value * 0.11f).coerceIn(12f, 19f)
    LazyVerticalGrid(
        GridCells.Fixed(cols), Modifier.fillMaxSize(), contentPadding = PaddingValues(pad),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        gridItems(candidates, key = { it.candidateId }) { c ->
            val color = Color(c.partyColor.toInt())
            Card(
                onClick = { onSelect(c) }, shape = RoundedCornerShape(18.dp), border = BorderStroke(3.dp, color),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp), modifier = Modifier.fillMaxWidth().height(cardH)
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = color) {
                            Text(c.serial.ifBlank { "•" }, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        PartySymbol(c.partyLogoFile, c.partyAbbr, c.partyColor, 28.dp, emoji = c.partyEmoji)
                    }
                    Spacer(Modifier.height(4.dp))
                    StoredImage(c.photoFile, Modifier.size(photo), RoundedCornerShape(14.dp), emoji = c.emoji)
                    Spacer(Modifier.height(4.dp))
                    Text(c.name, fontSize = nameSp.sp, lineHeight = (nameSp + 2f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(c.partyName, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun BallotView(style: String?, candidates: List<ElectionCandidateEntity>, onSelect: (ElectionCandidateEntity) -> Unit, modifier: Modifier = Modifier) {
    if (style == com.kARThik.vote.data.BallotStyle.EVM.name) EvmBallot(candidates, onSelect, modifier) else Ballot(candidates, onSelect, modifier)
}

@Composable
private fun EvmRow(c: ElectionCandidateEntity, index: Int, rowH: Dp, nameSp: Float, onSelect: (ElectionCandidateEntity) -> Unit) {
    val ink = Color(0xFF14171C)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(Modifier.fillMaxWidth().height(rowH).background(Color(0xFFF5F2EA)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).border(1.5.dp, ink, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                Text(c.serial.ifBlank { "${index + 1}" }, color = ink, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(5.dp))
            StoredImage(c.photoFile, Modifier.size(30.dp), RoundedCornerShape(6.dp), emoji = c.emoji)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name.uppercase(), color = ink, fontWeight = FontWeight.Bold, fontSize = nameSp.sp, lineHeight = (nameSp + 2f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(c.partyName, color = Color(0xFF4A5058), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(5.dp))
            PartySymbol(c.partyLogoFile, c.partyAbbr, c.partyColor, 28.dp, emoji = c.partyEmoji)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(11.dp).clip(CircleShape).background(if (pressed) Color(0xFFFF2A2A) else Color(0xFF5A1C1C)).border(1.dp, Color(0xFF2B0B0B), CircleShape))
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = { onSelect(c) }, interactionSource = interaction, shape = RoundedCornerShape(50),
                color = if (pressed) Color(0xFF0B2A8A) else Color(0xFF2154E0), shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0B2370)),
                modifier = Modifier.width(46.dp).height(26.dp)
            ) {}
        }
        HorizontalDivider(color = Color(0xFF8A9099), thickness = 1.dp)
    }
}

/** EVM ballot unit. Portrait: one column. Landscape: two columns so every candidate fits on screen. */
@Composable
fun EvmBallot(candidates: List<ElectionCandidateEntity>, onSelect: (ElectionCandidateEntity) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().padding(10.dp)) {
        val landscape = maxWidth > maxHeight
        val n = candidates.size.coerceAtLeast(1)
        val header = 0.dp
        val perColumn = if (landscape) kotlin.math.ceil(n / 2.0).toInt().coerceAtLeast(1) else n
        val rowH = ((maxHeight - header) / perColumn).coerceIn(46.dp, 62.dp)
        val nameSp = (rowH.value * 0.30f).coerceIn(13f, 17f)
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(RoundedCornerShape(14.dp)).background(Color(0xFFCFD2D8))
                .border(2.dp, Color(0xFF8A9099), RoundedCornerShape(14.dp))
        ) {
            if (!landscape) {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    itemsIndexed(candidates, key = { _, c -> c.candidateId }) { i, c -> EvmRow(c, i, rowH, nameSp, onSelect) }
                }
            } else {
                Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { candidates.take(perColumn).forEachIndexed { i, c -> EvmRow(c, i, rowH, nameSp, onSelect) } }
                        Box(Modifier.width(3.dp).height(rowH * perColumn).background(Color(0xFF8A9099)))
                        Column(Modifier.weight(1f)) { candidates.drop(perColumn).forEachIndexed { i, c -> EvmRow(c, i + perColumn, rowH, nameSp, onSelect) } }
                    }
                }
            }
        }
    }
}

@Composable
fun BallotStylePreview(style: com.kARThik.vote.data.BallotStyle) {
    if (style == com.kARThik.vote.data.BallotStyle.EVM) {
        Column(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFCFD2D8)).padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Row(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF5F2EA)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).background(Color(0xFF9AA0A6)))
                    Spacer(Modifier.width(4.dp)); Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF6E6E6E)))
                    Spacer(Modifier.width(4.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFB02A2A)))
                    Spacer(Modifier.width(4.dp)); Box(Modifier.width(20.dp).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2154E0)))
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().height(96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE3E6F0)).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF8FA0D6)))
                    Spacer(Modifier.width(6.dp)); Box(Modifier.weight(1f).height(6.dp).background(Color(0xFF9AA0A6)))
                    Spacer(Modifier.width(6.dp)); Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF1F3A93)))
                }
            }
        }
    }
}

@Composable
fun ConfirmVote(c: ElectionCandidateEntity, onConfirm: () -> Unit, onBack: () -> Unit, busy: Boolean = false) {
    val color = Color(c.partyColor.toInt())
    @Composable
    fun ChoiceCard(photo: Dp) {
        Card(shape = RoundedCornerShape(28.dp), border = BorderStroke(5.dp, color)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                StoredImage(c.photoFile, Modifier.size(photo), RoundedCornerShape(24.dp), emoji = c.emoji)
                Spacer(Modifier.height(10.dp))
                Text(c.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PartySymbol(c.partyLogoFile, c.partyAbbr, c.partyColor, 40.dp, emoji = c.partyEmoji); Spacer(Modifier.width(10.dp))
                    Column { Text(c.partyName, fontWeight = FontWeight.SemiBold); Text("Serial ${c.serial.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    @Composable
    fun Buttons() {
        Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("CONFIRM VOTE", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text("Go back") }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availH = maxHeight
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ChoiceCard((availH - 150.dp).coerceIn(70.dp, 170.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Confirm your choice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Buttons()
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Confirm your choice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    ChoiceCard(180.dp)
                    Spacer(Modifier.height(28.dp))
                    Buttons()
                }
            }
        }
    }
}

/** Landscape toggle for the voting screens. Portrait is restored automatically when the screen is left. */
@Composable
fun rememberLandscapeMode(): MutableState<Boolean> {
    val activity = LocalContext.current.findActivity()
    val landscape = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(landscape.value) {
        activity?.requestedOrientation =
            if (landscape.value) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } }
    return landscape
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

@Composable
fun RotateButton(landscape: MutableState<Boolean>, modifier: Modifier = Modifier) {
    IconButton(onClick = { landscape.value = !landscape.value }, modifier = modifier) {
        Icon(Icons.Default.ScreenRotation, if (landscape.value) "Switch to portrait" else "Switch to landscape")
    }
}

@Composable
fun BigMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, color: Color, content: @Composable ColumnScope.() -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: message on the left, actions on the right, so nothing needs scrolling.
            Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(52.dp), tint = color)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    content()
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(130.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(80.dp), tint = color)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun VotingScreen(nav: NavHostController, id: String) {
    val appCtx = LocalContext.current.applicationContext
    val vm = appVm(key = "vote-$id") { VotingVm(it, id, appCtx) }
    val phase by vm.phase.collectAsState()
    val election by vm.election.collectAsState()
    val cands by vm.candidates.collectAsState()
    val selected by vm.selected.collectAsState()
    val error by vm.error.collectAsState()
    var exit by remember { mutableStateOf(false) }
    var gateResume by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    val landscape = rememberLandscapeMode()
    KeepScreenOn()
    BackHandler { if (phase == VotePhase.BALLOT || phase == VotePhase.CONFIRM) vm.cancelSession() else exit = true }
    LaunchedEffect(error) { error?.let { snack.showSnackbar(it); vm.clearError() } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (phase) {
                VotePhase.UNAVAILABLE -> BigMessage(Icons.Default.Block, "Voting unavailable", error ?: "This election is not open for voting.", MaterialTheme.colorScheme.error) {
                    Button(onClick = { nav.popBackStack() }) { Text("Back") }
                }
                VotePhase.READY -> BigMessage(Icons.Default.HowToVote, election?.name ?: "", "Ready for the next voter.\nThe operator starts each voting session.", MaterialTheme.colorScheme.primary) {
                    Button(onClick = { vm.beginSession() }, Modifier.fillMaxWidth().heightIn(min = 70.dp), shape = RoundedCornerShape(22.dp)) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Begin voting session", fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.pause() }) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("Pause") }
                        OutlinedButton(onClick = { exit = true }) { Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(4.dp)); Text("Exit") }
                    }
                    if (!election?.instructions.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp)); Text(election!!.instructions, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
                VotePhase.PAUSED -> BigMessage(Icons.Default.PauseCircle, "Voting paused", "The operator has paused this session.", Color(0xFFEF6C00)) {
                    Button(onClick = { gateResume = true }, Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("Resume voting") }
                    Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { nav.popBackStack() }) { Text("Exit") }
                }
                VotePhase.BALLOT -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Select your candidate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Tap a candidate, then confirm.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.cancelSession() }) { Text("Cancel") }
                    }
                    BallotView(election?.ballotStyle, cands, { vm.select(it) }, Modifier.weight(1f))
                }
                VotePhase.CONFIRM -> selected?.let { ConfirmVote(it, { vm.confirm() }, { vm.back() }) }
                VotePhase.SUCCESS -> BigMessage(Icons.Default.CheckCircle, "Vote recorded", "Thank you. Please step away.", Color(0xFF2E7D32))
            }
            RotateButton(landscape, Modifier.align(Alignment.TopEnd))
        }
    }
    if (gateResume) PasswordPrompt(id, "Resume voting", onSuccess = { gateResume = false; vm.resume() }, onDismiss = { gateResume = false })
    if (exit) ConfirmDialog("Leave voting screen?", "Voting will be paused. Votes already recorded are saved. Continuing later needs the election password.", "Pause & leave",
        onConfirm = { vm.leave { nav.popBackStack() } }, onDismiss = { exit = false })
}
