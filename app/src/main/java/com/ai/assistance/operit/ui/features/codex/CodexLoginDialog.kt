package com.ai.assistance.operit.ui.features.codex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.CodexAuthState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun CodexLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: ((CodexAuthState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { CodexOAuthCoordinator(context) }
    var session by remember { mutableStateOf<CodexOAuthLoginSession?>(null) }
    var isLaunching by remember { mutableStateOf(true) }
    var isCompleting by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var activeSession: CodexOAuthLoginSession? = null
        try {
            activeSession = withContext(Dispatchers.IO) { coordinator.startLogin() }
            session = activeSession
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(activeSession.authorizationUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            isLaunching = false
            val remainingMillis = activeSession.expiresAt - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                throw IllegalStateException("Codex OAuth session expired before browser launch")
            }
            val callbackUri = withTimeout(remainingMillis) {
                activeSession.callbackServer.awaitCallback()
            }
            isCompleting = true
            val state = coordinator.completeLogin(activeSession, callbackUri)
            Toast.makeText(
                context,
                context.getString(R.string.codex_login_success),
                Toast.LENGTH_LONG,
            ).show()
            onLoginSuccess?.invoke(state)
            onDismissRequest()
        } catch (error: TimeoutCancellationException) {
            AppLogger.e(TAG, "Codex OAuth login timed out", error)
            showLoginFailure(context, error)
            onDismissRequest()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!cancelRequested) {
                AppLogger.e(TAG, "Codex OAuth login failed", error)
                showLoginFailure(context, error)
                onDismissRequest()
            }
        } finally {
            activeSession?.callbackServer?.close()
            withContext(NonCancellable) {
                session = null
            }
        }
    }

    val cancelLogin = {
        cancelRequested = true
        session?.callbackServer?.close()
        onDismissRequest()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isCompleting) cancelLogin()
        },
        title = { Text(stringResource(R.string.codex_login_title)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        stringResource(
                            if (isLaunching) {
                                R.string.codex_login_starting
                            } else {
                                R.string.codex_login_waiting
                            },
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = cancelLogin) {
                Text(stringResource(R.string.cancel_action))
            }
        },
    )
}

private fun showLoginFailure(context: Context, error: Throwable) {
    Toast.makeText(
        context,
        context.getString(R.string.codex_login_failed, error.message.orEmpty()),
        Toast.LENGTH_LONG,
    ).show()
}

private const val TAG = "CodexLoginDialog"
