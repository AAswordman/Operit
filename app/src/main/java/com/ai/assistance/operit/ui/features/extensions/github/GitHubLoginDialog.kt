package com.ai.assistance.operit.ui.features.extensions.github

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubOAuthBrokerStartResponse
import com.ai.assistance.operit.ui.common.browser.BrowserCallbackDialog
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private enum class GitHubLoginMode {
    CHOOSER,
    EMBEDDED,
    EXTERNAL
}

@Composable
fun GitHubLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (() -> Unit)? = null
) {
    var loginMode by rememberSaveable { mutableStateOf(GitHubLoginMode.CHOOSER) }

    when (loginMode) {
        GitHubLoginMode.CHOOSER -> GitHubLoginMethodDialog(
            onDismissRequest = onDismissRequest,
            onUseEmbeddedLogin = { loginMode = GitHubLoginMode.EMBEDDED },
            onUseExternalLogin = { loginMode = GitHubLoginMode.EXTERNAL }
        )
        GitHubLoginMode.EMBEDDED -> GitHubEmbeddedLoginDialog(
            onDismissRequest = onDismissRequest,
            onLoginSuccess = onLoginSuccess
        )
        GitHubLoginMode.EXTERNAL -> GitHubExternalLoginDialog(
            onDismissRequest = onDismissRequest,
            onLoginSuccess = onLoginSuccess
        )
    }
}

@Composable
private fun GitHubLoginMethodDialog(
    onDismissRequest: () -> Unit,
    onUseEmbeddedLogin: () -> Unit,
    onUseExternalLogin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.login_github)) },
        text = {
            Column {
                Text(stringResource(R.string.github_login_method_description))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onUseEmbeddedLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.github_login_method_embedded))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUseExternalLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.github_login_method_external))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun GitHubEmbeddedLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coordinator = remember { GitHubOAuthCoordinator(context) }
    val scope = rememberCoroutineScope()
    var transaction by remember { mutableStateOf<GitHubOAuthBrokerStartResponse?>(null) }

    LaunchedEffect(Unit) {
        coordinator.startEmbeddedLogin().fold(
            onSuccess = { started -> transaction = started },
            onFailure = { error ->
                AppLogger.e(TAG, "Failed to start embedded GitHub login", error)
                OKToast.error(
                    context,
                    context.getString(R.string.main_github_login_failed, error.message.orEmpty()),
                    OKToast.LENGTH_LONG
                ).show()
                onDismissRequest()
            }
        )
    }

    val activeTransaction = transaction
    if (activeTransaction == null) {
        AlertDialog(
            onDismissRequest = {
                scope.launch { coordinator.cancelLogin() }
                onDismissRequest()
            },
            title = { Text(stringResource(R.string.login_github)) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Column {
                        Text(stringResource(R.string.github_login_starting))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { coordinator.cancelLogin() }
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        return
    }

    BrowserCallbackDialog(
        title = stringResource(R.string.login_github),
        authorizationUrl = activeTransaction.authorizationUrl,
        completionRedirectUri = Uri.parse(activeTransaction.completionRedirectUri),
        expiresAt = activeTransaction.expiresAt,
        onCompletion = { completionUri ->
            scope.launch {
                coordinator.completeLogin(completionUri).fold(
                    onSuccess = { user ->
                        OKToast.success(
                            context,
                            context.getString(R.string.main_github_login_success, user.login),
                            OKToast.LENGTH_LONG
                        ).show()
                        onLoginSuccess?.invoke()
                        onDismissRequest()
                    },
                    onFailure = { error ->
                        AppLogger.e(TAG, "Failed to complete embedded GitHub login", error)
                        OKToast.error(
                            context,
                            context.getString(R.string.main_github_login_failed, error.message.orEmpty()),
                            OKToast.LENGTH_LONG
                        ).show()
                        onDismissRequest()
                    }
                )
            }
        },
        onCancelled = {
            scope.launch { coordinator.cancelLogin() }
            onDismissRequest()
        },
        onFailure = { error ->
            scope.launch { coordinator.cancelLogin() }
            AppLogger.e(TAG, "Embedded GitHub browser callback failed", error)
            OKToast.error(
                context,
                context.getString(R.string.main_github_login_failed, error.message.orEmpty()),
                OKToast.LENGTH_LONG
            ).show()
            onDismissRequest()
        }
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
private fun GitHubExternalLoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coordinator = remember { GitHubOAuthCoordinator(context) }
    var callbackServer by remember { mutableStateOf<GitHubOAuthLoopbackCallbackServer?>(null) }
    var isLaunching by remember { mutableStateOf(true) }
    var isCompleting by remember { mutableStateOf(false) }
    var isCancelRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var loopbackServer: GitHubOAuthLoopbackCallbackServer? = null
        try {
            loopbackServer = withContext(Dispatchers.IO) {
                GitHubOAuthLoopbackCallbackServer.open()
            }
            callbackServer = loopbackServer
            val transaction = coordinator.startLogin(loopbackServer.completionRedirectUri).getOrElse { error ->
                throw error
            }
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(transaction.authorizationUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            isLaunching = false

            val remainingMillis = transaction.expiresAt - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                throw IllegalStateException("GitHub login transaction expired before the browser opened")
            }
            val completionUri = withTimeout(remainingMillis) {
                loopbackServer.awaitCompletion()
            }
            isCompleting = true
            val user = coordinator.completeLogin(completionUri).getOrElse { error -> throw error }
            OKToast.success(
                context,
                context.getString(R.string.main_github_login_success, user.login),
                OKToast.LENGTH_LONG
            ).show()
            onLoginSuccess?.invoke()
            onDismissRequest()
        } catch (error: TimeoutCancellationException) {
            AppLogger.e(TAG, "External GitHub login timed out", error)
            OKToast.error(
                context,
                context.getString(R.string.main_github_login_failed, error.message.orEmpty()),
                OKToast.LENGTH_LONG
            ).show()
            onDismissRequest()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCancelRequested) {
                AppLogger.d(TAG, "External GitHub login cancelled by user")
                return@LaunchedEffect
            }
            AppLogger.e(TAG, "External GitHub login failed", error)
            OKToast.error(
                context,
                context.getString(R.string.main_github_login_failed, error.message.orEmpty()),
                OKToast.LENGTH_LONG
            ).show()
            onDismissRequest()
        } finally {
            loopbackServer?.close()
            withContext(NonCancellable) {
                coordinator.cancelLogin()
            }
        }
    }

    val cancelExternalLogin = {
        isCancelRequested = true
        callbackServer?.close()
        onDismissRequest()
    }
    AlertDialog(
        onDismissRequest = {
            if (!isCompleting) {
                cancelExternalLogin()
            }
        },
        title = { Text(stringResource(R.string.login_github)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    stringResource(
                        if (isLaunching) {
                            R.string.github_login_starting
                        } else {
                            R.string.github_login_external_waiting
                        }
                    )
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = cancelExternalLogin,
                enabled = !isCompleting
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private const val TAG = "GitHubLoginDialog"
