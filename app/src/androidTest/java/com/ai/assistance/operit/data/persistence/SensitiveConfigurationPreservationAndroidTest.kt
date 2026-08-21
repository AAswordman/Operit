package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.model.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.RootCommandExecutionMode
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveConfigurationPreservationAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun modelRepairPreservesSensitiveAndUnknownFieldsAndConverges() = runBlocking {
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.MODEL_CONFIGS)
        val configId = "sensitive-model-fixture"
        val apiKey = "  model-secret::exact  "
        val poolKey = "pool-secret::exact"
        val endpoint = "https://model.example.invalid/v1"
        val customHeaders =
            """{"Authorization":"Bearer header-secret","X-Exact":"  spaced  "}"""
        val customParameters =
            """[{"id":"secret-param","name":"Secret","apiName":"secret","defaultValue":"exact-default","currentValue":"exact-current","isEnabled":true,"valueType":"STRING","category":"OTHER","futureParameterField":"preserved"}]"""
        val summaryRules = "Keep this exact summary rule."
        val rawConfig =
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(configId),
                    "name" to JsonPrimitive("Sensitive fixture"),
                    "apiKey" to JsonPrimitive(apiKey),
                    "apiEndpoint" to JsonPrimitive(endpoint),
                    "apiProviderType" to JsonPrimitive(ApiProviderType.OPENAI.name),
                    "apiProviderTypeId" to JsonPrimitive(7),
                    "apiKeyPool" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "id" to JsonPrimitive("pool-id"),
                                        "key" to JsonPrimitive(poolKey),
                                        "name" to JsonPrimitive("Pool key"),
                                        "availabilityStatus" to JsonPrimitive("BROKEN_STATUS"),
                                        "usageCount" to JsonPrimitive("wrong-type"),
                                        "lastUsed" to JsonPrimitive(17L),
                                        "futurePoolField" to JsonPrimitive("preserved")
                                    )
                                )
                            )
                        ),
                    "currentKeyIndex" to JsonPrimitive(0),
                    "hasCustomParameters" to JsonPrimitive(true),
                    "customParameters" to JsonPrimitive(customParameters),
                    "customHeaders" to JsonPrimitive(customHeaders),
                    "summaryCustomRules" to JsonPrimitive(summaryRules),
                    "maxTokens" to JsonPrimitive("wrong-type"),
                    "futureConfigField" to JsonPrimitive("preserved")
                )
            )
        try {
            store.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("config_list")] =
                    Json.encodeToString(listOf(configId))
                preferences[stringPreferencesKey("config_$configId")] = rawConfig.toString()
            }
            val manager = ModelConfigManager(context)

            assertTrue(manager.repairPersistedState())
            assertFalse(manager.repairPersistedState())

            val repaired = requireNotNull(manager.getModelConfig(configId))
            assertEquals(apiKey, repaired.apiKey)
            val repairedPoolEntry = repaired.apiKeyPool.single()
            assertEquals("pool-id", repairedPoolEntry.id)
            assertEquals(poolKey, repairedPoolEntry.key)
            assertEquals("Pool key", repairedPoolEntry.name)
            assertEquals(ApiKeyAvailabilityStatus.UNTESTED, repairedPoolEntry.availabilityStatus)
            assertEquals(0L, repairedPoolEntry.usageCount)
            assertEquals(17L, repairedPoolEntry.lastUsed)
            assertEquals(endpoint, repaired.apiEndpoint)
            assertEquals(ApiProviderType.OPENAI.name, repaired.apiProviderTypeId)
            assertEquals(customHeaders, repaired.customHeaders)
            assertEquals(customParameters, repaired.customParameters)
            assertEquals(summaryRules, repaired.summaryCustomRules)
            assertEquals(4096, repaired.maxTokens)

            val persisted =
                Json.parseToJsonElement(
                    requireNotNull(store.data.first()[stringPreferencesKey("config_$configId")])
                ).jsonObject
            assertEquals(
                "preserved",
                persisted["futureConfigField"]?.jsonPrimitive?.content
            )
            val persistedPoolEntry =
                persisted["apiKeyPool"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
            assertEquals(poolKey, persistedPoolEntry?.get("key")?.jsonPrimitive?.content)
            assertEquals(
                "preserved",
                persistedPoolEntry
                    ?.get("futurePoolField")
                    ?.jsonPrimitive
                    ?.content
            )
        } finally {
            store.edit { preferences -> preferences.clear() }
        }
    }

    @Test
    fun legacyProfileRepairPreservesStructuredDocumentInputAndConverges() = runBlocking {
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.USER_PREFERENCES)
        val profileId = "legacy-structured-profile-fixture"
        val birthDate = 946_684_800_000L
        val rawProfile =
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive(profileId),
                    "name" to JsonPrimitive("Legacy structured profile"),
                    "birthDate" to JsonPrimitive(birthDate),
                    "gender" to JsonPrimitive("gender-exact"),
                    "personality" to JsonPrimitive("personality-exact"),
                    "identity" to JsonPrimitive("identity-exact"),
                    "occupation" to JsonPrimitive("occupation-exact"),
                    "aiStyle" to JsonPrimitive("ai-style-exact"),
                    "isInitialized" to JsonPrimitive("wrong-type"),
                    "futureProfileField" to JsonPrimitive("preserved")
                )
            )
        try {
            store.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("profile_list")] =
                    Json.encodeToString(listOf(profileId))
                preferences[stringPreferencesKey("active_profile_id")] = profileId
                preferences[stringPreferencesKey("profile_$profileId")] = rawProfile.toString()
            }
            val manager = UserPreferencesManager.getInstance(context)

            assertTrue(manager.repairPersistedState())
            assertFalse(manager.repairPersistedState())

            // This snapshot is the source consumed by the user.md document migration.
            val repaired = manager.readLegacyUserProfiles().profiles.single { it.id == profileId }
            assertEquals("Legacy structured profile", repaired.name)
            assertEquals(birthDate, repaired.birthDate)
            assertEquals("gender-exact", repaired.gender)
            assertEquals("personality-exact", repaired.personality)
            assertEquals("identity-exact", repaired.identity)
            assertEquals("occupation-exact", repaired.occupation)
            assertEquals("ai-style-exact", repaired.aiStyle)
            assertFalse(repaired.isInitialized)

            val persisted =
                Json.parseToJsonElement(
                    requireNotNull(store.data.first()[stringPreferencesKey("profile_$profileId")])
                ).jsonObject
            assertEquals(
                "preserved",
                persisted["futureProfileField"]?.jsonPrimitive?.content
            )
        } finally {
            store.edit { preferences -> preferences.clear() }
        }
    }

    @Test
    fun speechMigrationAndProfileRepairPreserveSecretsAndConverge() = runBlocking {
        val legacyStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.SPEECH_SERVICES)
        val profileStore =
            RecoverablePreferenceDataStores.get(
                context,
                PreferenceStoreCatalog.SPEECH_SERVICE_PROFILES
            )
        val ttsApiKey = "  tts-secret::exact  "
        val sttApiKey = "stt-secret::exact"
        val ttsUrl = "https://speech.example.invalid/tts"
        val requestBody = "{\"input\":\"{text}\",\"secret\":\"body-exact\"}"
        val legacyTts =
            JsonObject(
                mapOf(
                    "urlTemplate" to JsonPrimitive(ttsUrl),
                    "apiKey" to JsonPrimitive(ttsApiKey),
                    "headers" to
                        JsonObject(
                            mapOf(
                                "Authorization" to JsonPrimitive("Bearer speech-header-secret"),
                                "X-Exact" to JsonPrimitive("  header-spaces  ")
                            )
                        ),
                    "httpMethod" to JsonPrimitive(7),
                    "requestBody" to JsonPrimitive(requestBody),
                    "contentType" to JsonPrimitive("application/json"),
                    "localeTag" to JsonPrimitive("en-US"),
                    "voiceId" to JsonPrimitive("voice-exact"),
                    "modelName" to JsonPrimitive("model-exact"),
                    "responsePipeline" to
                        JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("pick"),
                                        "path" to JsonPrimitive("items[not-an-index]")
                                    )
                                )
                            )
                        ),
                    "futureHttpField" to JsonPrimitive("preserved")
                )
            )
        try {
            profileStore.edit { preferences -> preferences.clear() }
            legacyStore.edit { preferences ->
                preferences.clear()
                preferences[SpeechServicesPreferences.TTS_SERVICE_TYPE] =
                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS.name
                preferences[SpeechServicesPreferences.TTS_HTTP_CONFIG] = legacyTts.toString()
                preferences[SpeechServicesPreferences.STT_SERVICE_TYPE] =
                    SpeechServiceFactory.SpeechServiceType.OPENAI_STT.name
                preferences[SpeechServicesPreferences.STT_HTTP_CONFIG] =
                    """{"endpointUrl":"https://speech.example.invalid/stt","apiKey":"$sttApiKey","modelName":"stt-model-exact"}"""
            }

            val profiles = SpeechServiceProfilesPreferences(context)
            assertFalse(profiles.initializeAndRepair())
            val migratedTts = profiles.getCurrentTtsProfile()
            val migratedStt = profiles.getCurrentSttProfile()
            assertEquals(ttsApiKey, migratedTts.httpConfig.apiKey)
            assertEquals(ttsUrl, migratedTts.httpConfig.urlTemplate)
            assertEquals(requestBody, migratedTts.httpConfig.requestBody)
            assertEquals("voice-exact", migratedTts.httpConfig.voiceId)
            assertEquals("model-exact", migratedTts.httpConfig.modelName)
            assertEquals("GET", migratedTts.httpConfig.httpMethod)
            assertTrue(migratedTts.httpConfig.responsePipeline.isEmpty())
            assertEquals(sttApiKey, migratedStt.httpConfig.apiKey)
            assertEquals(
                "Bearer speech-header-secret",
                migratedTts.httpConfig.headers["Authorization"]
            )
            val migratedTtsRaw =
                Json.parseToJsonElement(
                    requireNotNull(
                        profileStore.data.first()[stringPreferencesKey("tts_profiles")]
                    )
                ).jsonArray.single().jsonObject
            assertEquals(
                "preserved",
                migratedTtsRaw["httpConfig"]
                    ?.jsonObject
                    ?.get("futureHttpField")
                    ?.jsonPrimitive
                    ?.content
            )

            profileStore.edit { preferences ->
                val key = stringPreferencesKey("tts_profiles")
                val storedProfiles =
                    Json.parseToJsonElement(requireNotNull(preferences[key])).jsonArray
                val profile = storedProfiles.single().jsonObject
                val http = requireNotNull(profile["httpConfig"]).jsonObject
                val malformedHttp = JsonObject(http + ("httpMethod" to JsonPrimitive(false)))
                val malformedProfile = JsonObject(profile + ("httpConfig" to malformedHttp))
                preferences[key] = JsonArray(listOf(malformedProfile)).toString()
            }
            assertTrue(profiles.initializeAndRepair())
            assertFalse(profiles.initializeAndRepair())
            assertEquals(ttsApiKey, profiles.getCurrentTtsProfile().httpConfig.apiKey)
            assertEquals(requestBody, profiles.getCurrentTtsProfile().httpConfig.requestBody)
        } finally {
            profileStore.edit { preferences -> preferences.clear() }
            legacyStore.edit { preferences -> preferences.clear() }
        }
    }

    @Test
    fun authExternalAndPermissionRepairsPreserveExactValuesAndConverge() = runBlocking {
        val githubStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.GITHUB_AUTH)
        val externalStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.EXTERNAL_HTTP_API)
        val toolStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.TOOL_PERMISSIONS)
        val androidStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.ANDROID_PERMISSION)
        val accessToken = "  github-access::exact  "
        val refreshToken = "github-refresh::exact"
        val deliveryCredential = "github-delivery::exact"
        val bearerToken = "  external-bearer::exact  "
        val customSuCommand = "  /system/xbin/su --mount-master  "
        try {
            githubStore.edit { preferences ->
                preferences.clear()
                preferences[booleanPreferencesKey("is_logged_in")] = true
                preferences[stringPreferencesKey("access_token")] = accessToken
                preferences[stringPreferencesKey("token_type")] = "bearer"
                preferences[stringPreferencesKey("refresh_token")] = refreshToken
                preferences[stringPreferencesKey("user_info")] =
                    """{"id":7,"login":"fixture","avatar_url":"https://avatars.example.invalid/7"}"""
                preferences[longPreferencesKey("last_login_time")] = 1L
                preferences[stringPreferencesKey("auth_version")] = "wrong-type"
                preferences[stringPreferencesKey("granted_scope")] =
                    GitHubAuthPreferences.GITHUB_SCOPE
                preferences[stringPreferencesKey("active_oauth_transaction_id")] =
                    "transaction-exact"
                preferences[stringPreferencesKey("active_oauth_delivery_credential")] =
                    deliveryCredential
                preferences[longPreferencesKey("active_oauth_expires_at")] =
                    System.currentTimeMillis() + 3_600_000L
            }
            val github = GitHubAuthPreferences.getInstance(context)
            assertTrue(github.repairPersistedState())
            assertFalse(github.repairPersistedState())
            val repairedGithub = githubStore.data.first()
            assertFalse(github.isLoggedIn())
            assertEquals(accessToken, repairedGithub[stringPreferencesKey("access_token")])
            assertEquals(refreshToken, repairedGithub[stringPreferencesKey("refresh_token")])
            assertEquals(
                deliveryCredential,
                repairedGithub[stringPreferencesKey("active_oauth_delivery_credential")]
            )
            assertEquals(
                deliveryCredential,
                github.getActiveOAuthTransaction()?.deliveryCredential
            )

            externalStore.edit { preferences ->
                preferences.clear()
                preferences[booleanPreferencesKey("external_http_api_enabled")] = true
                preferences[intPreferencesKey("external_http_api_port")] = 70_000
                preferences[stringPreferencesKey("external_http_api_bearer_token")] = bearerToken
            }
            val external = ExternalHttpApiPreferences.getInstance(context)
            assertTrue(external.repairPersistedState())
            assertFalse(external.repairPersistedState())
            val externalConfig = external.getConfig()
            assertTrue(externalConfig.enabled)
            assertEquals(ExternalHttpApiPreferences.DEFAULT_PORT, externalConfig.port)
            assertEquals(bearerToken, externalConfig.bearerToken)

            toolStore.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("master_switch")] = "BROKEN"
                preferences[intPreferencesKey("tool_permission_fixture")] = 9
            }
            val toolPermissions = ToolPermissionSystem.getInstance(context)
            assertTrue(toolPermissions.repairPersistedState())
            assertFalse(toolPermissions.repairPersistedState())
            assertEquals(PermissionLevel.ASK, toolPermissions.masterSwitchFlow.first())
            assertEquals(
                PermissionLevel.ASK,
                toolPermissions.getToolPermissionFlow("fixture").first()
            )

            androidStore.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("preferred_permission_level")] = "BROKEN"
                preferences[stringPreferencesKey("root_execution_mode")] = "BROKEN"
                preferences[stringPreferencesKey("custom_su_command")] = customSuCommand
            }
            val androidPermissions = AndroidPermissionPreferences(context)
            assertTrue(androidPermissions.repairPersistedState())
            assertFalse(androidPermissions.repairPersistedState())
            assertEquals(
                AndroidPermissionLevel.STANDARD,
                androidPermissions.preferredPermissionLevelFlow.first()
            )
            assertEquals(
                RootCommandExecutionMode.AUTO,
                androidPermissions.rootExecutionModeFlow.first()
            )
            assertEquals(
                customSuCommand,
                androidStore.data.first()[stringPreferencesKey("custom_su_command")]
            )
        } finally {
            githubStore.edit { preferences -> preferences.clear() }
            externalStore.edit { preferences -> preferences.clear() }
            toolStore.edit { preferences -> preferences.clear() }
            androidStore.edit { preferences -> preferences.clear() }
        }
    }

    @Test
    fun invalidDeletionMarkersNeverAuthorizeDeletionAndRepairConverges() = runBlocking {
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.USER_PREFERENCES)
        val falseMarkerId = "false-deletion-marker-fixture"
        val wrongTypeMarkerId = "wrong-type-deletion-marker-fixture"
        val ids = listOf("default", falseMarkerId, wrongTypeMarkerId)
        try {
            store.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("memory_space_list")] = Json.encodeToString(ids)
                preferences[stringPreferencesKey("active_memory_space_id")] = "default"
                ids.forEach { id ->
                    preferences[stringPreferencesKey("memory_space_$id")] =
                        """{"id":"$id","name":"$id"}"""
                }
                preferences[
                    booleanPreferencesKey(
                        "storage_recovery_pending_memory_space_delete_$falseMarkerId"
                    )
                ] = false
                preferences[
                    stringPreferencesKey(
                        "storage_recovery_pending_memory_space_delete_$wrongTypeMarkerId"
                    )
                ] = "true"
            }
            val manager = UserPreferencesManager.getInstance(context)

            manager.completePendingMemorySpaceDeletions()
            val repaired = store.data.first()
            val repairedIds =
                Json.parseToJsonElement(
                    requireNotNull(repaired[stringPreferencesKey("memory_space_list")])
                ).jsonArray.map { it.jsonPrimitive.content }
            assertTrue(falseMarkerId in repairedIds)
            assertTrue(wrongTypeMarkerId in repairedIds)
            assertTrue(repaired[stringPreferencesKey("memory_space_$falseMarkerId")] != null)
            assertTrue(repaired[stringPreferencesKey("memory_space_$wrongTypeMarkerId")] != null)
            assertFalse(
                repaired.asMap().keys.any { key ->
                    key.name.startsWith("storage_recovery_pending_memory_space_delete_")
                }
            )

            val firstRepairedState = repaired.asMap()
            manager.completePendingMemorySpaceDeletions()
            assertEquals(firstRepairedState, store.data.first().asMap())
        } finally {
            store.edit { preferences -> preferences.clear() }
        }
    }
}
