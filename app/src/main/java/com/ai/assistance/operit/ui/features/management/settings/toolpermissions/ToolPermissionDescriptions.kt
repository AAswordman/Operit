package com.ai.assistance.operit.ui.features.management.settings.toolpermissions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.util.android.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 权限列表展示工具用途，执行确认使用的操作说明可能包含空参数或固定英文，不能混用。
private val chineseToolDescriptions by lazy {
    SystemToolPrompts.getAllCategoriesCn().flatMap { it.tools }.associate { it.name to it.description }
}
private val englishToolDescriptions by lazy {
    SystemToolPrompts.getAllCategoriesEn().flatMap { it.tools }.associate { it.name to it.description }
}

@Composable
internal fun rememberPermissionToolDescriptions(
    tools: List<String>,
    handler: AIToolHandler,
): Map<String, String> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // 列表变更时先建立完整键集合，异步读取期间搜索和行渲染不会访问上一轮的旧键。
    val descriptions = remember(tools, context, configuration) {
        mutableStateOf(tools.associateWith { "" })
    }
    LaunchedEffect(tools, context, configuration) {
        descriptions.value = withContext(Dispatchers.Default) {
            resolvePermissionToolDescriptions(tools, handler, context)
        }
    }
    return descriptions.value
}

private fun resolvePermissionToolDescriptions(
    tools: List<String>,
    handler: AIToolHandler,
    context: Context,
): Map<String, String> {
    val localizedContext = LocaleUtils.getLocalizedContext(context)
    val nativeDescriptions =
        if (LocaleUtils.usesChineseContent(localizedContext)) chineseToolDescriptions
        else englishToolDescriptions
    val packageNames = tools.filter { ':' in it }.map { it.substringBefore(':') }.toSet()
    val packageManager = if (packageNames.isEmpty()) null else handler.getOrCreatePackageManager()
    val packageTools = packageNames.associateWith { name ->
        packageManager?.getEffectivePackageTools(name)?.tools.orEmpty()
            .associateBy { it.name }
    }
    val serverPackages = packageManager?.getAvailableServerPackages().orEmpty()
    return tools.associateWith { name ->
        if (':' !in name) {
            nativeDescriptions[name].orEmpty()
        } else {
            val packageName = name.substringBefore(':')
            if (packageName in serverPackages) {
                // MCP 的工具说明由服务端提供，不把原文猜译成另一种语言。
                handler.getToolDescription(name)
            } else {
                packageTools.getValue(packageName)[name.substringAfter(':')]
                    ?.description?.resolve(localizedContext).orEmpty()
            }
        }
    }
}
