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

/** 全应用共享的 Antigravity 登录凭证。 */
data class AntigravityAuthState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val projectId: String,
    val email: String? = null,
)

class AntigravityAuthPreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var preferences = createPreferences(appContext)

    private val _authState = MutableStateFlow(readState())
    val authState: StateFlow<AntigravityAuthState?> = _authState.asStateFlow()

    fun currentState(): AntigravityAuthState? = _authState.value

    fun save(state: AntigravityAuthState) {
        require(state.accessToken.isNotBlank()) { "Antigravity access token is empty" }
        require(state.refreshToken.isNotBlank()) { "Antigravity refresh token is empty" }
        require(state.projectId.isNotBlank()) { "Antigravity project id is empty" }
        require(state.expiresAtMillis > 0L) { "Antigravity token expiration is invalid" }

        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, state.accessToken)
            .putString(KEY_REFRESH_TOKEN, state.refreshToken)
            .putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
            .putString(KEY_PROJECT_ID, state.projectId)
            .apply {
                if (state.email.isNullOrBlank()) {
                    remove(KEY_EMAIL)
                } else {
                    putString(KEY_EMAIL, state.email)
                }
            }
            .apply()
        _authState.value = state
    }

    fun clear() {
        preferences.edit().clear().apply()
        _authState.value = null
    }

    private fun readState(): AntigravityAuthState? {
        return try {
            readStateFromPreferences()
        } catch (error: SecurityException) {
            AppLogger.e(TAG, "Antigravity OAuth credentials are unreadable; resetting encrypted store", error)
            resetEncryptedStore()
            null
        }
    }

    private fun readStateFromPreferences(): AntigravityAuthState? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.trim()
        val projectId = preferences.getString(KEY_PROJECT_ID, null)?.trim()
        val expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (accessToken.isNullOrEmpty() ||
            refreshToken.isNullOrEmpty() ||
            projectId.isNullOrEmpty() ||
            expiresAtMillis <= 0L
        ) {
            return null
        }
        return AntigravityAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAtMillis,
            projectId = projectId,
            email = preferences.getString(KEY_EMAIL, null),
        )
    }

    private fun resetEncryptedStore() {
        appContext.deleteSharedPreferences(STORE_NAME)
        preferences = createEncryptedPreferences(appContext)
    }

    companion object {
        private const val TAG = "AntigravityAuthPreferences"
        private const val STORE_NAME = "antigravity_oauth_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_EMAIL = "email"

        @Volatile
        private var instance: AntigravityAuthPreferences? = null

        fun getInstance(context: Context): AntigravityAuthPreferences {
            return instance ?: synchronized(this) {
                instance ?: AntigravityAuthPreferences(context.applicationContext).also { instance = it }
            }
        }

        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                createEncryptedPreferences(context)
            } catch (error: GeneralSecurityException) {
                recreatePreferencesAfterUnreadableStore(context, error)
            } catch (error: IOException) {
                recreatePreferencesAfterUnreadableStore(context, error)
            }
        }

        private fun recreatePreferencesAfterUnreadableStore(
            context: Context,
            error: Exception,
        ): SharedPreferences {
            AppLogger.e(TAG, "Antigravity OAuth encrypted store cannot be opened; resetting it", error)
            context.deleteSharedPreferences(STORE_NAME)
            return createEncryptedPreferences(context)
        }

        private fun createEncryptedPreferences(context: Context): SharedPreferences {
            return EncryptedSharedPreferences.create(
                context,
                STORE_NAME,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
