package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.AssetCopyUtils
import java.io.File
import java.io.InputStream

/** APK编辑器 - 提供链式调用API 支持修改包名、应用名、图标和重新签名等操作 */
class ApkEditor
private constructor(
        private val context: Context,
        private val apkFile: File,
        private val apkReverseEngineer: ApkReverseEngineer
) {
    companion object {
        private const val TAG = "ApkEditor"

        /**
         * 从资产文件创建APK编辑器
         * @param context 上下文
         * @param assetPath 资产路径
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromAsset(context: Context, assetPath: String): ApkEditor {
            val fileName = assetPath.substringAfterLast('/')
            val outputFile = File(context.cacheDir, "apk_editor_$fileName")
            val apkFile = AssetCopyUtils.copyAssetToFile(context, assetPath, outputFile, overwrite = true)
            val apkReverseEngineer = ApkReverseEngineer(context)
            return ApkEditor(context, apkFile, apkReverseEngineer)
        }

        /**
         * 从文件创建APK编辑器
         * @param context 上下文
         * @param apkFile APK文件
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromFile(context: Context, apkFile: File): ApkEditor {
            val apkReverseEngineer = ApkReverseEngineer(context)
            return ApkEditor(context, apkFile, apkReverseEngineer)
        }

        /**
         * 从文件路径创建APK编辑器
         * @param context 上下文
         * @param apkFilePath APK文件路径
         * @return APK编辑器实例
         */
        @JvmStatic
        fun fromPath(context: Context, apkFilePath: String): ApkEditor {
            val apkFile = File(apkFilePath)
            return fromFile(context, apkFile)
        }

        /**
         * 复制资产文件到缓存目录
         * @param context 上下文
         * @param assetPath 资产路径
         * @return 缓存文件
         */
    }

    private var newPackageName: String? = null
    private var newAppName: String? = null
    private var newVersionName: String? = null
    private var newVersionCode: String? = null
    private var newIconBitmap: Bitmap? = null

    private var keyStoreFile: File? = null
    private var keyStorePassword: String? = null
    private var keyAlias: String? = null
    private var keyPassword: String? = null

    private var outputFile: File? = null

    /**
     * 修改包名
     * @param packageName 新包名
     * @return 当前APK编辑器实例
     */
    fun changePackageName(packageName: String): ApkEditor {
        this.newPackageName = packageName
        return this
    }

    /**
     * 修改应用名称
     * @param appName 新应用名称
     * @return 当前APK编辑器实例
     */
    fun changeAppName(appName: String): ApkEditor {
        this.newAppName = appName
        return this
    }

    /**
     * 修改版本名
     * @param versionName 新版本名
     * @return 当前APK编辑器实例
     */
    fun changeVersionName(versionName: String): ApkEditor {
        this.newVersionName = versionName
        return this
    }

    /**
     * 修改版本号
     * @param versionCode 新版本号
     * @return 当前APK编辑器实例
     */
    fun changeVersionCode(versionCode: String): ApkEditor {
        this.newVersionCode = versionCode
        return this
    }

    /**
     * 更改图标（从位图）
     * @param iconBitmap 图标位图
     * @return 当前APK编辑器实例
     */
    fun changeIcon(iconBitmap: Bitmap): ApkEditor {
        this.newIconBitmap = iconBitmap
        return this
    }

    /**
     * 更改图标（从输入流）
     * @param iconInputStream 图标输入流
     * @return 当前APK编辑器实例
     */
    fun changeIcon(iconInputStream: InputStream): ApkEditor {
        val bitmap = BitmapFactory.decodeStream(iconInputStream)
        return changeIcon(bitmap)
    }

    /**
     * 更改图标（从资产文件）
     * @param iconAssetPath 图标资产路径
     * @return 当前APK编辑器实例
     */
    fun changeIconFromAsset(iconAssetPath: String): ApkEditor {
        context.assets.open(iconAssetPath).use { input ->
            return changeIcon(input)
        }
    }

    /**
     * 设置签名信息
     * @param keyStoreFile 密钥库文件
     * @param keyStorePassword 密钥库密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @return 当前APK编辑器实例
     */
    fun withSignature(
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String
    ): ApkEditor {
        this.keyStoreFile = keyStoreFile
        this.keyStorePassword = keyStorePassword
        this.keyAlias = keyAlias
        this.keyPassword = keyPassword
        return this
    }

    /**
     * 设置输出文件
     * @param outputFile 输出文件
     * @return 当前APK编辑器实例
     */
    fun setOutput(outputFile: File): ApkEditor {
        this.outputFile = outputFile
        return this
    }

    /**
     * 设置输出文件路径
     * @param outputPath 输出文件路径
     * @return 当前APK编辑器实例
     */
    fun setOutput(outputPath: String): ApkEditor {
        return setOutput(File(outputPath))
    }

    /**
     * 仅替换Web内容并更新清单信息的快速打包（不落地解压）
     * @param webContentDir 网页内容目录
     * @return 重新打包后的APK文件（未签名）
     */
    fun repackWithWebContent(webContentDir: File): File {
        if (!webContentDir.exists() || !webContentDir.isDirectory) {
            throw IllegalArgumentException("webContentDir is missing or not a directory: ${webContentDir.absolutePath}")
        }

        val unsignedOutputFile =
                if (outputFile != null) {
                    outputFile!!
                } else {
                    File(context.cacheDir, "unsigned_${apkFile.name}")
                }

        if (!apkReverseEngineer.repackageApkWithWebContent(
                        apkFile,
                        unsignedOutputFile,
                        webContentDir,
                        newPackageName,
                        newAppName,
                        newVersionName,
                        newVersionCode,
                        newIconBitmap
                )
        ) {
            throw RuntimeException(context.getString(R.string.apk_editor_repack_failed))
        }

        return unsignedOutputFile
    }

    /**
     * 仅替换Web内容并更新清单信息后重新打包并签名APK
     * @param webContentDir 网页内容目录
     * @return 签名后的APK文件
     */
    fun repackAndSignWithWebContent(webContentDir: File): File {
        val unsignedApk = repackWithWebContent(webContentDir)

        AppLogger.d(TAG, "未签名APK生成成功: ${unsignedApk.absolutePath}, 文件大小: ${unsignedApk.length()}")

        if (!unsignedApk.exists() || unsignedApk.length() == 0L) {
            throw RuntimeException(context.getString(R.string.apk_editor_unsigned_apk_not_found, unsignedApk.absolutePath))
        }

        if (keyStoreFile == null ||
                        keyStorePassword == null ||
                        keyAlias == null ||
                        keyPassword == null
        ) {
            throw IllegalStateException(context.getString(R.string.apk_editor_signature_incomplete))
        }

        val signedOutputFile = if (outputFile != null) {
            File(unsignedApk.parentFile, "to_sign_${System.currentTimeMillis()}_${unsignedApk.name}")
        } else {
            File(context.cacheDir, "signed_${apkFile.name}")
        }

        AppLogger.d(TAG, "开始签名APK，输入: ${unsignedApk.absolutePath}, 输出: ${signedOutputFile.absolutePath}")

        val signResult = apkReverseEngineer.signApk(
                unsignedApk,
                keyStoreFile!!,
                keyStorePassword!!,
                keyAlias!!,
                keyPassword!!,
                signedOutputFile
        )

        if (!signResult.first) {
            val errorMessage = signResult.second ?: context.getString(R.string.apk_editor_unknown_sign_error)
            throw RuntimeException(context.getString(R.string.apk_editor_sign_failed, errorMessage))
        }

        val finalOutputFile = if (outputFile != null && signedOutputFile.exists()) {
            outputFile!!.parentFile?.mkdirs()

            if (outputFile!!.exists()) {
                outputFile!!.delete()
            }

            signedOutputFile.inputStream().use { input ->
                outputFile!!.outputStream().use { output -> input.copyTo(output) }
            }

            signedOutputFile.delete()

            AppLogger.d(TAG, "已将签名后的APK从临时文件复制到指定输出位置: ${outputFile!!.absolutePath}")
            outputFile!!
        } else {
            signedOutputFile
        }

        AppLogger.d(TAG, "APK签名完成: ${finalOutputFile.absolutePath}, 文件大小: ${finalOutputFile.length()}字节")
        return finalOutputFile
    }

    /** 清理临时文件 */
    fun cleanup() {
        newIconBitmap?.recycle()
        newIconBitmap = null
    }
}
