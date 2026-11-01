package com.ai.assistance.operit.core.tools.permissions.authorization

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.core.content.FileProvider
import com.ai.assistance.operit.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 用于管理内置Shizuku应用的安装
 */
class ShizukuInstaller {
    companion object {
        private const val TAG = "ShizukuInstaller"
        private const val SHIZUKU_APK_FILENAME = "shizuku.apk"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        
        // 缓存版本信息，避免重复计算
        private var cachedInstalledVersion: String? = null
        private var cachedBundledVersion: String? = null
        private var cachedUpdateNeeded: Boolean? = null
        private var lastCheckTime: Long = 0
        private const val CACHE_EXPIRE_TIME = 60 * 1000 // 缓存有效期1分钟
        
        /**
         * 从assets目录复制Shizuku APK到应用私有目录
         * @param context Android上下文
         * @return 返回目标APK文件对象，如果失败则返回null
         */
        fun extractApkFromAssets(context: Context): File? {
            val apkFile = File(context.cacheDir, SHIZUKU_APK_FILENAME)
            
            try {
                context.assets.open(SHIZUKU_APK_FILENAME).use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(4 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    }
                }
                return apkFile
            } catch (e: IOException) {
                AppLogger.e(TAG, "Failed to extract Shizuku APK from assets", e)
                return null
            }
        }
        
        /**
         * 检查应用私有目录中是否存在提取的APK文件
         * @param context Android上下文
         * @return 是否存在APK文件
         */
        fun isApkExtracted(context: Context): Boolean {
            val apkFile = File(context.cacheDir, SHIZUKU_APK_FILENAME)
            return apkFile.exists() && apkFile.length() > 0
        }
        
        /**
         * 安装或更新内置的Shizuku APK
         * @param context Android上下文
         * @return 是否成功启动安装界面
         */
        fun installBundledShizuku(context: Context): Boolean {
            try {
                // 记录是安装还是更新
                val isUpdate = ShizukuAuthorizer.isShizukuInstalled(context)
                val action = if (isUpdate) context.getString(R.string.shizuku_install_update) else context.getString(R.string.shizuku_install_install)

                AppLogger.d(TAG, "开始${action}内置Shizuku")

                // 从assets目录提取APK
                val apkFile = extractApkFromAssets(context)
                if (apkFile == null) {
                    AppLogger.e(TAG, "提取APK失败")
                    return false
                }

                AppLogger.d(TAG, "APK提取成功: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节")

                // 生成APK的URI，考虑文件提供者权限
                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }

                AppLogger.d(TAG, "生成APK URI: $apkUri")

                // 创建安装意图
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }

                AppLogger.d(TAG, "启动${action}界面")

                // 启动安装界面
                context.startActivity(installIntent)

                // 清除缓存，强制下次检测重新计算
                clearCache()

