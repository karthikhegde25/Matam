package com.kARThik.vote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.kARThik.vote.data.*
import com.kARThik.vote.ui.*
import com.kARThik.vote.utils.fmtDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatamScaffold(
    title: String, nav: NavHostController? = null, showBack: Boolean = nav != null,
    actions: @Composable RowScope.() -> Unit = {}, fab: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null, content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (showBack && nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = fab,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (LocalBottomBarShown.current) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = { pad -> Box(Modifier.fillMaxSize().imePadding()) { content(pad) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ElectionCard(e: ElectionWithStats, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp)); StatusChip(e.status)
                trailing?.invoke()
            }
            Spacer(Modifier.height(4.dp))
            Text("${fmtDate(e.dateMillis)}  •  ${e.category}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelPill("${e.candidateCount} candidates", Color(0xFF3949AB))
                LabelPill("${e.voteCount} votes", Color(0xFF00796B))
                LabelPill(if (e.votingMode == VotingMode.BLUETOOTH.name) "Bluetooth" else "Single phone", Color(0xFF6A1B9A))
            }
        }
    }
}

@Composable
fun CandidateRow(c: ElectionCandidateEntity, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        StoredImage(c.photoFile, Modifier.size(52.dp), emoji = c.emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.partyName}  •  #${c.serial}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PartySymbol(c.partyLogoFile, c.partyAbbr, c.partyColor, 32.dp, emoji = c.partyEmoji)
        trailing?.invoke()
    }
}

@Composable
fun ErrorBanner(text: String?) {
    if (text == null) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

val Color.Companion.Success get() = Color(0xFF2E7D32)
