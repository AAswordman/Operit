package com.ai.assistance.operit.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Device-local, namespace-bound ciphertext. Not written to model configuration; Android Auto Backup excludes this directory. */
internal class AndroidProviderOAuthStore(context: Context) : ProviderOAuthCredentialStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "toolpkg_oauth")
    private val keyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    private fun file(key: String): AtomicFile {
        require(key.matches(Regex("[a-f0-9]{64}"))) { "Invalid OAuth credential namespace" }
        check(directory.isDirectory || directory.mkdirs()) { "OAuth credential directory is unavailable" }
        return AtomicFile(File(directory, "$key.enc"))
    }

    @Synchronized
    override fun load(key: String): ProviderOAuthTokens? {
        val file = file(key)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return try {
            val secret = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (secret == null) { file.delete(); return null }
            val bytes = file.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 262_144) throw IOException("OAuth credential file is too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size >= 29 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(key.toByteArray(UTF_8))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), UTF_8))
            ProviderOAuthTokens(
                json.getString("access_token"),
                if (json.isNull("refresh_token")) null else json.getString("refresh_token"),
                if (json.isNull("expires_at")) null else json.getLong("expires_at"),
            )
        } catch (_: GeneralSecurityException) {
            // Restored or invalidated device keys must lead to a fresh login, never plaintext fallback.
            file.delete(); null
        } catch (_: IOException) {
            file.delete(); null
        } catch (_: IllegalArgumentException) {
            file.delete(); null
        } catch (_: org.json.JSONException) {
            file.delete(); null
        }
    }

    @Synchronized
    override fun save(key: String, tokens: ProviderOAuthTokens) {
        val secret = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        check(cipher.iv.size == 12)
        cipher.updateAAD(key.toByteArray(UTF_8))
        val json = JSONObject().put("access_token", tokens.accessToken)
            .put("refresh_token", tokens.refreshToken ?: JSONObject.NULL)
            .put("expires_at", tokens.expiresAtMillis ?: JSONObject.NULL)
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(UTF_8))
        val file = file(key)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    override fun clear(key: String) {
        val file = file(key)
        file.delete()
        check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            "OAuth credentials could not be removed"
        }
    }

    companion object { private const val KEY_ALIAS = "operit.toolpkg.oauth.v1" }
}
