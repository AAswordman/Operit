package com.ai.assistance.operit.ui.features.management.settings.toolpermissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

private val permissionProxyTools = setOf("package_proxy", "proxy", "search")

/** 只合并原生执行器、当前启用包的真实工具，以及已连接服务的注册工具。 */
internal fun permissionToolNames(
    registered: Collection<String>,
    enabledPackages: List<ToolPackage>,
    activeServers: Set<String>,
): List<String> {
    val runtimeTools = registered.filter { name ->
        name !in permissionProxyTools && (':' !in name || name.substringBefore(':') in activeServers)
    }
    val packageTools = enabledPackages.flatMap { toolPackage ->
        toolPackage.tools.filterNot { it.advice }.map { "${toolPackage.name}:${it.name}" }
    }
    return (runtimeTools + packageTools).distinct().sorted()
}

@Composable
internal fun rememberPermissionTools(handler: AIToolHandler, isCurrent: Boolean): List<String>? {
    val context = LocalContext.current
    val tools by produceState<List<String>?>(null, handler, isCurrent) {
        if (!isCurrent) return@produceState
        withContext(Dispatchers.Default) {
            handler.registerDefaultTools()
            val packageManager = handler.getOrCreatePackageManager()
            val mcpManager = MCPManager.getInstance(context)
            val packageChanges = callbackFlow<Unit> {
                // 现有包状态通知也覆盖沙盒包和子包开关，订阅时会立即同步当前目录。
                val listener = PackageManager.ToolPkgRuntimeChangeListener { trySend(Unit) }
                packageManager.addToolPkgRuntimeChangeListener(listener)
                awaitClose { packageManager.removeToolPkgRuntimeChangeListener(listener) }
            }.conflate()
            combine(handler.registryRevision, packageChanges) { _, _ ->
                handler.registerDefaultTools()
                val activeServers = packageManager.getAvailableServerPackages().keys
                    .filter { mcpManager.isSessionActive(it) }.toSet()
                permissionToolNames(
                    handler.getAllToolNames(),
                    packageManager.getEnabledExecutablePackages(),
                    activeServers,
                )
            }.collect { value = it }
        }
    }
    return tools
}