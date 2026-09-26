package com.ai.assistance.operit.ui.features.vertex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.VertexAuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun VertexLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: ((VertexAuthState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coordinator = remember { VertexOAuthCoordinator(context) }
    var session by remember { mutableStateOf<VertexOAuthLoginSession?>(null) }
    var isLaunching by remember { mutableStateOf(true) }
    var cancelRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var activeSession: VertexOAuthLoginSession? = null
        try {
            activeSession = withContext(Dispatchers.IO) { coordinator.startLogin() }
            session = activeSession
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeSession.authorizationUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            isLaunching = false
            val callbackUri = withTimeout(activeSession.expiresAt - System.currentTimeMillis()) {
                activeSession.callbackServer.awaitCallback()
            }
            val state = coordinator.completeLogin(activeSession, callbackUri)
            Toast.makeText(context, context.getString(R.string.vertex_login_success), Toast.LENGTH_LONG).show()
            onLoginSuccess?.invoke(state)
            onDismissRequest()
        } catch (error: TimeoutCancellationException) {
            showFailure(context, error)
            onDismissRequest()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!cancelRequested) {
                showFailure(context, error)
                onDismissRequest()
            }
        } finally {
            activeSession?.callbackServer?.close()
            withContext(NonCancellable) { session = null }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!cancelRequested) { cancelRequested = true; session?.callbackServer?.close(); onDismissRequest() } },
        title = { Text(stringResource(R.string.vertex_login_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(if (isLaunching) R.string.vertex_login_starting else R.string.vertex_login_waiting))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { cancelRequested = true; session?.callbackServer?.close(); onDismissRequest() }) {
                Text(stringResource(R.string.cancel_action))
            }
        },
    )
}

private fun showFailure(context: Context, error: Throwable) {
    Toast.makeText(context, context.getString(R.string.vertex_login_failed, error.message.orEmpty()), Toast.LENGTH_LONG).show()
}