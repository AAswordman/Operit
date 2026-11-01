package com.ai.assistance.operit.ui.features.automation.websession.browser

import android.net.Uri
import android.graphics.Color as AndroidColor
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBookmark
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBrowserHostState
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBrowserSheetRoute
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionHistoryEntry
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionPendingDialogState
import com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionWebViewHost
import com.ai.assistance.operit.core.tools.builtin.web.session.userscript.ui.WebSessionUserscriptUiState
import java.util.Locale

@Composable
internal fun WebSessionBrowserScreen(
    hostState: WebSessionBrowserHostState,
    bookmarks: List<WebSessionBookmark>,
    globalHistory: List<WebSessionHistoryEntry>,
    userscriptUiState: WebSessionUserscriptUiState,
    webViewHost: WebSessionWebViewHost,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onMinimize: () -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onSelectSessionHistory: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenUserscripts: () -> Unit,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit,
    onConfirmExternalOpen: (String) -> Unit,
    onCancelExternalOpen: (String) -> Unit,
    onHandlePendingDialog: (Boolean, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val browserState = hostState.browserState
    var totalHeightPx by remember { mutableIntStateOf(0) }
    var browserAreaHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(totalHeightPx, browserAreaHeightPx) {
        val chromeHeightPx = (totalHeightPx - browserAreaHeightPx).coerceAtLeast(0)
        if (
            chromeHeightPx != hostState.chromeHeightPx ||
                browserAreaHeightPx != hostState.browserAreaHeightPx
        ) {
            onHostStateChange { current ->
                if (
                    current.chromeHeightPx == chromeHeightPx &&
                        current.browserAreaHeightPx == browserAreaHeightPx
                ) {
                    current
                } else {
                    current.copy(
                        chromeHeightPx = chromeHeightPx,
                        browserAreaHeightPx = browserAreaHeightPx
                    )
                }
            }
        }
    }
    val currentTabNumber =
        browserState.tabs.indexOfFirst { it.isActive }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
    val isBookmarked =
        remember(browserState.currentUrl, bookmarks) {
            val normalizedUrl = normalizeLookupUrl(browserState.currentUrl)
            normalizedUrl != null && bookmarks.any { it.url == normalizedUrl }
        }
    val dismissSheet = {
        onHostStateChange { current ->
            current.copy(sheetRoute = WebSessionBrowserSheetRoute.NONE)
        }
    }
    val activeSheetRoute = hostState.sheetRoute
    var promptDraft by remember(browserState.pendingDialog?.message, browserState.pendingDialog?.defaultValue) {
        mutableStateOf(browserState.pendingDialog?.defaultValue.orEmpty())
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { totalHeightPx = it.height }
            ) {
            WebSessionTopUrlBar(
                url = browserState.currentUrl.ifBlank { "about:blank" },
                pageTitle = browserState.pageTitle,
                isLoading = browserState.isLoading,
                isEditing = hostState.isEditingUrl,
                urlDraft = hostState.urlDraft,
                isBookmarked = isBookmarked,
                onStartEditing = {
                    onHostStateChange { current ->
                        current.copy(
                            isEditingUrl = true,
                            urlDraft = browserState.currentUrl.ifBlank { "about:blank" }
                        )
                    }
                },
                onUrlDraftChange = { draft ->
                    onHostStateChange { current ->
                        current.copy(urlDraft = draft)
                    }
                },
                onSubmitUrl = {
                    val target = normalizeNavigationUrl(hostState.urlDraft)
                    onNavigate(target)
                    onHostStateChange { current ->
                        current.copy(
                            isEditingUrl = false,
                            urlDraft = target
                        )
                    }
                },
                onStopEditing = {
                    onHostStateChange { current ->
                        current.copy(
                            isEditingUrl = false,
                            urlDraft = browserState.currentUrl
                        )
                    }
                },
                onToggleBookmark = {
                    onToggleBookmark(browserState.currentUrl, browserState.pageTitle)
                },
                onRefreshOrStop = onRefreshOrStop,
                onMinimize = onMinimize,
                modifier = Modifier.statusBarsPadding()
            )

            hostState.externalOpenPrompt?.let { prompt ->
                ExternalOpenPromptBar(
                    title = prompt.title,
                    target = prompt.target,
                    onConfirm = { onConfirmExternalOpen(prompt.requestId) },
                    onCancel = { onCancelExternalOpen(prompt.requestId) }
                )
            }

            if (browserState.activeDownloadCount > 0) {
                BrowserDownloadSummaryBar(
                    activeCount = browserState.activeDownloadCount,
                    overallProgress = browserState.overallDownloadProgress,
                    onClick = {
                        onHostStateChange { current ->
                            current.copy(sheetRoute = WebSessionBrowserSheetRoute.DOWNLOADS)
                        }
                    }
                )
            }

            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            )

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            browserAreaHeightPx = it.height
                            if (it.width != hostState.browserAreaWidthPx) {
                                onHostStateChange { current ->
                                    if (current.browserAreaWidthPx == it.width) {
                                        current
                                    } else {
                                        current.copy(browserAreaWidthPx = it.width)
                                    }
                                }
                            }
                        }
                        .background(MaterialTheme.colorScheme.background)
            ) {
                if (browserState.activeSessionId == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.web_session_no_tabs),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.web_session_new_tab),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { context ->
                                FrameLayout(context).apply {
                                    setBackgroundColor(AndroidColor.WHITE)
                                    webViewHost.attachContainer(this)
                                }
                            },
                            update = { container ->
                                webViewHost.attachContainer(container)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            )

            WebSessionBottomToolbar(
                canGoBack = browserState.canGoBack,
                canGoForward = browserState.canGoForward,
                currentTabNumber = currentTabNumber,
                onBack = onBack,
                onForward = onForward,
                onNewTab = onNewTab,
                onTabs = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.TABS)
                    }
                },
                onMenu = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.MENU)
                    }
                },
                modifier = Modifier.navigationBarsPadding()
            )
            }
        }

        if (activeSheetRoute != WebSessionBrowserSheetRoute.NONE) {
            val scrimInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            onClick = dismissSheet
                        )
            )

            // WebSession lives in an overlay window, so using ModalBottomSheet would
            // create a dialog window that does not have a valid activity token here.
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp
            ) {
                WebSessionOverlaySheetContent(
                    sheetRoute = activeSheetRoute,
                    browserState = browserState,
                    bookmarks = bookmarks,
                    globalHistory = globalHistory,
                    userscriptUiState = userscriptUiState,
                    onDismiss = dismissSheet,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onNewTab = onNewTab,
                    onCloseCurrentTab = onCloseCurrentTab,
                    onCloseAllTabs = onCloseAllTabs,
                    onRemoveBookmark = onRemoveBookmark,
                    onSelectSessionHistory = onSelectSessionHistory,
                    onOpenUrl = onOpenUrl,
                    onClearHistory = onClearHistory,
                    onToggleDesktopMode = onToggleDesktopMode,
                    onHostStateChange = onHostStateChange,
                    hostState = hostState,
                    onOpenUserscripts = onOpenUserscripts,
                    onImportUserscript = onImportUserscript,
                    onInstallUserscriptFromUrl = onInstallUserscriptFromUrl,
                    onConfirmUserscriptInstall = onConfirmUserscriptInstall,
                    onCancelUserscriptInstall = onCancelUserscriptInstall,
                    onSetUserscriptEnabled = onSetUserscriptEnabled,
                    onDeleteUserscript = onDeleteUserscript,
                    onCheckUserscriptUpdate = onCheckUserscriptUpdate,
                    onInvokeUserscriptMenu = onInvokeUserscriptMenu,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onCancelDownload = onCancelDownload,
                    onRetryDownload = onRetryDownload,
                    onDeleteDownload = onDeleteDownload,
                    onOpenDownloadedFile = onOpenDownloadedFile,
                    onOpenDownloadLocation = onOpenDownloadLocation
                )
            }
        }

        browserState.pendingDialog?.let { pendingDialog ->
            PendingDialogOverlay(
                dialog = pendingDialog,
                promptValue = promptDraft,
                onPromptValueChange = { promptDraft = it },
                onConfirm = {
                    onHandlePendingDialog(true, promptDraft)
                },
                onDismiss = {
                    onHandlePendingDialog(false, null)
                }
            )
        }
    }
}

