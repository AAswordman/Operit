package com.ai.assistance.operit.core.subpack

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.Provider
import java.security.Security

/** 密钥库辅助类 统一处理密钥库加载、验证和Provider管理 */
class KeyStoreHelper {
    companion object {
        private const val TAG = "KeyStoreHelper"

        // 保存注册的BouncyCastle提供者引用
        private var bcProvider: Provider? = null

        /**
         * 注册BouncyCastle提供者
         * @return 是否成功注册
         */
        @JvmStatic
        fun registerBouncyCastleProvider(): Boolean {
            try {
                // 先移除再添加，避免重复添加导致的问题
                Security.removeProvider("BC")

                // 创建新的BouncyCastle提供者实例
                val provider = BouncyCastleProvider()

                // 添加到安全提供者列表首位确保优先级
                val position = Security.insertProviderAt(provider, 1)

                // 保存引用
                bcProvider = provider

                return position > 0
            } catch (e: Exception) {
                AppLogger.e(TAG, "注册BouncyCastle提供程序失败: ${e.message}", e)
                return false
            }
        }

        /**
         * 获取密钥库实例
         * @param keyStoreType 密钥库类型 (PKCS12, JKS等)
         * @return 密钥库实例或null
         */
        @JvmStatic
        fun getKeyStoreInstance(keyStoreType: String): KeyStore? {
            try {
                // 对于PKCS12类型，确保BouncyCastle提供者已注册并在第一位
                if (keyStoreType == "PKCS12") {
                    registerBouncyCastleProvider()

                    // 尝试直接通过类型获取，因为BC现在是第一位提供者
                    return KeyStore.getInstance(keyStoreType)
                } else {
                    // 其他类型直接获取
                    return KeyStore.getInstance(keyStoreType)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "获取${keyStoreType}密钥库实例失败: ${e.message}", e)
                return null
            }
        }

        /**
         * 验证密钥库文件是否有效
         * @param file 密钥库文件
         * @param type 密钥库类型
         * @param password 密钥库密码
         * @return 是否有效
         */
        @JvmStatic
        fun validateKeystore(file: File, type: String, password: String): Boolean {
            try {
                // 对于PKCS12类型，确保提供者已正确注册
                if (type == "PKCS12") {
                    registerBouncyCastleProvider()
                }

                // 获取密钥库实例
                val keyStore = getKeyStoreInstance(type) ?: return false

                // 加载密钥库
                FileInputStream(file).use { input ->
                    keyStore.load(input, password.toCharArray())

                    // 检查是否包含至少一个别名
                    return keyStore.aliases().hasMoreElements()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "$type 密钥库验证失败: ${e.message}")
                return false
            }
        }

        /**
         * 从应用assets中加载内置密钥库
         * @param context 应用上下文
         * @param assetName 资产文件名
         * @param outputFileName 输出文件名
         * @return 密钥库文件或null
         */
        @JvmStatic
        fun loadKeystoreFromAsset(
                context: Context,
                assetName: String,
                outputFileName: String
        ): File? {
            try {
                val keystoreFile = File(context.filesDir, outputFileName)

                // 如果文件已存在且大小合理，直接返回
                if (keystoreFile.exists() && keystoreFile.length() > 1000) {
                    return keystoreFile
                }

                // 如果已存在但可能损坏，先删除
                if (keystoreFile.exists()) {
                    keystoreFile.delete()
                }

                // 从assets复制密钥库文件
                context.assets.open(assetName).use { input ->
                    val bytes = input.readBytes()

                    if (bytes.size < 1000) {
                        AppLogger.e(TAG, "密钥库文件大小异常: ${bytes.size}字节")
                        return null
                    }

                    keystoreFile.outputStream().use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                }

                return if (keystoreFile.exists() && keystoreFile.length() > 1000) {
                    keystoreFile
                } else {
                    null
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载内置密钥库失败: ${e.message}", e)
                return null
            }
        }

        /**
         * 获取或创建应用签名密钥库
         * @param context 应用上下文
         * @return 密钥库文件
         */
        @JvmStatic
        fun getOrCreateKeystore(context: Context): File {
            // 确保BouncyCastle提供者已注册
            registerBouncyCastleProvider()

            // 先尝试PKCS12格式
            val pkcs12KeyStoreFile = File(context.filesDir, "pkcs12.keystore")
            if (pkcs12KeyStoreFile.exists() && pkcs12KeyStoreFile.length() > 1000) {
                if (validateKeystore(pkcs12KeyStoreFile, "PKCS12", "android")) {
                    return pkcs12KeyStoreFile
                }
            }

            // 再尝试JKS格式
            val jksKeyStoreFile = File(context.filesDir, "jks.jks")
            if (jksKeyStoreFile.exists() && jksKeyStoreFile.length() > 1000) {
                if (validateKeystore(jksKeyStoreFile, "JKS", "android")) {
                    return jksKeyStoreFile
                }
            }

            // 尝试从assets加载
            val keystoreFiles = listOf(Pair("pkcs12.keystore", "PKCS12"), Pair("jks.jks", "JKS"))

            for ((assetName, type) in keystoreFiles) {
                try {
                    val keyStoreFile = loadKeystoreFromAsset(context, assetName, assetName)
                    if (keyStoreFile != null && validateKeystore(keyStoreFile, type, "android")) {
                        return keyStoreFile
                    }
                } catch (e: Exception) {
                    // 忽略单个格式错误，继续尝试下一个
                }
            }

            // 如果所有尝试都失败，返回默认文件路径
            return pkcs12KeyStoreFile
        }
    }
}
