package com.kARThik.vote.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kARThik.vote.data.ElectionStatus
import com.kARThik.vote.utils.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bitmapCache = object : LruCache<String, ImageBitmap>(48) {}

@Composable
fun rememberStoredImage(name: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = name?.let { bitmapCache.get(it) }, key1 = name) {
        if (name == null) { value = null; return@produceState }
        bitmapCache.get(name)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            ImageStore.file(ctx, name)?.let { f ->
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()?.also { bitmapCache.put(name, it) }
            }
        }
    }
    return state.value
}

@Composable
fun StoredImage(
    name: String?, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person, scale: ContentScale = ContentScale.Crop,
    emoji: String? = null
) {
    val img = rememberStoredImage(name)
    BoxWithConstraints(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val dim = minOf(maxWidth, maxHeight)
        when {
            img != null -> {
                Image(img, null, Modifier.fillMaxSize(), contentScale = scale)
                if (emoji != null) Box(
                    Modifier.align(Alignment.BottomEnd).size(dim * 0.42f).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = (dim.value * 0.26f).coerceAtLeast(8f).sp, maxLines = 1) }
            }
            emoji != null -> Text(emoji, fontSize = (dim.value * 0.55f).coerceAtLeast(10f).sp, maxLines = 1)
            else -> Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

/** Photo picker button area: copies chosen image into app storage and reports the stored filename. */
@Composable
fun rememberImagePicker(onPicked: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { ImageStore.import(ctx, uri)?.let(onPicked) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
fun ImageField(label: String, file: String?, onPick: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val pick = rememberImagePicker(onPick)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            StoredImage(file, Modifier.size(size).clickable { pick() })
            if (file != null) {
                IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp))
                }
            }
        }
        TextButton(onClick = pick) { Text(if (file == null) "Add $label" else "Change", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "DRAFT" -> Color(0xFF546E7A)
    "READY" -> Color(0xFF1565C0)
    "ONGOING" -> Color(0xFF2E7D32)
    "PAUSED" -> Color(0xFFC25100)
    "COMPLETED" -> Color(0xFF6A1B9A)
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun StatusChip(status: String) {
    val c = statusColor(status)
    val label = runCatching { ElectionStatus.valueOf(status).label }.getOrDefault(status)
    LabelPill(label, c)
}

@Composable
fun PartySymbol(logo: String?, abbr: String, color: Long, size: Dp = 36.dp, emoji: String? = null) {
    val c = Color(color.toInt())
    if (logo != null || emoji != null) {
        StoredImage(logo, Modifier.size(size).border(2.dp, c, CircleShape), CircleShape, Icons.Default.Flag, emoji = emoji)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
            Text(abbr.take(3).ifBlank { "IND" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirmLabel: String = "Confirm", destructive: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear") } },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun <T> FilterChipsRow(options: List<T>, selected: T?, label: (T) -> String, allLabel: String = "All", onSelect: (T?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        item { FilterChip(selected == null, { onSelect(null) }, { Text(allLabel) }) }
        items(options) { o -> FilterChip(selected == o, { onSelect(if (selected == o) null else o) }, { Text(label(o)) }) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun LabeledText(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

val PartyColors = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF1E88E5, 0xFF00ACC1,
    0xFF00897B, 0xFF43A047, 0xFFF9A825, 0xFFFB8C00, 0xFF6D4C41, 0xFF546E7A
)

@Composable
fun ColorPickerRow(selected: Long, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(PartyColors) { c ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c.toInt()))
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Swipe left = next, swipe right = previous. Does not interfere with vertical scrolling. */
@Composable
fun Modifier.swipeTabs(onNext: () -> Unit, onPrev: () -> Unit): Modifier {
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrev)
    return pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -140f) next() else if (total > 140f) prev(); total = 0f },
            onDragCancel = { total = 0f }
        ) { _, dx -> total += dx }
    }
}

val LocalBottomBarShown = androidx.compose.runtime.compositionLocalOf { false }

/** Text field whose keyboard "Next" key moves to the following field. */
@Composable
fun FormField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, singleLine: Boolean = false, minLines: Int = 1,
    readOnly: Boolean = false, trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value, onValueChange, modifier = modifier, label = label, singleLine = singleLine,
        minLines = minLines, readOnly = readOnly, trailingIcon = trailingIcon,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) androidx.compose.ui.text.input.ImeAction.Next else androidx.compose.ui.text.input.ImeAction.Default
        )
    )
}

/** Solid coloured label with white text. */
@Composable
fun LabelPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, maxLines = 1,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Abbreviation shown on a party symbol: its own, else initials of its name; "IND" only for independents. */
fun partyAbbr(p: com.kARThik.vote.data.PartyEntity?): String =
    if (p == null) "IND" else p.abbreviation.ifBlank { com.kARThik.vote.utils.initialsOf(p.name) }
