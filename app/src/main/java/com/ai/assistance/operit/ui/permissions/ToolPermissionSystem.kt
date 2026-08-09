package com.ai.assistance.operit.ui.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import androidx.compose.material3.ColorScheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Define DataStore
private val Context.toolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

/**
 * Permission levels for tool operations
 */
enum class PermissionLevel {
    ALLOW,      // Allow automatically without asking
    ASK,        // Always ask
    LLM,        // Approval model decides; hands over to the user when it cannot decide
    FORBID;     // Never allow

    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> ASK
                "ASK" -> ASK
                "LLM" -> LLM
                "FORBID" -> FORBID
                else -> ASK  // Default to ASK
            }
        }
    }
}

/**
 * Centralized tool permission system that manages both permission storage and checking
 */
class ToolPermissionSystem private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolPermissionSystem"
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // 60 seconds timeout

        // DataStore keys
        private val MASTER_SWITCH = stringPreferencesKey("master_switch")

        // Default permission setting
        private val DEFAULT_MASTER_SWITCH = PermissionLevel.ASK.name

        @Volatile
        private var INSTANCE: ToolPermissionSystem? = null

        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolPermissionSystem(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 工具权限存储：使用 "tool_permission_<tool_name>" 作为key
    private fun toolPermissionKey(toolName: String) = stringPreferencesKey("tool_permission_$toolName")
    
    // Permission request management
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRequestOverlay = PermissionRequestOverlay(context)
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    // 存储当前颜色方案
    private var currentColorScheme: ColorScheme? = null
    
    /**
     * 设置当前使用的颜色方案
     */
    fun setColorScheme(colorScheme: ColorScheme?) {
        this.currentColorScheme = colorScheme
        permissionRequestOverlay.setColorScheme(colorScheme)
    }
    
    // Permission request state flow
    private val _permissionRequestState = MutableStateFlow<Pair<AITool, String>?>(null)
    val permissionRequestState = _permissionRequestState.asStateFlow()
    
    // Permission level flows
    val masterSwitchFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
    }
    
    /**
     * Get permission level flow for a specific tool
     * If no permission is set for the tool, returns ASK as default
     */
    fun getToolPermissionFlow(toolName: String): Flow<PermissionLevel> {
        return context.toolPermissionsDataStore.data.map { preferences ->
            val key = toolPermissionKey(toolName)
            PermissionLevel.fromString(preferences[key] ?: PermissionLevel.ASK.name)
        }
    }
    
    // Registry of operation descriptions by tool name
    private val operationDescriptionRegistry = mutableMapOf<String, (AITool) -> String>()
    
    /**
     * Register a description generator for a tool
     */
    fun registerOperationDescription(toolName: String, descriptionGenerator: (AITool) -> String) {
        operationDescriptionRegistry[toolName] = descriptionGenerator
    }
    
    /**
     * Save permission level settings
     */
    suspend fun saveMasterSwitch(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = level.name
        }
    }
    
    /**
     * Save permission level for a specific tool
     */
    suspend fun saveToolPermission(toolName: String, level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            val key = toolPermissionKey(toolName)
            preferences[key] = level.name
        }
    }
    
    suspend fun clearToolPermission(toolName: String) {
        context.toolPermissionsDataStore.edit { preferences ->
            val key = toolPermissionKey(toolName)
            preferences.remove(key)
        }
    }
    
    /**
     * Save permission levels for multiple tools at once
     */
    suspend fun saveToolPermissions(toolPermissions: Map<String, PermissionLevel>) {
        context.toolPermissionsDataStore.edit { preferences ->
            toolPermissions.forEach { (toolName, level) ->
                val key = toolPermissionKey(toolName)
                preferences[key] = level.name
            }
        }
    }
    
    /**
     * Get permission level for a specific tool (synchronous, for one-time reads)
     * If no permission is set for the tool, returns ASK as default
     */
    suspend fun getToolPermission(toolName: String): PermissionLevel {
        val preferences = context.toolPermissionsDataStore.data.first()
        val key = toolPermissionKey(toolName)
        return PermissionLevel.fromString(preferences[key] ?: PermissionLevel.ASK.name)
    }
    
    suspend fun getToolPermissionOverride(toolName: String): PermissionLevel? {
        val preferences = context.toolPermissionsDataStore.data.first()
        val key = toolPermissionKey(toolName)
        val stored = preferences[key]
        return stored?.let { PermissionLevel.fromString(it) }
    }
    
    /**
     * Get human-readable description of an operation
     */
    fun getOperationDescription(tool: AITool): String {
        return operationDescriptionRegistry[tool.name]?.invoke(tool) ?: context.getString(R.string.tool_permission_operation, tool.name)
    }
    
    /**
     * Check if a tool is allowed to execute
     */
    suspend fun checkToolPermission(tool: AITool, toolInvocationRawText: String? = null): Boolean {
        AppLogger.d(TAG, "Starting permission check: ${tool.name}")
        
        val preferences = context.toolPermissionsDataStore.data.first()
        val masterSwitch = PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
        val key = toolPermissionKey(tool.name)
        val overrideLevel = preferences[key]?.let { PermissionLevel.fromString(it) }
        
        val permissionLevel = overrideLevel ?: masterSwitch
        
        return when (permissionLevel) {
            PermissionLevel.ALLOW -> true
            PermissionLevel.ASK -> requestPermission(tool)
            PermissionLevel.LLM -> when (requestLlmApproval(tool, toolInvocationRawText)) {
                LlmApprovalDecision.APPROVE -> true
                LlmApprovalDecision.DENY -> false
                // ASK: 模型明确表示无法自主判断; null: API 错误或输出不符合约定
                // 两者按产品设计都转交用户手动确认, 复用 ASK 档的弹窗流程
                LlmApprovalDecision.ASK, null -> requestPermission(tool)
            }
            PermissionLevel.FORBID -> false
        }
    }

    private enum class LlmApprovalDecision { APPROVE, DENY, ASK }

    // 最近一次 LLM 拒绝的 (toolName -> reason), 供调用方生成带理由的拒绝结果
    @Volatile
    private var lastLlmDenial: Pair<String, String>? = null

    /**
     * 取出指定工具最近一次由审批模型给出的拒绝理由, 取出后即清除。
     * 返回 null 表示该工具最近的拒绝并非来自审批模型 (如用户手动拒绝)。
     */
    fun consumeLlmDenialReason(toolName: String): String? {
        val denial = lastLlmDenial ?: return null
        if (denial.first != toolName) return null
        lastLlmDenial = null
        return denial.second
    }

    /**
     * 调用审批模型决定是否允许工具调用。
     * decision 为必填字段: approve 放行, deny 拒绝, ask 表示模型无法自主判断需转人工;
     * 返回 null 表示输出不符合约定 (API 错误 / 不是合法 JSON / decision 缺失或非法),
     * 调用方对 ASK 与 null 都转交用户手动确认。
     */
    private suspend fun requestLlmApproval(tool: AITool, toolInvocationRawText: String?): LlmApprovalDecision? {
        return try {
            // 没有拿到调用原文时按同样的标签格式重建, 保证审批模型看到的始终是工具调用原文
            val rawText = toolInvocationRawText?.takeIf { it.isNotBlank() } ?: buildToolInvocationText(tool)
            val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
            val prompt = FunctionalPrompts.buildToolApprovalPrompt(rawText, useEnglish)

            val service = EnhancedAIService.getAIServiceForFunction(context, FunctionType.TOOL_APPROVAL)
            val functionalConfigManager = FunctionalConfigManager(context)
            functionalConfigManager.initializeIfNeeded()
            val modelConfigManager = ModelConfigManager(context)
            val mapping = functionalConfigManager.getConfigMappingForFunction(FunctionType.TOOL_APPROVAL)
            val modelParameters = modelConfigManager.getModelParametersForConfig(mapping.configId)

            val sb = StringBuilder()
            service.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters,
                enableThinking = false,
                stream = false,
                availableTools = null
            ).collect { chunk -> sb.append(chunk) }

            val text = sb.toString().trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) {
                AppLogger.w(TAG, "LLM approval output has no JSON object for ${tool.name}: $text")
                return null
            }
            val obj = JSONObject(text.substring(start, end + 1))
            val decision = obj.optString("decision", "").trim().lowercase()
            val reason = obj.optString("reason", "")
            when (decision) {
                "approve" -> {
                    AppLogger.d(TAG, "LLM approval approved ${tool.name}: $reason")
                    LlmApprovalDecision.APPROVE
                }
                "deny" -> {
                    AppLogger.d(TAG, "LLM approval denied ${tool.name}: $reason")
                    lastLlmDenial = tool.name to reason.ifBlank { "No reason provided" }
                    LlmApprovalDecision.DENY
                }
                "ask" -> {
                    AppLogger.d(TAG, "LLM approval asked for manual review of ${tool.name}: $reason")
                    LlmApprovalDecision.ASK
                }
                else -> {
                    // decision 为必填字段, 缺失或取值非法说明输出不符合约定
                    AppLogger.w(TAG, "LLM approval returned invalid decision '$decision' for ${tool.name}")
                    null
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "LLM approval failed for ${tool.name}, handing over to user", e)
            null
        }
    }

    private fun buildToolInvocationText(tool: AITool): String {
        return buildString {
            append("<tool name=\"")
            append(tool.name)
            append("\">")
            tool.parameters.forEach { parameter ->
                append("<param name=\"")
                append(parameter.name)
                append("\">")
                append(parameter.value)
                append("</param>")
            }
            append("</tool>")
        }
    }

    /**
     * Request permission from the user to execute a tool
     */
    private suspend fun requestPermission(tool: AITool): Boolean {
        // Get operation description
        val operationDescription = getOperationDescription(tool)
        
        AppLogger.d(TAG, "Requesting permission: ${tool.name}")
        
        // Clear existing request
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // Set up new request
        val requestInfo = Pair(tool, operationDescription)
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        AppLogger.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // Set callback
                currentPermissionCallback = { result ->
                    AppLogger.d(TAG, "Permission result received: $result for ${tool.name}")
                    // Clean up state
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    
                    // Handle result
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.ALWAYS_ALLOW -> {
                            // Save the permission and resume
                            tool.let {
                                val toolScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                toolScope.launch {
                                    saveToolPermission(it.name, PermissionLevel.ALLOW)
                                }
                            }
                            continuation.resume(true)
                        }
                    }
                }
                
                // Start permission request on main thread
                mainHandler.post {
                    // Use overlay to show permission request
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        AppLogger.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(PermissionRequestResult.DENY)
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            // Timeout handling
            AppLogger.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            false
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * Get current permission request info
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * Check if there is an active permission request
     */
    fun hasActivePermissionRequest(): Boolean {
        return permissionRequestInfo != null && currentPermissionCallback != null
    }
    
    /**
     * Refresh permission request state
     */
    fun refreshPermissionRequestState(): Boolean {
        return hasActivePermissionRequest()
    }
}
