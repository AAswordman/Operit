package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Central provider for environment-like configuration values used by tool packages.
 *
 * Values are stored in EncryptedSharedPreferences (AES256_GCM) and can optionally fall back
 * to the process environment via System.getenv when not explicitly set.
 */
class EnvPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = getEncryptedPrefs(context.applicationContext)

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get effective environment value for the given key.
     *
     * Lookup order:
     * 1. App preferences (if non-blank)
     * 2. System.getenv (may be null)
     */
    fun getEnv(key: String): String? {
        val name = key.trim()
        if (name.isEmpty()) return null

        val fromPrefs = prefs.getString(name, null)
        if (!fromPrefs.isNullOrEmpty()) {
            return fromPrefs
        }

        return try {
            System.getenv(name)
        } catch (_: Exception) {
            null
        }
    }

    /** Set or override an environment value in app preferences. */
    fun setEnv(key: String, value: String) {
        val name = key.trim()
        if (name.isEmpty()) return
        prefs.edit().putString(name, value).apply()
    }

    /** Remove a stored environment value (does not affect process env). */
    fun removeEnv(key: String) {
        val name = key.trim()
        if (name.isEmpty()) return
        prefs.edit().remove(name).apply()
    }

    /** Get all stored environment values from preferences. */
    fun getAllEnv(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            val key = k.trim()
            val value = v as? String
            if (key.isNotEmpty() && !value.isNullOrEmpty()) key to value else null
        }.toMap()
    }

    /** Replace all stored environment values with the given map. */
    fun setAllEnv(variables: Map<String, String>) {
        val editor = prefs.edit().clear()
        variables.forEach { (k, v) ->
            val key = k.trim()
            if (key.isNotEmpty()) {
                editor.putString(key, v)
            }
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "env_preferences"

        @Volatile private var INSTANCE: EnvPreferences? = null

        fun getInstance(context: Context): EnvPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EnvPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
