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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import fr.sygix.jauge.R
import fr.sygix.jauge.auth.LoginState
import fr.sygix.jauge.model.Provider

@Composable
fun LoginDialog(
    state: LoginState,
    onOpen: (String, Provider) -> Unit,
    onManual: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onSubmitApiKey: (String) -> Unit,
    onRetry: (Provider) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        LoginState.Idle -> Unit
        is LoginState.WaitingBrowser -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.login_title, state.provider.displayName)) },
            text = {
                Column {
                    Waiting(stringResource(R.string.login_browser))
                    if (state.provider == Provider.CLAUDE) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.login_browser_fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onOpen(state.url, state.provider) }) { Text(stringResource(R.string.reopen_page)) } },
            dismissButton = {
                Row {
                    if (state.provider == Provider.CLAUDE) TextButton(onClick = onManual) { Text(stringResource(R.string.manual_code)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
        is LoginState.WaitingManualCode -> ManualCodeDialog(state, onOpen, onSubmitCode, onCancel)
        is LoginState.WaitingApiKey -> ApiKeyDialog(state, onSubmitApiKey, onCancel)
        is LoginState.RequestingDevice -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.login_title, state.provider.displayName)) },
            text = { Waiting(stringResource(R.string.requesting_code)) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        )
        is LoginState.WaitingDevice -> DeviceDialog(state, onOpen, onCancel)
        is LoginState.Finishing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.login_title, state.provider.displayName)) },
            text = { Waiting(stringResource(R.string.finishing)) },
            confirmButton = {},
        )
        is LoginState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.connected_title, state.provider.displayName)) },
            text = { Text(stringResource(R.string.connected_body)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        )
        is LoginState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.failed_title)) },
            text = { SelectionContainer { Text(Texts.failure(LocalContext.current, state.failure)) } },
            confirmButton = { TextButton(onClick = { onRetry(state.provider) }) { Text(stringResource(R.string.retry)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
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
        title = { Text(stringResource(R.string.manual_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.manual_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { clipboardText(context)?.let { code = it.trim() } }) { Text(stringResource(R.string.paste)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.isNotBlank()) { Text(stringResource(R.string.validate)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onOpen(state.url, state.provider) }) { Text(stringResource(R.string.reopen)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun DeviceDialog(state: LoginState.WaitingDevice, onOpen: (String, Provider) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.login_title, state.provider.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(when (state.provider) {
                        Provider.CODEX -> R.string.device_codex
                        Provider.OPENCODE -> R.string.device_opencode
                        else -> R.string.device_nous
                    }),
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
                Waiting(stringResource(R.string.device_waiting))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copy(context, state.code.userCode)
                onOpen(state.code.verificationUrl, state.provider)
            }) { Text(stringResource(R.string.copy_and_open)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun clipboardText(context: Context): String? = runCatching {
    context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", text))
}

@Composable
private fun ApiKeyDialog(state: LoginState.WaitingApiKey, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var key by remember(state.provider) { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.api_key_title, state.provider.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.api_key_body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (state.provider == Provider.OPENCODE) R.string.api_key_opencode_body else R.string.api_key_openrouter_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true,
                    enabled = !state.validating,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.validating) Waiting(stringResource(R.string.finishing))
                state.failure?.let { Text(Texts.failure(context, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(key) }, enabled = !state.validating && key.isNotBlank() && key.trim().none { it.isWhitespace() || it.isISOControl() }) { Text(stringResource(R.string.connect)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
