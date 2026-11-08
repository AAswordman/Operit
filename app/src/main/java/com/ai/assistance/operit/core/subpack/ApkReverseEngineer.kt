package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import com.android.apksig.ApkSigner
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import pxb.android.axml.Axml
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter

/** APK逆向工程工具类 使用Android标准库和专业库实现APK的修改和重新打包 */
class ApkReverseEngineer(private val context: Context) {
    companion object {
        private const val TAG = "ApkReverseEngineer"
        private const val ANDROID_MANIFEST = "AndroidManifest.xml"
    }

    /**
     * 替换AXML中所有引用旧包名的属性
     * @param axml AXML数据结构
     * @param oldPackageName 旧包名
     * @param newPackageName 新包名
     */
    private fun replacePackageReferences(
            axml: Axml,
            oldPackageName: String,
            newPackageName: String
    ) {
        // 递归处理所有节点
        fun processNode(node: Axml.Node) {
            // 处理当前节点的属性
            for (attr in node.attrs) {
                if (attr.value is String) {
                    val strValue = attr.value as String

                    // 特殊情况：保留对MainActivity的引用不变
                    if (strValue == "$oldPackageName.MainActivity" ||
                                    strValue.endsWith(".$oldPackageName.MainActivity")
                    ) {
                        AppLogger.d(TAG, "保留MainActivity引用不变: $strValue")
                        continue
                    }

                    // 替换所有其他引用旧包名的情况
                    if (strValue.contains(oldPackageName)) {
                        val newValue = strValue.replace(oldPackageName, newPackageName)
                        AppLogger.d(TAG, "替换包名引用: $strValue -> $newValue")
                        attr.value = newValue
                    }
                }
            }

            // 递归处理子节点
            for (childNode in node.children) {
                processNode(childNode)
            }
        }

        // 处理所有顶级节点
        for (node in axml.firsts) {
            processNode(node)
        }
    }

