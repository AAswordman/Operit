package com.ai.assistance.operit.core.tools.permissions.authorization

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import rikka.shizuku.Shizuku

internal data class ShizukuConnectionInfo(val uid: Int, val binder: IBinder)

/** Shizuku授权工具类 提供Shizuku权限检查和管理功能 */
class ShizukuAuthorizer {
    companion object {
        private const val TAG = "ShizukuAuthorizer"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private val mainHandler = Handler(Looper.getMainLooper())

        // 注册Shizuku权限请求监听器
        private var binderReceivedListenerRegistered = false
        private var permissionRequestListenerRegistered = false

        // 服务状态
        private var isServiceAvailable = false
        private var cachedConnection: ShizukuConnectionInfo? = null
        
        // 错误消息缓存
        private var lastServiceErrorMessage = ""
        private var lastPermissionErrorMessage = ""

        // 状态变更回调
        private val stateChangeListeners = mutableListOf<() -> Unit>()

        /**
         * 添加状态变更监听器
         * @param listener 监听器回调
         */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }

        /**
         * 移除状态变更监听器
         * @param listener 要移除的监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) { stateChangeListeners.remove(listener) }
        }

        /** 触发状态变更通知 */
        private fun notifyStateChanged() {
            // 确保在主线程中执行UI相关回调
            mainHandler.post {
                synchronized(stateChangeListeners) {
                    AppLogger.d(
                            TAG,
                            "Notifying ${stateChangeListeners.size} listeners about state change"
                    )
                    stateChangeListeners.forEach { it.invoke() }
                }
            }
        }

        private fun isSuiBackendAvailable(): Boolean {
            return try {
                if (Shizuku.pingBinder()) {
                    AppLogger.i(TAG, "检测到Sui/Shizuku后端可用（pingBinder）")
                    true
                } else {
                    val binder = Shizuku.getBinder()
                    val binderAlive = binder != null && binder.isBinderAlive
                    if (binderAlive) {
                        AppLogger.i(TAG, "检测到Sui/Shizuku后端可用（binder alive）")
                    }
                    binderAlive
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Sui后端检测失败: ${e.message}")
                false
            }
        }

        /**
         * 检查Shizuku是否已安装（兼容Sui后端）
         * @param context Android上下文
         * @return 是否已安装Shizuku或可用Sui后端
         */
        fun isShizukuInstalled(context: Context): Boolean {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                val versionName = packageInfo.versionName
                AppLogger.i(TAG, "检测到已安装Shizuku，版本: $versionName")
                true
            } catch (e: PackageManager.NameNotFoundException) {
                val suiBackendAvailable = isSuiBackendAvailable()
                if (suiBackendAvailable) {
                    AppLogger.i(TAG, "未检测到Shizuku应用，但检测到Sui后端可用")
                } else {
                    AppLogger.i(TAG, "未检测到已安装的Shizuku，也未检测到可用的Sui后端")
                }
                suiBackendAvailable
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查Shizuku/Sui可用性时出错", e)
                false
            }
        }

        /**
         * 获取最后一次服务检查的错误信息
         * @return 错误信息
         */
        fun getServiceErrorMessage(): String {
            return lastServiceErrorMessage
        }
        
        /**
         * 获取最后一次权限检查的错误信息
         * @return 错误信息
         */
        fun getPermissionErrorMessage(): String {
            return lastPermissionErrorMessage
        }

        private fun cacheConnection(uid: Int, binder: IBinder): ShizukuConnectionInfo {
            val connection = ShizukuConnectionInfo(uid, binder)
            cachedConnection = connection
            isServiceAvailable = true
            lastServiceErrorMessage = ""
            return connection
        }

        private fun clearConnection(errorMessage: String) {
            cachedConnection = null
            isServiceAvailable = false
            lastServiceErrorMessage = errorMessage
        }

        private fun getCachedConnection(): ShizukuConnectionInfo? {
            val connection = cachedConnection ?: return null
            if (!connection.binder.isBinderAlive) {
                clearConnection("Shizuku binder is not alive")
                return null
            }
            lastServiceErrorMessage = ""
            return connection
        }

        private fun isAllowedShizukuUid(uid: Int): Boolean {
            return uid == 0 || uid == 2000
        }

