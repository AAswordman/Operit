package com.ai.assistance.operit.ui.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.material3.ColorScheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.AITool
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
    FORBID;     // Never allow

    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> ASK
                "ASK" -> ASK
                "FORBID" -> FORBID
                else -> ASK  // Default to ASK
            }
        }
    }
}

data class PermissionRequestState(
    val chatId: String?,
    val tool: AITool,
    val description: String
)

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
    private var currentPermissionCallback: ((ToolPermissionCheckResult) -> Unit)? = null
    private var permissionRequestInfo: PermissionRequestState? = null
    
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
    private val _permissionRequestState = MutableStateFlow<PermissionRequestState?>(null)
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
    suspend fun checkToolPermission(
        tool: AITool,
        chatId: String? = null
    ): ToolPermissionCheckResult {
        AppLogger.d(TAG, "Starting permission check: ${tool.name}")
        
        val preferences = context.toolPermissionsDataStore.data.first()
        val masterSwitch = PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
        val key = toolPermissionKey(tool.name)
        val overrideLevel = preferences[key]?.let { PermissionLevel.fromString(it) }
        
        val permissionLevel = overrideLevel ?: masterSwitch
        
        return when (permissionLevel) {
            PermissionLevel.ALLOW -> ToolPermissionCheckResult.GRANTED
            PermissionLevel.ASK -> requestPermission(tool, chatId)
            PermissionLevel.FORBID -> ToolPermissionCheckResult.DENIED
        }
    }
    
    /**
     * Request permission from the user to execute a tool
     */
    private suspend fun requestPermission(
        tool: AITool,
        chatId: String?
    ): ToolPermissionCheckResult {
        // Get operation description
        val operationDescription = getOperationDescription(tool)
        
        AppLogger.d(TAG, "Requesting permission: ${tool.name}")
        
        // Clear existing request
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // Set up new request
        val requestInfo = PermissionRequestState(
            chatId = chatId?.trim()?.takeIf { it.isNotBlank() },
            tool = tool,
            description = operationDescription
        )
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        AppLogger.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                currentPermissionCallback = { result ->
                    AppLogger.d(TAG, "Permission result received: $result for ${tool.name}")
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    continuation.resume(result)
                }
                
                // Start permission request on main thread
                mainHandler.post {
                    // Use overlay to show permission request
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        AppLogger.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(
                            ToolPermissionCheckResult.OVERLAY_PERMISSION_REQUIRED
                        )
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            AppLogger.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            ToolPermissionCheckResult.CONFIRMATION_TIMEOUT
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        val permissionResult =
            when (result) {
                PermissionRequestResult.ALLOW -> ToolPermissionCheckResult.GRANTED
                PermissionRequestResult.DENY -> ToolPermissionCheckResult.DENIED
                PermissionRequestResult.ALWAYS_ALLOW -> {
                    permissionRequestInfo?.tool?.let { tool ->
                        CoroutineScope(Dispatchers.IO).launch {
                            saveToolPermission(tool.name, PermissionLevel.ALLOW)
                        }
                    }
                    ToolPermissionCheckResult.GRANTED
                }
            }
        currentPermissionCallback?.invoke(permissionResult)
    }
    
    /**
     * Get current permission request info
     */
    fun getCurrentPermissionRequest(): PermissionRequestState? {
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
