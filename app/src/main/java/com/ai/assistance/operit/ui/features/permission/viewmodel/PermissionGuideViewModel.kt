package com.ai.assistance.operit.ui.features.permission.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionGuideViewModel : ViewModel() {
    
    private val TAG = "PermissionGuideVM"
    
    // UI状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 步骤枚举
    enum class Step {
        WELCOME,
        BASIC_PERMISSIONS,
        PERMISSION_LEVEL
    }
    
    // 初始化
    init {
        AppLogger.d(TAG, "ViewModel initialized")
    }
    
    // 检查所有权限
    fun checkPermissions(context: Context) {
        AppLogger.d(TAG, "Checking permissions")
        
        // 存储权限
        val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        // 悬浮窗权限
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // 低于Android 6.0不需要特别申请
        }
        
        // 电池优化豁免
        val hasBatteryOptimizationExemption = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // 低于Android 6.0不需要特别申请
        }
        
        // 位置权限
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || 
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        
        // 更新UI状态
        _uiState.update { currentState ->
            currentState.copy(
                hasStoragePermission = hasStoragePermission,
                hasOverlayPermission = hasOverlayPermission,
                hasBatteryOptimizationExemption = hasBatteryOptimizationExemption,
                hasLocationPermission = hasLocationPermission,
                allBasicPermissionsGranted = hasStoragePermission && 
                        hasOverlayPermission && 
                        hasBatteryOptimizationExemption && 
                        hasLocationPermission
            )
        }
        
        AppLogger.d(TAG, "Permissions checked: Storage=$hasStoragePermission, " +
                "Overlay=$hasOverlayPermission, " +
                "Battery=$hasBatteryOptimizationExemption, " +
                "Location=$hasLocationPermission")
    }
    
    // 更新当前步骤
    fun setCurrentStep(step: Step) {
        _uiState.update { it.copy(currentStep = step) }
        AppLogger.d(TAG, "Current step set to: $step")
    }
    
    // 选择权限级别
    fun selectPermissionLevel(level: AndroidPermissionLevel) {
        _uiState.update { it.copy(selectedPermissionLevel = level) }
        AppLogger.d(TAG, "Selected permission level: $level")
    }
    
    // 保存权限级别
    fun savePermissionLevel() {
        val level = _uiState.value.selectedPermissionLevel
        if (level != null) {
            AppLogger.d(TAG, "Saving permission level: $level")
            
            viewModelScope.launch {
                // 保存到偏好设置
                try {
                    androidPermissionPreferences.savePreferredPermissionLevel(level)
                    AppLogger.d(TAG, "Preferred permission level switched to: $level")

                    // 更新完成状态
                    _uiState.update { it.copy(isCompleted = true) }
                    
                    AppLogger.d(TAG, "Permission level saved, guide completed")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error saving permission level", e)
                }
            }
        } else {
            AppLogger.w(TAG, "Cannot save null permission level")
        }
    }
    
    // 更新位置权限状态
    fun updateLocationPermission(granted: Boolean) {
        _uiState.update { currentState ->
            val newState = currentState.copy(hasLocationPermission = granted)
            // 同时更新allBasicPermissionsGranted状态
            newState.copy(
                allBasicPermissionsGranted = newState.hasStoragePermission && 
                        newState.hasOverlayPermission && 
                        newState.hasBatteryOptimizationExemption && 
                        newState.hasLocationPermission
            )
        }
        AppLogger.d(TAG, "Location permission updated: $granted")
    }
    
    // UI状态数据类
    data class UiState(
        val currentStep: Step = Step.WELCOME,
        val hasStoragePermission: Boolean = false,
        val hasOverlayPermission: Boolean = false,
        val hasBatteryOptimizationExemption: Boolean = false,
        val hasLocationPermission: Boolean = false,
        val allBasicPermissionsGranted: Boolean = false,
        val selectedPermissionLevel: AndroidPermissionLevel? = null,
        val isCompleted: Boolean = false
    )
}