        internal fun getOrResolveShizukuConnection(): ShizukuConnectionInfo? {
            getCachedConnection()?.let { return it }

            try {
                val pingSucceeded =
                        try {
                            Shizuku.pingBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Shizuku pingBinder check failed", e)
                            clearConnection("Shizuku ping failed: ${e.message}")
                            return null
                        }

                if (pingSucceeded) {
                    AppLogger.d(TAG, "Shizuku pingBinder succeeded")
                }

                val binder =
                        try {
                            Shizuku.getBinder()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Binder check failed", e)
                            clearConnection("Failed to get binder: ${e.message}")
                            return null
                        }

                if (binder == null) {
                    clearConnection("Shizuku binder is null")
                    return null
                }

                if (!binder.isBinderAlive) {
                    clearConnection("Shizuku binder is not alive")
                    return null
                }

                if (!pingSucceeded) {
                    AppLogger.d(TAG, "Shizuku binder is alive")
                }

                val uid =
                        try {
                            Shizuku.getUid()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "UID check failed", e)
                            clearConnection("Failed to get UID: ${e.message}")
                            return null
                        }

                if (!isAllowedShizukuUid(uid)) {
                    clearConnection("Invalid Shizuku UID: $uid, expected 0 or 2000")
                    return null
                }

                return cacheConnection(uid, binder)
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Critical error checking Shizuku service", e)
                clearConnection("Critical error: ${e.message}")
                return null
            }
        }

        /**
         * 检查Shizuku服务是否正在运行
         * @return 服务是否运行
         */
        fun isShizukuServiceRunning(): Boolean {
            return getOrResolveShizukuConnection() != null
        }

        /**
         * 检查应用是否有Shizuku权限
         * @return 是否有权限
         */
        fun hasShizukuPermission(): Boolean {
            try {
                if (getOrResolveShizukuConnection() == null) {
                    lastPermissionErrorMessage = "Shizuku service not running: $lastServiceErrorMessage"
                    return false
                }

                // 适用于Shizuku 13.x版本的权限检查
                val result = Shizuku.checkSelfPermission()
                val granted = result == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    lastPermissionErrorMessage = ""
                    return true
                } else {
                    lastPermissionErrorMessage = "Shizuku permission not granted (code: $result)"
                    return false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking Shizuku permission", e)
                lastPermissionErrorMessage = "Error checking permission: ${e.message}"
                return false
            }
        }

        /**
         * 请求Shizuku权限
         * @param onResult 权限请求结果回调，仅返回是否授予权限
         */
        fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
            val serviceRunning = isShizukuServiceRunning()
            if (!serviceRunning) {
                AppLogger.e(TAG, "Cannot request permission: $lastServiceErrorMessage")
                onResult(false)
                return
            }

            val hasPermission = hasShizukuPermission()
            if (hasPermission) {
                AppLogger.d(TAG, "Permission already granted")
                onResult(true)
                notifyStateChanged()
                return
            }

            AppLogger.d(TAG, "Requesting Shizuku permission")

            // 移除之前的监听器避免重复
            try {
                if (permissionRequestListenerRegistered) {
                    Shizuku.removeRequestPermissionResultListener { _, _ -> }
                    permissionRequestListenerRegistered = false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing existing permission listener", e)
            }

            try {
                val requestCode = 100

                AppLogger.d(TAG, "Setting up permission result listener")

                Shizuku.addRequestPermissionResultListener { code, grantResult ->
                    AppLogger.d(TAG, "Permission result received: code=$code, result=$grantResult")
                    if (code == requestCode) {
                        val granted = grantResult == PackageManager.PERMISSION_GRANTED
                        AppLogger.d(TAG, "Shizuku permission request result: $granted")
                        onResult(granted)
                        if (granted) {
                            // 权限授予时触发状态变更通知
                            notifyStateChanged()
                        }

                        // 权限请求完成后移除监听器
                        try {
                            Shizuku.removeRequestPermissionResultListener { _, _ -> }
                            permissionRequestListenerRegistered = false
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error removing permission listener", e)
                        }
                    }
                }
                permissionRequestListenerRegistered = true

                // 请求权限
                AppLogger.d(TAG, "Calling Shizuku.requestPermission($requestCode)")
                Shizuku.requestPermission(requestCode)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error requesting Shizuku permission", e)
                onResult(false)
            }
        }

        /** 初始化Shizuku绑定 */
        fun initialize() {
            AppLogger.d(TAG, "Initializing Shizuku")

            // 重置服务状态
            isServiceAvailable = false
            cachedConnection = null
            lastServiceErrorMessage = ""
            lastPermissionErrorMessage = ""

            // 移除之前的监听器避免重复
            if (binderReceivedListenerRegistered) {
                try {
                    Shizuku.removeBinderReceivedListener {}
                    Shizuku.removeBinderDeadListener {}
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing binder listeners", e)
                }
                binderReceivedListenerRegistered = false
            }

            try {
                // 设置绑定接收监听器
                Shizuku.addBinderReceivedListener {
                    AppLogger.d(TAG, "Shizuku binder received")
                    isServiceAvailable = true
                    notifyStateChanged()

                    // 当收到binder时主动检查权限状态
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            AppLogger.d(TAG, "Checking permission after binder received: $hasPermission")
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error checking permission after binder received", e)
                        }
                    }
                }

                // 设置绑定断开监听器
                Shizuku.addBinderDeadListener {
                    AppLogger.d(TAG, "Shizuku binder dead")
                    isServiceAvailable = false
                    cachedConnection = null
                    notifyStateChanged()
                }

                binderReceivedListenerRegistered = true

                // 立即检查服务是否已经在运行
                val isRunning = isShizukuServiceRunning()
                AppLogger.d(TAG, "Initial Shizuku service status check: $isRunning")
                if (isRunning) {
                    // 如果服务正在运行，检查权限
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            AppLogger.d(TAG, "Initial permission check: $hasPermission")
                            notifyStateChanged()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error during initial permission check", e)
                        }
                    }
                } else {
                    // 如果服务未运行，500毫秒后再次检查以防初始化延迟
                    mainHandler.postDelayed(
                            {
                                val retryCheck = isShizukuServiceRunning()
                                AppLogger.d(TAG, "Delayed service status check: $retryCheck")
                                if (retryCheck) {
                                    notifyStateChanged()
                                }
                            },
                            500
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error initializing Shizuku", e)
            }
        }

        /**
         * 获取Shizuku启动说明
         * @param context Android上下文
         * @return Shizuku启动指南
         */
        fun getShizukuStartupInstructions(context: Context): String {
            return context.getString(R.string.shizuku_start_service_intro) +
                    context.getString(R.string.shizuku_step1_ensure_installed) +
                    context.getString(R.string.shizuku_step2_adb_command) +
                    "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                    context.getString(R.string.shizuku_or) +
                    context.getString(R.string.shizuku_step2_follow_instructions)
        }
    }
}
