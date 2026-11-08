package com.ai.assistance.operit.core.tools.permissions.authorization

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlin.math.min

/**
 * 用于管理内置无障碍服务提供者应用的安装和更新
 */
class AccessibilityProviderInstaller {
    companion object {
        private const val TAG = "AccessibilityProviderInstaller"
        private const val ACCESSIBILITY_PACKAGE_NAME = "com.ai.assistance.operit.provider"

        // 缓存版本信息
        private var cachedInstalledVersion: String? = null
        private var cachedBundledVersion: String? = null
        private var cachedUpdateNeeded: Boolean? = null
        private var lastCheckTime: Long = 0
        private const val CACHE_EXPIRE_TIME = 60 * 1000 // 缓存有效期1分钟

        /**
         * 获取内置无障碍服务APK版本信息
         */
        fun getBundledVersion(context: Context): String {
            if (cachedBundledVersion != null && !isCacheExpired()) {
                return cachedBundledVersion!!
            }

            try {
                val versionInfo = context.assets.open("accessibility_version.txt").use {
                    it.bufferedReader().readText().trim()
                }
                cachedBundledVersion = versionInfo
                return versionInfo
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取内置无障碍服务版本失败", e)
                val unknown = context.getString(R.string.accessibility_provider_unknown)
                cachedBundledVersion = unknown
                return unknown
            }
        }

        /**
         * 获取已安装的无障碍服务版本
         */
        fun getInstalledVersion(context: Context): String? {
            if (cachedInstalledVersion != null && !isCacheExpired()) {
                return cachedInstalledVersion
            }

            try {
                val packageManager = context.packageManager
                val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(ACCESSIBILITY_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(ACCESSIBILITY_PACKAGE_NAME, 0)
                }
                val versionName = packageInfo.versionName
                cachedInstalledVersion = versionName
                return versionName
            } catch (e: PackageManager.NameNotFoundException) {
                cachedInstalledVersion = null
                return null
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取已安装无障碍服务版本出错", e)
                cachedInstalledVersion = null
                return null
            }
        }

        /**
         * 检查是否需要更新无障碍服务
         */
        fun isUpdateNeeded(context: Context): Boolean {
            if (cachedUpdateNeeded != null && !isCacheExpired()) {
                return cachedUpdateNeeded!!
            }

            val installedVersion = getInstalledVersion(context)
            if (installedVersion == null) {
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false // Not installed, no need to update
            }

            val bundledVersion = getBundledVersion(context)
            val unknown = context.getString(R.string.accessibility_provider_unknown)
            if (bundledVersion == unknown) {
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false // Cannot determine bundled version, do not suggest update
            }

            try {
                val installed = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val bundled = bundledVersion.split(".").map { it.toIntOrNull() ?: 0 }

                val commonPartLength = min(installed.size, bundled.size)
                for (i in 0 until commonPartLength) {
                    if (bundled[i] > installed[i]) {
                        cachedUpdateNeeded = true
                        updateCacheTimestamp()
                        return true
                    }
                    if (bundled[i] < installed[i]) {
                        cachedUpdateNeeded = false
                        updateCacheTimestamp()
                        return false
                    }
                }

                if (bundled.size > installed.size) {
                    cachedUpdateNeeded = true
                    updateCacheTimestamp()
                    return true
                }
                
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false
            } catch (e: Exception) {
                AppLogger.e(TAG, "比较无障碍服务版本时出错", e)
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false
            }
        }

        /**
         * 触发内置无障碍服务的安装流程
         */
        fun launchInstall(context: Context) {
            UIHierarchyManager.launchProviderInstall(context)
            clearCache() // 清除缓存以在安装后刷新状态
        }

        private fun updateCacheTimestamp() {
            lastCheckTime = System.currentTimeMillis()
        }

        private fun isCacheExpired(): Boolean {
            return System.currentTimeMillis() - lastCheckTime > CACHE_EXPIRE_TIME
        }

        fun clearCache() {
            cachedInstalledVersion = null
            cachedBundledVersion = null
            cachedUpdateNeeded = null
            lastCheckTime = 0
            AppLogger.d(TAG, "无障碍服务版本缓存已清除")
        }
    }
} 
