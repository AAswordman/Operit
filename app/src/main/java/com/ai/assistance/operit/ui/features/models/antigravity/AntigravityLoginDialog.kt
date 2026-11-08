package com.ai.assistance.operit.ui.features.models.antigravity

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
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
import com.ai.assistance.operit.data.preferences.AntigravityAuthState
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun AntigravityLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: ((AntigravityAuthState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coordinator = remember { AntigravityOAuthCoordinator(context) }
    var session by remember { mutableStateOf<AntigravityOAuthLoginSession?>(null) }
    var isLaunching by remember { mutableStateOf(true) }
    var isCompleting by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var activeSession: AntigravityOAuthLoginSession? = null
        var stage = "starting"
        try {
            val loginSession = withContext(Dispatchers.IO) {
                // 创建阶段若发生取消，finally 仍需拿到并关闭刚打开的监听器。
                coordinator.startLogin().also { activeSession = it }
            }
            session = loginSession
            AppLogger.d(TAG, "Antigravity OAuth callback server ready; opening browser")
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(loginSession.authorizationUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            isLaunching = false
            val remainingMillis = loginSession.expiresAt - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                throw IllegalStateException("Antigravity OAuth session expired before browser launch")
            }
            stage = "waiting_for_callback"
            AppLogger.d(TAG, "Waiting up to 15 minutes for browser authorization")
            val callbackUrl = withTimeout(remainingMillis) {
                loginSession.callbackServer.awaitCallback(loginSession.state)
            }
            isCompleting = true
            stage = "exchanging_token_and_loading_account"
            val state = coordinator.completeLogin(loginSession, Uri.parse(callbackUrl))
            OKToast.success(
                context,
                context.getString(R.string.antigravity_login_success),
                OKToast.LENGTH_LONG,
            ).show()
            onLoginSuccess?.invoke(state)
            onDismissRequest()
        } catch (error: TimeoutCancellationException) {
            AppLogger.e(TAG, "Antigravity OAuth login timed out", error)
            OKToast.error(context, R.string.antigravity_login_timed_out, OKToast.LENGTH_LONG).show()
            onDismissRequest()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!cancelRequested) {
                AppLogger.e(TAG, "Antigravity OAuth login failed at stage=$stage", error)
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
        title = { Text(stringResource(R.string.antigravity_login_title)) },
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
                                R.string.antigravity_login_starting
                            } else if (isCompleting) {
                                R.string.antigravity_login_completing
                            } else {
                                R.string.antigravity_login_waiting
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
    OKToast.error(
        context,
        context.getString(R.string.antigravity_login_failed, error.message.orEmpty()),
        OKToast.LENGTH_LONG,
    ).show()
}

private const val TAG = "AntigravityLoginDialog"
