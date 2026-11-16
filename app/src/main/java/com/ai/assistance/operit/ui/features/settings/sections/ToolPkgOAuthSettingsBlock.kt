package com.ai.assistance.operit.ui.features.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.auth.ProviderOAuthStatus
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgAiProviderRegistration
import com.ai.assistance.operit.plugins.toolpkg.ToolPkgProviderOAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ToolPkgOAuthSettingsBlock(
    provider: ToolPkgAiProviderRegistration,
    config: ModelConfigData,
    onAuthChanged: () -> Unit,
) {
    key(provider.containerPackageName, provider.providerId, provider.auth, config.id, config.apiEndpoint) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val manager = remember { ToolPkgProviderOAuth.getInstance(context) }
        val revision by manager.changes.collectAsState()
        var status by remember { mutableStateOf(ProviderOAuthStatus(false, false, null)) }
        var ready by remember { mutableStateOf(false) }
        var working by remember { mutableStateOf(false) }
        var job by remember { mutableStateOf<Job?>(null) }
        var error by remember { mutableStateOf(false) }

        LaunchedEffect(revision) {
            ready = false
            try {
                status = manager.status(provider, config)
                ready = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = ProviderOAuthStatus(false, false, null)
            }
        }
        DisposableEffect(Unit) { onDispose { job?.cancel() } }

        fun runAction(action: suspend () -> Unit) {
            if (working) return
            working = true
            error = false
            job = scope.launch {
                try {
                    action()
                    status = manager.status(provider, config)
                    onAuthChanged()
                } catch (_: TimeoutCancellationException) {
                    error = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Never expose provider response bodies, codes or tokens in notifications.
                    error = true
                } finally {
                    working = false
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.toolpkg_oauth_title, provider.displayName), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.toolpkg_oauth_authorization_endpoint, provider.auth?.authorizationEndpoint.orEmpty()),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.toolpkg_oauth_token_endpoint, provider.auth?.tokenEndpoint.orEmpty()),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (status.signedIn) R.string.toolpkg_oauth_signed_in else R.string.toolpkg_oauth_signed_out))
            Text(stringResource(R.string.toolpkg_oauth_trust_notice), style = MaterialTheme.typography.bodySmall)
            if (!ready) Text(stringResource(R.string.toolpkg_oauth_configure_endpoint))
            if (error) Text(stringResource(R.string.toolpkg_oauth_failed), color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = ready && !working, onClick = {
                    runAction { manager.login(context, provider, config) }
                }) { Text(stringResource(R.string.toolpkg_oauth_login)) }
                if (working) {
                    OutlinedButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.toolpkg_oauth_cancel)) }
                } else if (status.signedIn || status.canRefresh) {
                    OutlinedButton(onClick = { runAction { manager.logout(provider, config) } }) {
                        Text(stringResource(R.string.toolpkg_oauth_logout))
                    }
                }
            }
            if (status.canRefresh && !working) {
                OutlinedButton(onClick = { runAction { manager.refresh(provider, config) } }) {
                    Text(stringResource(R.string.toolpkg_oauth_refresh))
                }
            }
        }
    }
}
