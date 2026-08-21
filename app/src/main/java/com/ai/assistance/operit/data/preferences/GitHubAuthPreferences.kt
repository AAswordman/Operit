package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.persistence.PreferenceStateRepairResult
import com.ai.assistance.operit.data.persistence.PreferenceStoreCatalog
import com.ai.assistance.operit.data.persistence.recoverablePreferencesDataStore
import com.ai.assistance.operit.data.persistence.repairPreferenceState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.githubAuthDataStore: DataStore<Preferences> by
    recoverablePreferencesDataStore(name = "github_auth_preferences")

@Serializable
data class GitHubUser(
    @SerialName("id") val id: Long,
    @SerialName("login") val login: String,
    @SerialName("name") val name: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("bio") val bio: String? = null,
    @SerialName("public_repos") val publicRepos: Int? = null,
    @SerialName("followers") val followers: Int? = null,
    @SerialName("following") val following: Int? = null
)

/**
 * GitHub认证偏好设置管理器
 * 负责管理GitHub OAuth认证状态、用户信息和访问令牌
 */
class GitHubAuthPreferences(private val context: Context) {

    companion object {
        private const val TAG = "GitHubAuthPreferences"
        const val GITHUB_SCOPE = "notifications,public_repo,user:email,read:user"
        private const val REQUIRED_AUTH_VERSION = 3
        
        // 认证相关键
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val TOKEN_TYPE = stringPreferencesKey("token_type")
        private val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_INFO = stringPreferencesKey("user_info")
        private val LAST_LOGIN_TIME = longPreferencesKey("last_login_time")
        private val AUTH_VERSION = longPreferencesKey("auth_version")
        private val GRANTED_SCOPE = stringPreferencesKey("granted_scope")
        private val ACTIVE_OAUTH_TRANSACTION_ID = stringPreferencesKey("active_oauth_transaction_id")
        private val ACTIVE_OAUTH_DELIVERY_CREDENTIAL = stringPreferencesKey("active_oauth_delivery_credential")
        private val ACTIVE_OAUTH_EXPIRES_AT = longPreferencesKey("active_oauth_expires_at")
        
        @Volatile
        private var INSTANCE: GitHubAuthPreferences? = null
        
        fun getInstance(context: Context): GitHubAuthPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GitHubAuthPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

    }

    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val requiredScopes: Set<String> =
        GITHUB_SCOPE.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun parseScopeSet(scope: String?): Set<String> {
        return scope
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    private fun isAuthSessionCurrent(preferences: Preferences): Boolean {
        val grantedScopes = parseScopeSet(preferences[GRANTED_SCOPE])
        val authVersion = preferences[AUTH_VERSION] ?: 0L
        return authVersion >= REQUIRED_AUTH_VERSION && grantedScopes.containsAll(requiredScopes)
    }

    private fun decodeUserInfo(raw: String): GitHubUser? =
        try {
            json.decodeFromString<GitHubUser>(raw)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Invalid persisted GitHub user information", e)
            null
        }

    suspend fun repairPersistedState(): Boolean =
        repairPreferenceState(
            context = context,
            storeName = PreferenceStoreCatalog.GITHUB_AUTH,
            dataStore = context.githubAuthDataStore
        ) { current ->
            val values = current.asMap().entries.associate { it.key.name to it.value }
            val mutable = current.toMutablePreferences()
            val issues = linkedSetOf<String>()

            fun removeName(name: String) {
                mutable.asMap().keys.filter { it.name == name }.forEach { mutable.remove(it) }
            }

            fun replaceLoggedIn(value: Boolean) {
                removeName(IS_LOGGED_IN.name)
                mutable[IS_LOGGED_IN] = value
                issues += IS_LOGGED_IN.name
            }

            fun validString(key: Preferences.Key<String>): String? {
                val raw = values[key.name] ?: return null
                if (raw is String) return raw
                removeName(key.name)
                issues += key.name
                return null
            }

            fun validLong(
                key: Preferences.Key<Long>,
                predicate: (Long) -> Boolean
            ): Long? {
                val raw = values[key.name] ?: return null
                if (raw is Long && predicate(raw)) return raw
                removeName(key.name)
                issues += key.name
                return null
            }

            val rawLoggedIn = values[IS_LOGGED_IN.name]
            var loggedIn = rawLoggedIn as? Boolean ?: false
            if (rawLoggedIn != null && rawLoggedIn !is Boolean) {
                replaceLoggedIn(false)
                loggedIn = false
            }

            val accessToken = validString(ACCESS_TOKEN)
            validString(TOKEN_TYPE)
            validString(REFRESH_TOKEN)
            val userInfoText = validString(USER_INFO)
            val grantedScope = validString(GRANTED_SCOPE)
            val transactionId = validString(ACTIVE_OAUTH_TRANSACTION_ID)
            val deliveryCredential = validString(ACTIVE_OAUTH_DELIVERY_CREDENTIAL)

            val rawTokenExpiresAt = values[TOKEN_EXPIRES_AT.name]
            val tokenExpiresAt = validLong(TOKEN_EXPIRES_AT) { it > 0L }
            validLong(LAST_LOGIN_TIME) { it >= 0L }
            val authVersion = validLong(AUTH_VERSION) { it >= 0L }
            val activeExpiresAt = validLong(ACTIVE_OAUTH_EXPIRES_AT) { it > 0L }

            var userInfoValid = userInfoText != null
            if (userInfoText != null && decodeUserInfo(userInfoText) == null) {
                removeName(USER_INFO.name)
                issues += USER_INFO.name
                userInfoValid = false
            }

            if (activeExpiresAt != null &&
                (transactionId.isNullOrBlank() || deliveryCredential.isNullOrBlank())
            ) {
                removeName(ACTIVE_OAUTH_EXPIRES_AT.name)
                issues += ACTIVE_OAUTH_EXPIRES_AT.name
            }

            val authStateConsistent =
                accessToken?.isNotBlank() == true &&
                    userInfoValid &&
                    authVersion != null && authVersion >= REQUIRED_AUTH_VERSION &&
                    (rawTokenExpiresAt == null || tokenExpiresAt != null) &&
                    parseScopeSet(grantedScope).containsAll(requiredScopes)
            if (loggedIn && !authStateConsistent) {
                replaceLoggedIn(false)
            }

            PreferenceStateRepairResult(mutable.toPreferences(), issues)
        }

    // 登录状态Flow
    val isLoggedInFlow: Flow<Boolean> = context.githubAuthDataStore.data.map { preferences ->
        (preferences[IS_LOGGED_IN] ?: false) && isAuthSessionCurrent(preferences)
    }

    // 访问令牌Flow
    val accessTokenFlow: Flow<String?> = context.githubAuthDataStore.data.map { preferences ->
        if (isAuthSessionCurrent(preferences)) preferences[ACCESS_TOKEN] else null
    }

    // 用户信息Flow
    val userInfoFlow: Flow<GitHubUser?> = context.githubAuthDataStore.data.map { preferences ->
        if (!isAuthSessionCurrent(preferences)) {
            return@map null
        }
        val userInfoJson = preferences[USER_INFO]
        if (userInfoJson != null) {
            decodeUserInfo(userInfoJson)
        } else {
            null
        }
    }

    // 最后登录时间Flow
    val lastLoginTimeFlow: Flow<Long> = context.githubAuthDataStore.data.map { preferences ->
        preferences[LAST_LOGIN_TIME] ?: 0L
    }

    /**
     * 保存认证信息
     */
    suspend fun saveAuthInfo(
        accessToken: String,
        tokenType: String = "bearer",
        expiresIn: Long? = null,
        refreshToken: String? = null,
        userInfo: GitHubUser,
        grantedScope: String? = null
    ) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[ACCESS_TOKEN] = accessToken
            preferences[TOKEN_TYPE] = tokenType
            preferences[USER_INFO] = json.encodeToString(userInfo)
            preferences[LAST_LOGIN_TIME] = System.currentTimeMillis()
            preferences[AUTH_VERSION] = REQUIRED_AUTH_VERSION.toLong()
            preferences[GRANTED_SCOPE] = grantedScope.orEmpty()
            
            expiresIn?.let {
                preferences[TOKEN_EXPIRES_AT] = System.currentTimeMillis() + (it * 1000)
            }
            
            refreshToken?.let {
                preferences[REFRESH_TOKEN] = it
            }
            if (refreshToken == null) {
                preferences.remove(REFRESH_TOKEN)
            }
            if (expiresIn == null) {
                preferences.remove(TOKEN_EXPIRES_AT)
            }
            preferences.remove(ACTIVE_OAUTH_TRANSACTION_ID)
            preferences.remove(ACTIVE_OAUTH_DELIVERY_CREDENTIAL)
            preferences.remove(ACTIVE_OAUTH_EXPIRES_AT)
        }
    }

    /**
     * 更新用户信息
     */
    suspend fun updateUserInfo(userInfo: GitHubUser) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[USER_INFO] = json.encodeToString(userInfo)
        }
    }

    /**
     * 检查令牌是否已过期
     */
    suspend fun isTokenExpired(): Boolean {
        val preferences = context.githubAuthDataStore.data.first()
        val expiresAt = preferences[TOKEN_EXPIRES_AT] ?: return false
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * 获取当前访问令牌
     */
    suspend fun getCurrentAccessToken(): String? {
        val preferences = context.githubAuthDataStore.data.first()
        if (!isAuthSessionCurrent(preferences)) {
            return null
        }
        return preferences[ACCESS_TOKEN]
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUserInfo(): GitHubUser? {
        val preferences = context.githubAuthDataStore.data.first()
        if (!isAuthSessionCurrent(preferences)) {
            return null
        }
        val userInfoJson = preferences[USER_INFO]
        return if (userInfoJson != null) {
            decodeUserInfo(userInfoJson)
        } else {
            null
        }
    }

    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        val preferences = context.githubAuthDataStore.data.first()
        return (preferences[IS_LOGGED_IN] ?: false) && isAuthSessionCurrent(preferences)
    }

    /**
     * 登出
     */
    suspend fun logout() {
        context.githubAuthDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun saveActiveOAuthTransaction(
        transactionId: String,
        deliveryCredential: String,
        expiresAt: Long
    ) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[ACTIVE_OAUTH_TRANSACTION_ID] = transactionId
            preferences[ACTIVE_OAUTH_DELIVERY_CREDENTIAL] = deliveryCredential
            preferences[ACTIVE_OAUTH_EXPIRES_AT] = expiresAt
        }
    }

    suspend fun getActiveOAuthTransaction(): ActiveGitHubOAuthTransaction? {
        val preferences = context.githubAuthDataStore.data.first()
        val transactionId = preferences[ACTIVE_OAUTH_TRANSACTION_ID]
        val deliveryCredential = preferences[ACTIVE_OAUTH_DELIVERY_CREDENTIAL]
        val expiresAt = preferences[ACTIVE_OAUTH_EXPIRES_AT]
        if (transactionId == null || deliveryCredential == null || expiresAt == null) {
            return null
        }
        if (System.currentTimeMillis() >= expiresAt) {
            clearActiveOAuthTransaction()
            return null
        }
        return ActiveGitHubOAuthTransaction(transactionId, deliveryCredential, expiresAt)
    }

    suspend fun clearActiveOAuthTransaction() {
        context.githubAuthDataStore.edit { mutablePreferences ->
            mutablePreferences.remove(ACTIVE_OAUTH_TRANSACTION_ID)
            mutablePreferences.remove(ACTIVE_OAUTH_DELIVERY_CREDENTIAL)
            mutablePreferences.remove(ACTIVE_OAUTH_EXPIRES_AT)
        }
    }

    /**
     * 获取访问令牌的授权头
     */
    suspend fun getAuthorizationHeader(): String? {
        val token = getCurrentAccessToken()
        return if (token != null) {
            "Bearer $token"
        } else {
            null
        }
    }
}

data class ActiveGitHubOAuthTransaction(
    val transactionId: String,
    val deliveryCredential: String,
    val expiresAt: Long
)