                return true
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to install bundled Shizuku", e)
                return false
            }
        }
        
        /**
         * 获取内置Shizuku APK版本信息
         * @param context Android上下文
         * @return 内置APK的版本名称，如果无法获取则返回"未知"
         */
        fun getBundledShizukuVersion(context: Context): String {
            // 优先使用缓存
            if (cachedBundledVersion != null && !isCacheExpired()) {
                AppLogger.i(TAG, "从缓存获取内置Shizuku版本: $cachedBundledVersion")
                return cachedBundledVersion!!
            }

            try {
                // 从assets读取版本信息文件
                val versionInfo = context.assets.open("shizuku_version.txt").use { inputStream ->
                    inputStream.bufferedReader().readText().trim()
                }
                AppLogger.i(TAG, "获取内置Shizuku版本: $versionInfo")
                cachedBundledVersion = versionInfo
                return versionInfo
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取内置Shizuku版本失败", e)
                val unknown = context.getString(R.string.shizuku_install_unknown)
                cachedBundledVersion = unknown
                return unknown
            }
        }
        
        /**
         * 获取已安装的Shizuku版本
         * @param context Android上下文
         * @return 已安装的Shizuku版本名称，如果未安装则返回null
         */
        fun getInstalledShizukuVersion(context: Context): String? {
            // 优先使用缓存
            if (cachedInstalledVersion != null && !isCacheExpired()) {
                AppLogger.i(TAG, "从缓存获取已安装Shizuku版本: $cachedInstalledVersion")
                return cachedInstalledVersion
            }
            
            try {
                val packageManager = context.packageManager
                val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                }
                val versionName = packageInfo.versionName
                AppLogger.i(TAG, "获取已安装Shizuku版本: $versionName")
                cachedInstalledVersion = versionName
                return versionName
            } catch (e: PackageManager.NameNotFoundException) {
                // 未安装Shizuku
                AppLogger.i(TAG, "未检测到已安装的Shizuku")
                cachedInstalledVersion = null
                return null
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取已安装Shizuku版本出错", e)
                cachedInstalledVersion = null
                return null
            }
        }
        
        /**
         * 检查是否需要更新Shizuku
         * @param context Android上下文
         * @return 如果需要更新返回true，否则返回false
         */
        fun isShizukuUpdateNeeded(context: Context): Boolean {
            // 优先使用缓存
            if (cachedUpdateNeeded != null && !isCacheExpired()) {
                AppLogger.d(TAG, "从缓存获取Shizuku更新状态: $cachedUpdateNeeded")
                return cachedUpdateNeeded!!
            }

            AppLogger.d(TAG, "开始检查Shizuku是否需要更新...")

            val installedVersion = getInstalledShizukuVersion(context)
            if (installedVersion == null) {
                AppLogger.d(TAG, "未安装Shizuku，不需要更新")
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false
            }

            val bundledVersion = getBundledShizukuVersion(context)
            val unknown = context.getString(R.string.shizuku_install_unknown)
            if (bundledVersion == unknown) {
                AppLogger.d(TAG, "无法获取内置版本信息，不建议更新")
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false
            }

            try {
                // 提取主版本号部分 (例如 "13.5.0.r1234" -> "13.5.0")
                val installedMainVersion = extractMainVersion(installedVersion)
                val bundledMainVersion = extractMainVersion(bundledVersion)
                // 将版本号分割为数字数组
                val installed = installedMainVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val bundled = bundledMainVersion.split(".").map { it.toIntOrNull() ?: 0 }

                // 比较主要版本号
                for (i in 0 until minOf(installed.size, bundled.size)) {
                    if (bundled[i] > installed[i]) {
                        AppLogger.d(TAG, "需要更新: 内置版本 ${bundled[i]} > 已安装版本 ${installed[i]} (位置: $i)")
                        cachedUpdateNeeded = true
                        updateCacheTimestamp()
                        return true
                    }
                    if (bundled[i] < installed[i]) {
                        AppLogger.d(TAG, "不需要更新: 内置版本 ${bundled[i]} < 已安装版本 ${installed[i]} (位置: $i)")
                        cachedUpdateNeeded = false
                        updateCacheTimestamp()
                        return false
                    }
                }

                // 如果前面的版本号都相同，但bundled有更多的版本号段，则认为需要更新
                val updateNeeded = bundled.size > installed.size
                cachedUpdateNeeded = updateNeeded
                updateCacheTimestamp()
                return updateNeeded
            } catch (e: Exception) {
                AppLogger.e(TAG, "比较Shizuku版本时出错", e)
                cachedUpdateNeeded = false
                updateCacheTimestamp()
                return false
            }
        }
        
        /**
         * 从完整版本号中提取主版本号部分
         * 例如: "13.5.0.r1234" -> "13.5.0"
         */
        private fun extractMainVersion(version: String): String {
            // 正则表达式匹配主版本号部分 (x.y.z)
            val mainVersionRegex = """^(\d+)\.(\d+)\.(\d+)""".toRegex()
            val matchResult = mainVersionRegex.find(version)
            
            val result = matchResult?.value ?: version.split("-", ".", "+", " ").take(3).joinToString(".")
            return result
        }
        
        /**
         * 更新缓存时间戳
         */
        private fun updateCacheTimestamp() {
            lastCheckTime = System.currentTimeMillis()
        }
        
        /**
         * 检查缓存是否已过期
         */
        private fun isCacheExpired(): Boolean {
            return System.currentTimeMillis() - lastCheckTime > CACHE_EXPIRE_TIME
        }
        
        /**
         * 清除所有缓存
         */
        fun clearCache() {
            cachedInstalledVersion = null
            cachedBundledVersion = null
            cachedUpdateNeeded = null
            lastCheckTime = 0
            AppLogger.d(TAG, "Shizuku版本缓存已清除")
        }
    }
} 
