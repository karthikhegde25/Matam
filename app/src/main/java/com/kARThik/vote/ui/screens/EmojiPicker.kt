package com.kARThik.vote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kARThik.vote.utils.Emojis

@Composable
fun EmojiPickerDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val cats = Emojis.categories
    var tab by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an emoji") },
        text = {
            Column {
                ScrollableTabRow(tab, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surface) {
                    cats.forEachIndexed { i, c -> Tab(tab == i, { tab = i }, text = { Text(c.first, fontSize = 20.sp) }) }
                }
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxWidth().height(250.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(cats[tab.coerceIn(0, cats.lastIndex)].second) { e ->
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onPick(e) },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 26.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(custom, { custom = it }, Modifier.weight(1f), singleLine = true, label = { Text("Type or paste any emoji") })
                    TextButton(enabled = Emojis.clean(custom) != null, onClick = { onPick(Emojis.clean(custom)) }) { Text("Use") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (current != null) TextButton(onClick = { onPick(null) }) { Text("Remove emoji") } }
    )
}

@Composable
fun EmojiField(label: String, emoji: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Text(emoji ?: "😀", fontSize = 20.sp); Spacer(Modifier.width(8.dp)); Text(if (emoji == null) label else "Change emoji", maxLines = 1)
        }
        if (emoji != null) TextButton(onClick = { onChange(null) }) { Text("Remove") }
    }
    if (open) EmojiPickerDialog(emoji, { onChange(it); open = false }, { open = false })
}
