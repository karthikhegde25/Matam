package com.kARThik.vote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kARThik.vote.ui.container
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = error, modifier = modifier.fillMaxWidth(),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (show) "Hide password" else "Show password")
            }
        }
    )
}

/** Elections whose results were unlocked in this app session, so the password is asked only once. */
object UnlockedResults { val ids = mutableSetOf<String>() }

@Composable
fun LockedPane(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Box(modifier.fillMaxSize()) {
        BigMessage(Icons.Default.Lock, "Locked", "Enter this election's password to continue.", MaterialTheme.colorScheme.primary) {
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

/**
 * Asks for the election password. Elections created before passwords existed have none yet,
 * so the dialog lets the operator set one first (entered twice).
 */
@Composable
fun PasswordPrompt(electionId: String, title: String, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val repo = container().repo
    val scope = rememberCoroutineScope()
    var hasPw by remember { mutableStateOf<Boolean?>(null) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }
    LaunchedEffect(electionId) { hasPw = repo.hasPassword(electionId) }
    val setMode = hasPw == false

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (setMode) "Set election password" else title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasPw == null) CircularProgressIndicator() else {
                    Text(if (setMode) "This election has no password yet. Create one; it will be needed to complete the election and view its results." else "Enter the election password.")
                    PasswordField(if (setMode) "New password (min 4 characters)" else "Password", pw, { pw = it; error = null }, error = error != null)
                    if (setMode) PasswordField("Confirm password", pw2, { pw2 = it; error = null }, error = error != null)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = hasPw != null && !busy && pw.isNotEmpty(), onClick = {
                scope.launch {
                    busy = true
                    if (setMode) {
                        if (pw.length < 4) error = "At least 4 characters"
                        else if (pw != pw2) error = "Passwords do not match"
                        else if (repo.setInitialPassword(electionId, pw)) { busy = false; onSuccess(); return@launch }
                        else error = "Could not set password"
                    } else if (repo.verifyPassword(electionId, pw)) {
                        busy = false; onSuccess(); return@launch
                    } else {
                        failures++
                        delay(minOf(failures, 5) * 1000L)   // slows down guessing
                        error = "Incorrect password"; pw = ""
                    }
                    busy = false
                }
            }) { Text(if (setMode) "Set & continue" else "Unlock") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Settings: change an election's password (current password required). */
@Composable
fun ChangePasswordDialog(electionId: String, electionName: String, onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val repo = container().repo
    val scope = rememberCoroutineScope()
    var hasPw by remember { mutableStateOf<Boolean?>(null) }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(electionId) { hasPw = repo.hasPassword(electionId) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(electionName, style = MaterialTheme.typography.labelLarge)
                if (hasPw == true) PasswordField("Current password", old, { old = it; error = null })
                PasswordField("New password (min 4 characters)", new, { new = it; error = null })
                PasswordField("Confirm new password", confirm, { confirm = it; error = null }, error = confirm.isNotEmpty() && confirm != new)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = hasPw != null && !busy, onClick = {
                when {
                    new.length < 4 -> error = "New password must be at least 4 characters"
                    new != confirm -> error = "Passwords do not match"
                    else -> scope.launch {
                        busy = true
                        if (repo.changePassword(electionId, old, new)) onResult("Password changed") else error = "Current password is incorrect"
                        busy = false
                    }
                }
            }) { Text("Change") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}
