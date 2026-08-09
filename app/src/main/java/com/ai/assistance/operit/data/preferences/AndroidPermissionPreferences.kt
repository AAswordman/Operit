package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.androidPermissionDataStore: DataStore<Preferences> by
        recoverablePreferencesDataStore(name = "android_permission_preferences")

enum class RootCommandExecutionMode {
    AUTO,
    FORCE_LIBSU,
    FORCE_EXEC;

    companion object {
        fun fromString(value: String?): RootCommandExecutionMode {
            return values().firstOrNull { it.name == value } ?: AUTO
        }
    }
}

/** 全局单例实例 */
lateinit var androidPermissionPreferences: AndroidPermissionPreferences
    private set

private val androidPermissionPreferencesInitLock = Any()
@Volatile
private var androidPermissionPreferencesInitialized = false

/** 初始化Android权限偏好管理器。该入口可被多个进程组件重复调用。 */
fun initAndroidPermissionPreferences(context: Context) {
    if (androidPermissionPreferencesInitialized) {
        return
    }
    synchronized(androidPermissionPreferencesInitLock) {
        if (androidPermissionPreferencesInitialized) {
            return
        }
        androidPermissionPreferences = AndroidPermissionPreferences(context)
        androidPermissionPreferencesInitialized = true
    }
}

/** Android权限偏好管理器 负责管理应用全局的权限级别偏好设置 */
class AndroidPermissionPreferences(private val context: Context) {
    companion object {
        private const val TAG = "AndroidPermissionPrefs"
        const val DEFAULT_SU_COMMAND = "su"

        // 权限相关键
        private val PREFERRED_PERMISSION_LEVEL = stringPreferencesKey("preferred_permission_level")
        private val ROOT_EXECUTION_MODE = stringPreferencesKey("root_execution_mode")
        private val CUSTOM_SU_COMMAND = stringPreferencesKey("custom_su_command")
    }

    private fun normalizeSuCommand(command: String?): String {
        val normalized = command?.trim().orEmpty()
        return normalized.ifEmpty { DEFAULT_SU_COMMAND }
    }

    /** 首选权限级别Flow 返回用户配置的首选Android权限级别，如果未设置则返回null */
    val preferredPermissionLevelFlow: Flow<AndroidPermissionLevel?> =
            context.androidPermissionDataStore.data.map { preferences ->
                val levelString = preferences[PREFERRED_PERMISSION_LEVEL]
                if (levelString != null) AndroidPermissionLevel.fromString(levelString) else null
            }

    val rootExecutionModeFlow: Flow<RootCommandExecutionMode> =
            context.androidPermissionDataStore.data.map { preferences ->
                RootCommandExecutionMode.fromString(preferences[ROOT_EXECUTION_MODE])
            }

    val customSuCommandFlow: Flow<String> =
            context.androidPermissionDataStore.data.map { preferences ->
                normalizeSuCommand(preferences[CUSTOM_SU_COMMAND])
            }

    /**
     * 保存首选权限级别
     * @param permissionLevel 要设置的权限级别
     */
    suspend fun savePreferredPermissionLevel(permissionLevel: AndroidPermissionLevel) {
        AppLogger.d(TAG, "Saving preferred permission level: $permissionLevel")
        context.androidPermissionDataStore.edit { preferences ->
            preferences[PREFERRED_PERMISSION_LEVEL] = permissionLevel.name
        }
    }

    suspend fun saveRootExecutionMode(mode: RootCommandExecutionMode) {
        AppLogger.d(TAG, "Saving root execution mode: $mode")
        context.androidPermissionDataStore.edit { preferences ->
            preferences[ROOT_EXECUTION_MODE] = mode.name
        }
    }

    suspend fun saveCustomSuCommand(command: String) {
        val normalized = normalizeSuCommand(command)
        AppLogger.d(TAG, "Saving custom su command: $normalized")
        context.androidPermissionDataStore.edit { preferences ->
            preferences[CUSTOM_SU_COMMAND] = normalized
        }
    }

    /**
     * 获取当前首选的权限级别 这是一个阻塞调用，应在非UI线程使用或谨慎使用
     * @return 当前配置的首选权限级别，如果未设置则返回null
     */
    fun getPreferredPermissionLevel(): AndroidPermissionLevel? {
        return runBlocking {
            try {
                preferredPermissionLevelFlow.first()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting preferred permission level", e)
                null
            }
        }
    }

    fun getRootExecutionMode(): RootCommandExecutionMode {
        return runBlocking {
            try {
                rootExecutionModeFlow.first()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting root execution mode", e)
                RootCommandExecutionMode.AUTO
            }
        }
    }

    fun getCustomSuCommand(): String {
        return runBlocking {
            try {
                customSuCommandFlow.first()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error getting custom su command", e)
                DEFAULT_SU_COMMAND
            }
        }
    }

    /**
     * 检查是否已设置权限级别
     * @return 是否已设置权限级别
     */
    fun isPermissionLevelSet(): Boolean {
        return runBlocking {
            try {
                preferredPermissionLevelFlow.first() != null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking if permission level is set", e)
                false
            }
        }
    }

    /** 重置权限级别（清除设置） */
    suspend fun resetPermissionLevel() {
        AppLogger.d(TAG, "Resetting permission level")
        context.androidPermissionDataStore.edit { preferences ->
            preferences.remove(PREFERRED_PERMISSION_LEVEL)
        }
    }

    suspend fun resetRootExecutionSettings() {
        AppLogger.d(TAG, "Resetting root execution settings")
        context.androidPermissionDataStore.edit { preferences ->
            preferences.remove(ROOT_EXECUTION_MODE)
            preferences.remove(CUSTOM_SU_COMMAND)
        }
    }
}
