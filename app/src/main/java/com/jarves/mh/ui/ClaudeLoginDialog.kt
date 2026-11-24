package com.jarves.mh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.jarves.mh.R
import com.jarves.mh.runtime.ClaudeLoginMode
import com.jarves.mh.runtime.ClaudeLoginState

/** Janela do /login: escolhe a forma de entrar, mostra a URL e recebe o código de volta. */
@Composable
fun ClaudeLoginDialog(
    state: ClaudeLoginState,
    onStart: (ClaudeLoginMode) -> Unit,
    onSubmitCode: (String) -> Unit,
    onClose: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.login_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    ClaudeLoginState.Idle -> LoginOptions(onStart)
                    ClaudeLoginState.Starting -> Progress(stringResource(R.string.login_starting))
                    ClaudeLoginState.Verifying -> Progress(stringResource(R.string.login_verifying))
                    is ClaudeLoginState.WaitingForCode -> {
                        Text(stringResource(R.string.login_open_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { runCatching { uriHandler.openUri(state.url) } }) {
                                Text(stringResource(R.string.login_open))
                            }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(state.url)) }) {
                                Text(stringResource(R.string.login_copy))
                            }
                        }
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(stringResource(R.string.login_code_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ClaudeLoginState.Success -> Text(stringResource(R.string.login_success))
                    is ClaudeLoginState.Failed -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        LoginOptions(onStart)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ClaudeLoginState.WaitingForCode ->
                    Button(onClick = { onSubmitCode(code) }, enabled = code.isNotBlank()) {
                        Text(stringResource(R.string.login_submit))
                    }
                ClaudeLoginState.Success -> TextButton(onClick = onClose) { Text(stringResource(R.string.login_done)) }
                else -> Unit
            }
        },
        dismissButton = {
            if (state != ClaudeLoginState.Success) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.login_cancel)) }
            }
        },
    )
}

@Composable
private fun LoginOptions(onStart: (ClaudeLoginMode) -> Unit) {
    OutlinedButton(onClick = { onStart(ClaudeLoginMode.SUBSCRIPTION) }, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_subscription))
    }
    OutlinedButton(onClick = { onStart(ClaudeLoginMode.CONSOLE) }, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_console))
    }
    OutlinedButton(onClick = { onStart(ClaudeLoginMode.SSO) }, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_sso))
    }
}

@Composable
private fun Progress(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier, strokeWidth = 2.dp)
        Text(text)
    }
}
