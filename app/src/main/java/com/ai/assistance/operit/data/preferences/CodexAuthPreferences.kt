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

/** Credentials shared by all Codex model configurations in this app. */
data class CodexAuthState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val accountId: String,
    val residency: String? = null,
    val email: String? = null,
)

class CodexAuthPreferences private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var preferences = createPreferences(appContext)

    private val _authState = MutableStateFlow(readState())
    val authState: StateFlow<CodexAuthState?> = _authState.asStateFlow()

    fun currentState(): CodexAuthState? = _authState.value

    fun save(state: CodexAuthState) {
        require(state.accessToken.isNotBlank()) { "Codex access token is empty" }
        require(state.refreshToken.isNotBlank()) { "Codex refresh token is empty" }
        require(state.accountId.isNotBlank()) { "Codex account ID is empty" }
        require(state.expiresAtMillis > 0L) { "Codex token expiration is invalid" }

        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, state.accessToken)
            .putString(KEY_REFRESH_TOKEN, state.refreshToken)
            .putLong(KEY_EXPIRES_AT, state.expiresAtMillis)
            .putString(KEY_ACCOUNT_ID, state.accountId)
            .apply {
                if (state.residency.isNullOrBlank()) {
                    remove(KEY_RESIDENCY)
                } else {
                    putString(KEY_RESIDENCY, state.residency)
                }
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

    private fun readState(): CodexAuthState? {
        return try {
            readStateFromPreferences()
        } catch (error: SecurityException) {
            // Restored encrypted values can be unreadable when Android Keystore kept the key
            // device-local. Reset only Codex OAuth state so the settings screen can open.
            AppLogger.e(TAG, "Codex OAuth credentials are unreadable; resetting encrypted store", error)
            resetEncryptedStore()
            null
        }
    }

    private fun readStateFromPreferences(): CodexAuthState? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.trim()
        val accountId = preferences.getString(KEY_ACCOUNT_ID, null)?.trim()
        val expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (accessToken.isNullOrEmpty() ||
            refreshToken.isNullOrEmpty() ||
            accountId.isNullOrEmpty() ||
            expiresAtMillis <= 0L
        ) {
            return null
        }

        return CodexAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresAtMillis,
            accountId = accountId,
            residency = preferences.getString(KEY_RESIDENCY, null),
            email = preferences.getString(KEY_EMAIL, null),
        )
    }

    private fun resetEncryptedStore() {
        appContext.deleteSharedPreferences(STORE_NAME)
        preferences = createEncryptedPreferences(appContext)
    }

    companion object {
        private const val TAG = "CodexAuthPreferences"
        private const val STORE_NAME = "codex_oauth_credentials"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_RESIDENCY = "residency"
        private const val KEY_EMAIL = "email"

        @Volatile
        private var instance: CodexAuthPreferences? = null

        fun getInstance(context: Context): CodexAuthPreferences {
            return instance ?: synchronized(this) {
                instance ?: CodexAuthPreferences(context.applicationContext).also { instance = it }
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
            error: Exception
        ): SharedPreferences {
            // Android backup restores SharedPreferences XML but not the app's Android Keystore
            // entry. Tink then rejects the encrypted keyset with AEADBadTagException.
            AppLogger.e(TAG, "Codex OAuth encrypted store cannot be opened; resetting it", error)
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