@Composable
private fun PendingDialogOverlay(
    dialog: WebSessionPendingDialogState,
    promptValue: String,
    onPromptValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text =
                        when (dialog.type.lowercase(Locale.ROOT)) {
                            "confirm" -> stringResource(R.string.web_session_dialog_title_confirm)
                            "prompt" -> stringResource(R.string.web_session_dialog_title_prompt)
                            else -> stringResource(R.string.web_session_dialog_title_alert)
                        },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dialog.message.ifBlank { stringResource(R.string.web_session_dialog_empty_message) },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (dialog.type.equals("prompt", ignoreCase = true)) {
                    OutlinedTextField(
                        value = promptValue,
                        onValueChange = onPromptValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!dialog.type.equals("alert", ignoreCase = true)) {
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    }
                    TextButton(onClick = onConfirm) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSessionOverlaySheetContent(
    sheetRoute: WebSessionBrowserSheetRoute,
    browserState: com.ai.assistance.operit.core.tools.builtin.web.session.browser.WebSessionBrowserState,
    bookmarks: List<WebSessionBookmark>,
    globalHistory: List<WebSessionHistoryEntry>,
    userscriptUiState: WebSessionUserscriptUiState,
    onDismiss: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseCurrentTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onSelectSessionHistory: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onHostStateChange: ((WebSessionBrowserHostState) -> WebSessionBrowserHostState) -> Unit,
    hostState: WebSessionBrowserHostState,
    onOpenUserscripts: () -> Unit,
    onImportUserscript: () -> Unit,
    onInstallUserscriptFromUrl: (String) -> Unit,
    onConfirmUserscriptInstall: () -> Unit,
    onCancelUserscriptInstall: () -> Unit,
    onSetUserscriptEnabled: (Long, Boolean) -> Unit,
    onDeleteUserscript: (Long) -> Unit,
    onCheckUserscriptUpdate: (Long) -> Unit,
    onInvokeUserscriptMenu: (String) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onDeleteDownload: (String, Boolean) -> Unit,
    onOpenDownloadedFile: (String) -> Unit,
    onOpenDownloadLocation: (String) -> Unit
) {
    when (sheetRoute) {
        WebSessionBrowserSheetRoute.TABS ->
            WebSessionTabSheet(
                tabs = browserState.tabs,
                onSelectTab = { sessionId ->
                    onSelectTab(sessionId)
                    onDismiss()
                },
                onCloseTab = onCloseTab,
                onNewTab = {
                    onNewTab()
                    onDismiss()
                }
            )

        WebSessionBrowserSheetRoute.MENU ->
            WebSessionMenuSheet(
                isDesktopMode = browserState.isDesktopMode,
                downloadSummary =
                    stringResource(
                        R.string.web_session_downloads_summary,
                        browserState.activeDownloadCount,
                        browserState.failedDownloadCount
                    ),
                onOpenHistory = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.HISTORY)
                    }
                },
                onOpenDownloads = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.DOWNLOADS)
                    }
                },
                onOpenBookmarks = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.BOOKMARKS)
                    }
                },
                onOpenUserscripts = {
                    onHostStateChange { current ->
                        current.copy(sheetRoute = WebSessionBrowserSheetRoute.USERSCRIPTS)
                    }
                    onOpenUserscripts()
                },
                userscriptMenuCommands = browserState.userscriptMenuCommands,
                onInvokeUserscriptMenu = { commandId ->
                    onDismiss()
                    onInvokeUserscriptMenu(commandId)
                },
                onToggleDesktopMode = {
                    onDismiss()
                    onToggleDesktopMode()
                },
                onCloseCurrentTab = {
                    onDismiss()
                    onCloseCurrentTab()
                },
                onCloseAllTabs = {
                    onDismiss()
                    onCloseAllTabs()
                }
            )

        WebSessionBrowserSheetRoute.DOWNLOADS ->
            WebSessionDownloadSheet(
                uiState = hostState.downloadUiState,
                onFilterChange = { filter ->
                    onHostStateChange { current ->
                        current.copy(
                            downloadUiState =
                                current.downloadUiState.copy(selectedFilter = filter)
                        )
                    }
                },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onRetryDownload = onRetryDownload,
                onDeleteDownload = onDeleteDownload,
                onOpenDownloadedFile = onOpenDownloadedFile,
                onOpenDownloadLocation = onOpenDownloadLocation
            )

        WebSessionBrowserSheetRoute.HISTORY ->
            WebSessionHistorySheet(
                sessionHistory = browserState.sessionHistory,
                globalHistory = globalHistory,
                onSelectSessionHistory = { index ->
                    onSelectSessionHistory(index)
                    onDismiss()
                },
                onOpenHistoryUrl = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onClearHistory = onClearHistory
            )

        WebSessionBrowserSheetRoute.BOOKMARKS ->
            WebSessionBookmarkSheet(
                bookmarks = bookmarks,
                onOpenBookmark = { url ->
                    onOpenUrl(url)
                    onDismiss()
                },
                onRemoveBookmark = onRemoveBookmark
            )

        WebSessionBrowserSheetRoute.USERSCRIPTS ->
            WebSessionUserscriptSheet(
                state = userscriptUiState,
                currentPageMenuCommands = browserState.userscriptMenuCommands,
                onInstallFromUrl = onInstallUserscriptFromUrl,
                onImportLocal = onImportUserscript,
                onConfirmInstall = onConfirmUserscriptInstall,
                onCancelInstall = onCancelUserscriptInstall,
                onSetScriptEnabled = onSetUserscriptEnabled,
                onDeleteScript = onDeleteUserscript,
                onCheckUpdate = onCheckUserscriptUpdate,
                onInvokeMenuCommand = onInvokeUserscriptMenu
            )

        WebSessionBrowserSheetRoute.NONE -> Unit
    }
}

@Composable
private fun ExternalOpenPromptBar(
    title: String,
    target: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = target,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.web_session_external_open_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(R.string.web_session_external_open_allow_once))
                }
            }
        }
    }
}

@Composable
private fun BrowserDownloadSummaryBar(
    activeCount: Int,
    overallProgress: Float?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.web_session_downloads_active_bar, activeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text =
                    overallProgress?.let { progress ->
                        "${(progress * 100f).toInt()}%"
                    } ?: stringResource(R.string.web_session_downloads_active_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun normalizeNavigationUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return "about:blank"
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (
        lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("about:")
    ) {
        return trimmed
    }

    return if (!trimmed.contains("://") && trimmed.contains(".") && !trimmed.contains(" ")) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

private fun normalizeLookupUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return null
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) {
        return null
    }
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
        return null
    }

    return runCatching {
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val portPart =
            when {
                uri.port < 0 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        val path = uri.encodedPath?.ifBlank { "/" } ?: "/"
        buildString {
            append(scheme)
            append("://")
            append(host)
            append(portPart)
            append(path)
            uri.encodedQuery?.takeIf { it.isNotBlank() }?.let {
                append('?')
                append(it)
            }
        }
    }.getOrElse { trimmed }
}
