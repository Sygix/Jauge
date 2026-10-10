/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.jauge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fr.sygix.jauge.auth.LoginState
import fr.sygix.jauge.model.Provider

@Composable
fun LoginDialog(
    state: LoginState,
    onOpen: (String, Provider) -> Unit,
    onManual: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onRetry: (Provider) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        LoginState.Idle -> Unit
        is LoginState.WaitingBrowser -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Connexion à ${state.provider.displayName}") },
            text = {
                Column {
                    Waiting("Autorisez Jauge dans le navigateur, l'application récupère la connexion automatiquement.")
                    if (state.provider == Provider.CLAUDE) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Si le retour automatique échoue, utilisez la saisie manuelle du code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onOpen(state.url, state.provider) }) { Text("Rouvrir la page") } },
            dismissButton = {
                Row {
                    if (state.provider == Provider.CLAUDE) TextButton(onClick = onManual) { Text("Code manuel") }
                    TextButton(onClick = onCancel) { Text("Annuler") }
                }
            },
        )
        is LoginState.WaitingManualCode -> ManualCodeDialog(state, onOpen, onSubmitCode, onCancel)
        is LoginState.RequestingDevice -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Connexion à ${state.provider.displayName}") },
            text = { Waiting("Demande d'un code de connexion…") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
        )
        is LoginState.WaitingDevice -> DeviceDialog(state, onOpen, onCancel)
        is LoginState.Finishing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Connexion à ${state.provider.displayName}") },
            text = { Waiting("Finalisation et première lecture de l'usage…") },
            confirmButton = {},
        )
        is LoginState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${state.provider.displayName} connecté") },
            text = { Text("Les widgets se mettent à jour automatiquement.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Parfait") } },
        )
        is LoginState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Connexion impossible") },
            text = { SelectionContainer { Text(state.message) } },
            confirmButton = { TextButton(onClick = { onRetry(state.provider) }) { Text("Réessayer") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        )
    }
}

@Composable
private fun Waiting(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.width(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ManualCodeDialog(
    state: LoginState.WaitingManualCode,
    onOpen: (String, Provider) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clip = clipboardText(context)
            if (code.isBlank() && clip != null && Regex("""^[\w-]{10,}#[\w-]{10,}$""").matches(clip.trim())) code = clip.trim()
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Code d'autorisation Claude") },
        text = {
            Column {
                Text(
                    "Autorisez Jauge sur claude.ai, copiez le code affiché puis collez-le ici.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { clipboardText(context)?.let { code = it.trim() } }) { Text("Coller") }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text("Valider") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onOpen(state.url, state.provider) }) { Text("Rouvrir") }
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
        },
    )
}

@Composable
private fun DeviceDialog(state: LoginState.WaitingDevice, onOpen: (String, Provider) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Connexion à ${state.provider.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (state.provider == Provider.CODEX) {
                        "Ouvrez la page OpenAI, saisissez ce code et validez. Si la page le demande, activez la connexion par code appareil dans les paramètres de sécurité ChatGPT."
                    } else {
                        "Ouvrez la page Nous Portal et validez ce code."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            state.code.userCode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            letterSpacing = 3.sp,
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                        )
                    }
                }
                Waiting("En attente de validation…")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copy(context, state.code.userCode)
                onOpen(state.code.verificationUrl, state.provider)
            }) { Text("Copier et ouvrir") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
    )
}

private fun clipboardText(context: Context): String? = runCatching {
    context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", text))
}