    /** 按指定尺寸缩放位图 */
    private fun scaleBitmap(source: Bitmap, size: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, size, size, true)
    }

    /**
     * 仅替换Web内容与清单信息的快速打包（不落地解压）
     * @param inputApk 原始APK
     * @param outputApk 输出APK
     * @param webContentDir 新的网页内容目录
     * @param newPackageName 新包名（可选）
     * @param newAppName 新应用名（可选）
     * @param newVersionName 新版本名（可选）
     * @param newVersionCode 新版本号（可选）
     * @return 是否打包成功
     */
    fun repackageApkWithWebContent(
            inputApk: File,
            outputApk: File,
            webContentDir: File,
            newPackageName: String?,
            newAppName: String?,
            newVersionName: String?,
            newVersionCode: String?,
            newIconBitmap: Bitmap?
    ): Boolean {
        try {
            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            val tempUnalignedApk =
                    File(outputApk.parentFile, "${outputApk.nameWithoutExtension}_unaligned.apk")
            if (tempUnalignedApk.exists()) tempUnalignedApk.delete()

            ZipArchiveOutputStream(FileOutputStream(tempUnalignedApk)).use { zipOut ->
                ZipFile(inputApk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name

                        // 跳过旧签名
                        if (entryName.startsWith("META-INF/")) {
                            continue
                        }

                        // 跳过旧的web内容
                        if (entryName.startsWith("assets/flutter_assets/assets/web_content/")) {
                            continue
                        }

                        if (newIconBitmap != null && shouldReplaceIconEntry(entryName)) {
                            val iconBytes = buildIconBytes(newIconBitmap, entryName)
                            writeBytesEntry(zipOut, entryName, iconBytes, entry.time, entry.method)
                            continue
                        }

                        if (entryName == ANDROID_MANIFEST) {
                            val originalBytes = zip.getInputStream(entry).use { it.readBytes() }
                            val modifiedBytes =
                                    modifyManifestBytes(
                                            originalBytes,
                                            newPackageName,
                                            newAppName,
                                            newVersionName,
                                            newVersionCode
                                    )
                            writeBytesEntry(zipOut, entryName, modifiedBytes, entry.time, entry.method)
                            continue
                        }

                        copyZipEntry(zip, entry, zipOut)
                    }
                }

                addWebContentToZip(zipOut, webContentDir)
            }

            AppLogger.d(TAG, "APK快速打包完成，准备进行zipalign对齐: ${tempUnalignedApk.absolutePath}")

            val aligned = zipalign(tempUnalignedApk, outputApk, 4)
            tempUnalignedApk.delete()

            if (!aligned) {
                AppLogger.e(TAG, "APK对齐失败")
                return false
            }

            AppLogger.d(TAG, "APK快速打包成功并完成4字节对齐: ${outputApk.absolutePath}")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "APK快速打包失败", e)
            return false
        }
    }

    /**
     * 对APK文件进行zipalign处理
     * @param inputApk 输入的APK文件
     * @param outputApk 输出的APK文件
     * @param alignment 对齐字节数（通常为4）
     * @return 是否对齐成功
     */
    fun zipalign(inputApk: File, outputApk: File, alignment: Int): Boolean {
        try {
            if (outputApk.exists()) outputApk.delete()

            AppLogger.d(
                    TAG,
                    "使用zipalign-java库进行${alignment}字节对齐: ${inputApk.absolutePath} -> ${outputApk.absolutePath}"
            )

            // 使用zipalign-java库进行对齐
            val rafIn = RandomAccessFile(inputApk, "r")
            val fos = FileOutputStream(outputApk)

            // 对.so文件使用16KB边界对齐，其他文件使用4字节对齐
            com.iyxan23.zipalignjava.ZipAlign.alignZip(rafIn, fos, alignment, 4 * 1024)

            rafIn.close()
            fos.close()

            AppLogger.d(TAG, "APK对齐完成")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "zipalign处理失败", e)
            return false
        }
    }

    /**
     * 判断文件是否应该不压缩存储
     * @param filePath 文件路径
     * @return 如果应该不压缩存储返回true，否则返回false
     */
    private fun shouldStoreWithoutCompression(filePath: String): Boolean {
        // 检查文件名或扩展名
        return when {
            // 关键的APK文件
            filePath.endsWith("/AndroidManifest.xml") || filePath == "AndroidManifest.xml" -> true
            filePath.endsWith("/resources.arsc") || filePath == "resources.arsc" -> true
            filePath.endsWith(".dex") -> true

            // META-INF目录中的签名文件
            filePath.startsWith("META-INF/") &&
                    (filePath.endsWith(".SF") ||
                            filePath.endsWith(".RSA") ||
                            filePath.endsWith(".DSA") ||
                            filePath == "META-INF/MANIFEST.MF") -> true

            // 默认压缩
            else -> false
        }
    }

    private fun calculateBytesCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    private fun shouldReplaceIconEntry(entryName: String): Boolean {
        val lowerName = entryName.lowercase()
        val fileName = lowerName.substringAfterLast('/')

        if (!lowerName.startsWith("res/")) {
            return false
        }

        val knownIconNames = setOf("yn.png", "n3.png", "9w.png", "fs.png", "rj.png", "o-.png")
        if (knownIconNames.contains(fileName)) {
            return true
        }

        val isIconFile =
                fileName.startsWith("ic_launcher") ||
                        fileName.startsWith("ic_launcher_round") ||
                        fileName.startsWith("ic_launcher_foreground") ||
                        fileName.startsWith("ic_launcher_background")
        if (!isIconFile) {
            return false
        }

        return lowerName.contains("/mipmap") || lowerName.contains("/drawable")
    }

    private fun buildIconBytes(sourceBitmap: Bitmap, entryName: String): ByteArray {
        val size = determineIconSizeFromPath(entryName)
        val scaled = scaleBitmap(sourceBitmap, size)
        val format =
                when (entryName.substringAfterLast('.').lowercase()) {
                    "webp" -> Bitmap.CompressFormat.WEBP
                    "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                    else -> Bitmap.CompressFormat.PNG
                }

        val output = ByteArrayOutputStream()
        scaled.compress(format, 100, output)
        return output.toByteArray()
    }

    private fun determineIconSizeFromPath(entryPath: String): Int {
        val lowerPath = entryPath.lowercase()
        return when {
            lowerPath.contains("xxxhdpi") -> 192
            lowerPath.contains("xxhdpi") -> 144
            lowerPath.contains("xhdpi") -> 96
            lowerPath.contains("hdpi") -> 72
            lowerPath.contains("mdpi") -> 48
            else -> 96
        }
    }

    private fun calculateStreamCrcAndSize(input: InputStream): Pair<Long, Long> {
        val crc = CRC32()
        var size = 0L
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            crc.update(buffer, 0, bytesRead)
            size += bytesRead
        }
        return Pair(crc.value, size)
    }

    private fun writeBytesEntry(
            zipOut: ZipArchiveOutputStream,
            entryName: String,
            data: ByteArray,
            time: Long,
            preferredMethod: Int? = null
    ) {
        val entry = ZipArchiveEntry(entryName)
        entry.time = time

        val methodToUse =
                when {
                    preferredMethod != null && preferredMethod != -1 -> preferredMethod
                    shouldStoreWithoutCompression(entryName) -> ZipArchiveEntry.STORED
                    else -> ZipArchiveEntry.DEFLATED
                }

        if (methodToUse == ZipArchiveEntry.STORED) {
            entry.method = ZipArchiveEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = calculateBytesCrc32(data)
        } else {
            entry.method = ZipArchiveEntry.DEFLATED
        }

        zipOut.putArchiveEntry(entry)
        zipOut.write(data)
        zipOut.closeArchiveEntry()
    }

    private fun copyZipEntry(
            zip: ZipFile,
            entry: java.util.zip.ZipEntry,
            zipOut: ZipArchiveOutputStream
    ) {
        val entryName = entry.name
        val outEntry = ZipArchiveEntry(entryName)
        outEntry.time = entry.time

        val originalMethod = entry.method
        if (originalMethod == java.util.zip.ZipEntry.STORED) {
            outEntry.method = ZipArchiveEntry.STORED
            if (entry.size >= 0 && entry.crc >= 0) {
                outEntry.size = entry.size
                outEntry.compressedSize = entry.size
                outEntry.crc = entry.crc
            } else {
                val (crc, size) =
                        zip.getInputStream(entry).use { input -> calculateStreamCrcAndSize(input) }
                outEntry.size = size
                outEntry.compressedSize = size
                outEntry.crc = crc
            }
        } else {
            outEntry.method = ZipArchiveEntry.DEFLATED
        }

        zipOut.putArchiveEntry(outEntry)
        zip.getInputStream(entry).use { input -> IOUtils.copy(input, zipOut) }
        zipOut.closeArchiveEntry()
    }

    private fun addWebContentToZip(zipOut: ZipArchiveOutputStream, webContentDir: File) {
        if (!webContentDir.exists() || !webContentDir.isDirectory) {
            AppLogger.w(TAG, "web内容目录不存在或不是目录: ${webContentDir.absolutePath}")
            return
        }

        val basePath = webContentDir.absolutePath
        val files =
                webContentDir.walkTopDown().filter { it.isFile }.sortedBy { it.absolutePath }

        for (file in files) {
            val relativePath =
                    file.absolutePath.substring(basePath.length + 1).replace("\\", "/")
            val entryName = "assets/flutter_assets/assets/web_content/$relativePath"

            val entry = ZipArchiveEntry(entryName)
            entry.method = ZipArchiveEntry.DEFLATED
            entry.time = file.lastModified()

            zipOut.putArchiveEntry(entry)
            FileInputStream(file).use { input -> IOUtils.copy(input, zipOut) }
            zipOut.closeArchiveEntry()
        }
    }

    private fun modifyManifestBytes(
            manifestBytes: ByteArray,
            newPackageName: String?,
            newAppName: String?,
            newVersionName: String?,
            newVersionCode: String?
    ): ByteArray {
        try {
            val reader = AxmlReader(manifestBytes)
            val axml = Axml()
            reader.accept(axml)

            val manifestNode = axml.firsts.firstOrNull { it.name == "manifest" } ?: return manifestBytes

            var oldPackageName: String? = null

            if (newPackageName != null) {
                var packageAttr = manifestNode.attrs.find { it.name == "package" }
                if (packageAttr != null) {
                    oldPackageName = packageAttr.value as? String
                    packageAttr.value = newPackageName
                } else {
                    packageAttr = Axml.Node.Attr().apply {
                        name = "package"
                        ns = null
                        resourceId = -1
                        type = AxmlVisitor.TYPE_STRING
                        value = newPackageName
                    }
                    manifestNode.attrs.add(packageAttr)
                }

                if (!oldPackageName.isNullOrEmpty()) {
                    replacePackageReferences(axml, oldPackageName!!, newPackageName)
                }
            }

            val androidNs =
                    manifestNode.attrs.find { it.name == "versionCode" }?.ns
                            ?: "http://schemas.android.com/apk/res/android"

            if (newVersionName != null) {
                var versionNameAttr =
                        manifestNode.attrs.find { it.name == "versionName" && it.ns == androidNs }
                if (versionNameAttr != null) {
                    versionNameAttr.value = newVersionName
                } else {
                    versionNameAttr = Axml.Node.Attr().apply {
                        name = "versionName"
                        ns = androidNs
                        resourceId = -1
                        type = AxmlVisitor.TYPE_STRING
                        value = newVersionName
                    }
                    manifestNode.attrs.add(versionNameAttr)
                }
            }

            if (newVersionCode != null) {
                var versionCodeAttr =
                        manifestNode.attrs.find { it.name == "versionCode" && it.ns == androidNs }
                if (versionCodeAttr != null) {
                    versionCodeAttr.value = newVersionCode.toIntOrNull() ?: 1
                    versionCodeAttr.type = AxmlVisitor.TYPE_INT_HEX
                } else {
                    versionCodeAttr = Axml.Node.Attr().apply {
                        name = "versionCode"
                        ns = androidNs
                        resourceId = -1
                        type = AxmlVisitor.TYPE_INT_HEX
                        value = newVersionCode.toIntOrNull() ?: 1
                    }
                    manifestNode.attrs.add(versionCodeAttr)
                }
            }

            if (newAppName != null) {
                for (childNode in manifestNode.children) {
                    if (childNode.name == "application") {
                        var labelAttr: Axml.Node.Attr? = null
                        for (attr in childNode.attrs) {
                            if (attr.name == "label" &&
                                            (attr.ns == null || attr.ns == androidNs)
                            ) {
                                labelAttr = attr
                                break
                            }
                        }

                        if (labelAttr != null) {
                            labelAttr.value = newAppName
                        } else {
                            val attr = Axml.Node.Attr().apply {
                                name = "label"
                                ns = androidNs
                                resourceId = -1
                                type = AxmlVisitor.TYPE_STRING
                                value = newAppName
                            }
                            childNode.attrs.add(attr)
                        }
                        break
                    }
                }
            }

            val writer = AxmlWriter()
            axml.accept(writer)
            return writer.toByteArray()
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改AndroidManifest字节失败: ${e.message}", e)
            return manifestBytes
        }
    }

    /**
     * 重新签名APK
     * @param unsignedApk 未签名的APK文件
     * @param keyStoreFile 密钥库文件
     * @param keyStorePassword 密钥库密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @param outputApk 签名后的APK文件
     * @return 包含签名结果和错误消息的Pair，成功时第二个值为null
     */
    fun signApk(
            unsignedApk: File,
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Pair<Boolean, String?> {
        try {
            if (!unsignedApk.exists()) {
                val message = context.getString(R.string.apk_unsigned_not_exist, unsignedApk.absolutePath)
                AppLogger.e(TAG, message)
                return Pair(false, message)
            }

            if (!keyStoreFile.exists()) {
                val message = context.getString(R.string.apk_keystore_not_exist, keyStoreFile.absolutePath)
                AppLogger.e(TAG, message)
                return Pair(false, message)
            }

            AppLogger.d(TAG, "开始签名APK，使用密钥: ${keyStoreFile.absolutePath}, 别名: $keyAlias")
            AppLogger.d(TAG, "密钥文件大小: ${keyStoreFile.length()}字节")

            if (outputApk.exists()) outputApk.delete()
            outputApk.parentFile?.mkdirs()

            // 首先尝试使用PKCS12格式加载密钥库
            val pkcs12Result =
                    trySignWithKeyStoreType(
                            unsignedApk,
                            keyStoreFile,
                            keyStorePassword,
                            keyAlias,
                            keyPassword,
                            outputApk,
                            "PKCS12"
                    )
            if (pkcs12Result.first) {
                return Pair(true, null)
            }

            // 如果PKCS12失败，尝试使用JKS格式
            val jksResult =
                    trySignWithKeyStoreType(
                            unsignedApk,
                            keyStoreFile,
                            keyStorePassword,
                            keyAlias,
                            keyPassword,
                            outputApk,
                            "JKS"
                    )
            if (jksResult.first) {
                return Pair(true, null)
            }

            val errorMessage =
                    context.getString(R.string.apk_keystore_load_failed_both, pkcs12Result.second ?: "", jksResult.second ?: "")
            AppLogger.e(TAG, errorMessage)
            return Pair(false, errorMessage)
        } catch (e: Exception) {
            val errorMessage = context.getString(R.string.apk_sign_failed, e.message ?: "")
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }

    /** 尝试使用指定格式的密钥库进行签名 */
    private fun trySignWithKeyStoreType(
            unsignedApk: File,
            keyStoreFile: File,
            keyStorePassword: String,
            keyAlias: String,
            keyPassword: String,
            outputApk: File,
            keyStoreType: String
    ): Pair<Boolean, String?> {
        try {
            AppLogger.d(TAG, "尝试以$keyStoreType 格式加载密钥库")

            // 使用KeyStoreHelper获取密钥库实例
            val keyStore = KeyStoreHelper.getKeyStoreInstance(keyStoreType)
            if (keyStore == null) {
                val errorMessage = context.getString(R.string.apk_get_keystore_instance_failed, keyStoreType)
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            FileInputStream(keyStoreFile).use { input ->
                try {
                    keyStore.load(input, keyStorePassword.toCharArray())
                    AppLogger.d(TAG, "成功以$keyStoreType 格式加载密钥库")
                } catch (e: Exception) {
                    val errorMessage = context.getString(R.string.apk_load_keystore_failed, keyStoreType, e.message ?: "")
                    AppLogger.e(TAG, errorMessage)
                    return Pair(false, errorMessage)
                }

                // 获取可用的别名
                val aliases = keyStore.aliases()
                val aliasList = mutableListOf<String>()
                while (aliases.hasMoreElements()) {
                    aliasList.add(aliases.nextElement())
                }

                if (aliasList.isEmpty()) {
                    val errorMessage = context.getString(R.string.apk_keystore_no_aliases, keyStoreType)
                    AppLogger.e(TAG, errorMessage)
                    return Pair(false, errorMessage)
                } else {
                    AppLogger.d(TAG, "$keyStoreType 密钥库中的别名: ${aliasList.joinToString()}")

                    // 如果指定的别名不存在，但有其他别名，使用第一个别名
                    if (!aliasList.contains(keyAlias) && aliasList.isNotEmpty()) {
                        AppLogger.w(TAG, "指定的别名'$keyAlias'不存在，将使用可用的别名: ${aliasList[0]}")
                        val actualKeyAlias = aliasList[0]
                        return signWithKeyStore(
                                keyStore,
                                unsignedApk,
                                actualKeyAlias,
                                keyPassword,
                                outputApk
                        )
                    }
                }

                return signWithKeyStore(keyStore, unsignedApk, keyAlias, keyPassword, outputApk)
            }
        } catch (e: Exception) {
            val errorMessage = context.getString(R.string.apk_load_keystore_format_failed, keyStoreType, e.message ?: "")
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }

    /** 使用已加载的KeyStore进行签名 */
    private fun signWithKeyStore(
            keyStore: KeyStore,
            unsignedApk: File,
            keyAlias: String,
            keyPassword: String,
            outputApk: File
    ): Pair<Boolean, String?> {
        try {
            // 获取私钥
            val key = keyStore.getKey(keyAlias, keyPassword.toCharArray())
            if (key == null) {
                val errorMessage = context.getString(R.string.apk_key_not_found_in_keystore, keyAlias)
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            if (key !is PrivateKey) {
                val errorMessage = context.getString(R.string.apk_key_not_private_key, key.javaClass.name)
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }
            val privateKey = key

            // 获取证书链
            val certificateChain = keyStore.getCertificateChain(keyAlias)
            if (certificateChain == null || certificateChain.isEmpty()) {
                val errorMessage = context.getString(R.string.apk_cannot_get_cert_chain, keyAlias)
                AppLogger.e(TAG, errorMessage)
                return Pair(false, errorMessage)
            }

            val x509CertificateChain =
                    certificateChain.map { cert ->
                        if (cert !is X509Certificate) {
                            val errorMessage = context.getString(R.string.apk_cert_not_x509, cert.javaClass.name)
                            AppLogger.e(TAG, errorMessage)
                            return Pair(false, errorMessage)
                        }
                        cert as X509Certificate
                    }

            // 使用ApkSigner进行签名
            val signer =
                    ApkSigner.SignerConfig.Builder(keyAlias, privateKey, x509CertificateChain)
                            .build()
            val signerConfigs = listOf(signer)

            val apkSigner =
                    ApkSigner.Builder(signerConfigs)
                            .setInputApk(unsignedApk)
                            .setOutputApk(outputApk)
                            .setMinSdkVersion(26) // 根据项目实际最低SDK版本调整
                            .build()

            try {
                apkSigner.sign()
            } catch (e: Exception) {
                val errorMessage = context.getString(R.string.apk_signer_execution_failed, e.message ?: "")
                AppLogger.e(TAG, errorMessage, e)
                return Pair(false, errorMessage)
            }

            AppLogger.d(TAG, "APK签名完成: ${outputApk.absolutePath}")
            return Pair(true, null)
        } catch (e: Exception) {
            val errorMessage = context.getString(R.string.apk_sign_with_keystore_failed, e.message ?: "")
            AppLogger.e(TAG, errorMessage, e)
            return Pair(false, errorMessage)
        }
    }
}
