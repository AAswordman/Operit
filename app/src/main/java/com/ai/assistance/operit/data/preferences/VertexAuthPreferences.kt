package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 全应用共享的 Vertex AI 登录凭证。项目编号由用户填写。 */
data class VertexAuthState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val email: String? = null,
)

class VertexAuthPreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var preferences = createPreferences(appContext)
    private val _authState = MutableStateFlow(readState())
    val authState: StateFlow<VertexAuthState?> = _authState.asStateFlow()

    fun currentState(): VertexAuthState? = _authState.value

    fun save(state: VertexAuthState) {
        require(state.accessToken.isNotBlank()) { "Vertex access token is empty" }
        require(state.refreshToken.isNotBlank()) { "Vertex refresh token is empty" }
        require(state.expiresAtMillis > 0L) { "Vertex token expiration is invalid" }
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, state.accessToken)
            .putString(KEY_REFRESH_TOKEN, state.refreshToken)
            .putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
            .apply {
                if (state.email.isNullOrBlank()) remove(KEY_EMAIL) else putString(KEY_EMAIL, state.email)
            }
            .apply()
        _authState.value = state
    }

    fun clear() {
        preferences.edit().clear().apply()
        _authState.value = null
    }

    private fun readState(): VertexAuthState? {
        return try {
            val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
            val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.trim()
            val expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0L)
            if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty() || expiresAtMillis <= 0L) {
                null
            } else {
                VertexAuthState(accessToken, refreshToken, expiresAtMillis, preferences.getString(KEY_EMAIL, null))
            }
        } catch (error: SecurityException) {
            AppLogger.e(TAG, "Vertex OAuth credentials are unreadable; resetting encrypted store", error)
            appContext.deleteSharedPreferences(STORE_NAME)
            preferences = createEncryptedPreferences(appContext)
            null
        }
    }

    companion object {
        private const val TAG = "VertexAuthPreferences"
        private const val STORE_NAME = "vertex_oauth_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_EMAIL = "email"

        @Volatile private var instance: VertexAuthPreferences? = null

        fun getInstance(context: Context): VertexAuthPreferences {
            return instance ?: synchronized(this) {
                instance ?: VertexAuthPreferences(context.applicationContext).also { instance = it }
            }
        }

        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                createEncryptedPreferences(context)
            } catch (error: GeneralSecurityException) {
                context.deleteSharedPreferences(STORE_NAME)
                createEncryptedPreferences(context)
            } catch (error: IOException) {
                context.deleteSharedPreferences(STORE_NAME)
                createEncryptedPreferences(context)
            }
        }

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            return EncryptedSharedPreferences.create(
                context,
                STORE_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}