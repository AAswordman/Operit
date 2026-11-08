package com.ai.assistance.operit.ui.features.automation.terminal

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.ui.features.management.resources.OfflineResourcesActivity
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 所有终端入口共用资源检查，准备好会话后交给聊天工作台显示。 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TerminalEntryScreen(
    initialPage: EmbeddedTerminalPage,
    onOpenTerminal: (EmbeddedTerminalPage) -> Unit,
    onGoBack: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(initialPage) {
        try {
            val resourcesReady = withContext(Dispatchers.IO) {
                OfflineResources.ubuntuInstalled(context) ||
                    OfflineResources.isReady(context, OfflineResources.UBUNTU)
            }
            if (!resourcesReady) {
                context.startActivity(
                    Intent(context, OfflineResourcesActivity::class.java)
                        .putExtra(OfflineResourcesActivity.EXTRA_GROUP, OfflineResources.UBUNTU)
                )
                onGoBack()
                return@LaunchedEffect
            }
            val manager = TerminalManager.getInstance(context)
            // 配置命令需要真实会话，不能直接在未安装环境里打开包检查页面。
            if (manager.terminalState.value.sessions.isEmpty()) {
                manager.createNewSession(context.getString(R.string.workspace_terminal_session_title, 1))
            }
            onOpenTerminal(initialPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("TerminalEntry", "打开终端失败", e)
            onError(context.getString(R.string.workspace_terminal_failed))
            onGoBack()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
