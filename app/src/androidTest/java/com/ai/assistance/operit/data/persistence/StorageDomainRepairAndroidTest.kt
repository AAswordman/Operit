package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageDomainRepairAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun speechRepairPersistsCanonicalValuesAndConverges() = runBlocking {
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.SPEECH_SERVICES)
        store.edit { preferences ->
            preferences.clear()
            preferences[stringPreferencesKey("tts_service_type")] = "BROKEN_TTS"
            preferences[stringPreferencesKey("tts_http_config")] = "{broken"
            preferences[floatPreferencesKey("tts_speech_rate")] = Float.NaN
        }
        val speech = SpeechServicesPreferences(context)

        assertTrue(speech.repairPersistedState())
        assertFalse(speech.repairPersistedState())
        assertEquals(
            SpeechServicesPreferences.DEFAULT_TTS_SERVICE_TYPE,
            speech.ttsServiceTypeFlow.first()
        )
        assertEquals(
            SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE,
            speech.ttsSpeechRateFlow.first()
        )

        val futureCompatibleHttpConfig =
            """{"urlTemplate":"","apiKey":"","headers":{},"httpMethod":"get","futureField":"preserved"}"""
        store.edit { preferences ->
            preferences[stringPreferencesKey("tts_http_config")] = futureCompatibleHttpConfig
        }
        assertTrue(speech.repairPersistedState())
        assertFalse(speech.repairPersistedState())
        val repairedHttpConfig =
            Json.parseToJsonElement(
                requireNotNull(store.data.first()[stringPreferencesKey("tts_http_config")])
            ).jsonObject
        assertEquals(
            "preserved",
            repairedHttpConfig["futureField"]?.jsonPrimitive?.content
        )
        assertEquals("GET", speech.ttsHttpConfigFlow.first().httpMethod)
    }

    @Test
    fun modelRepairRecreatesDefaultConfigurationAndConverges() = runBlocking {
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.MODEL_CONFIGS)
        store.edit { preferences ->
            preferences.clear()
            preferences[stringPreferencesKey("config_list")] = "not-json"
            preferences[stringPreferencesKey("config_unreadable")] = "{broken"
        }
        val manager = ModelConfigManager(context)

        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        assertTrue(ModelConfigManager.DEFAULT_CONFIG_ID in manager.configListFlow.first())
        assertTrue("unreadable" in manager.configListFlow.first())
        val defaultConfig = manager.getModelConfig(ModelConfigManager.DEFAULT_CONFIG_ID)
        assertNotNull(defaultConfig)
        assertNotNull(manager.getModelConfig("unreadable"))

        val defaultKey = stringPreferencesKey("config_${ModelConfigManager.DEFAULT_CONFIG_ID}")
        val persistedDefault =
            Json.parseToJsonElement(requireNotNull(store.data.first()[defaultKey])).jsonObject
        store.edit { preferences ->
            preferences[defaultKey] =
                JsonObject(
                    persistedDefault +
                        ("name" to JsonPrimitive("")) +
                        ("futureField" to JsonPrimitive("preserved"))
                ).toString()
        }
        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        val normalizedDefault =
            Json.parseToJsonElement(requireNotNull(store.data.first()[defaultKey])).jsonObject
        assertEquals(
            "preserved",
            normalizedDefault["futureField"]?.jsonPrimitive?.content
        )

        manager.saveModelConfig(
            requireNotNull(defaultConfig).copy(
                hasCustomParameters = true,
                customParameters =
                    """[{"id":"known","name":"known","apiName":"known","defaultValue":"1","currentValue":"1","isEnabled":true,"valueType":"int","category":"other","futureParameterField":true}]"""
            )
        )
        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        val normalizedParameters =
            Json.parseToJsonElement(
                requireNotNull(
                    manager.getModelConfig(ModelConfigManager.DEFAULT_CONFIG_ID)?.customParameters
                )
            ).jsonArray
        assertEquals(
            true,
            normalizedParameters.single().jsonObject["futureParameterField"]
                ?.jsonPrimitive
                ?.content
                ?.toBooleanStrict()
        )

        manager.saveModelConfig(
            requireNotNull(defaultConfig).copy(
                hasCustomParameters = true,
                customParameters =
                    """[{"id":"bad","name":"bad","apiName":"bad","defaultValue":"x","currentValue":"x","isEnabled":true,"valueType":"INT","category":"OTHER"}]"""
            )
        )
        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        assertEquals(
            "[]",
            manager.getModelConfig(ModelConfigManager.DEFAULT_CONFIG_ID)?.customParameters
        )
    }

    @Test
    fun apiBookmarkRepairRetainsValidFutureFields() = runBlocking {
        val store = RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.API_SETTINGS)
        val bookmarkKey = stringPreferencesKey("saf_bookmarks_json")
        store.edit { preferences ->
            preferences[bookmarkKey] =
                """[{"uri":"content://valid","name":"Valid","futureField":true},{"uri":4}]"""
        }
        val manager = ApiPreferences.getInstance(context)

        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        assertEquals(listOf("content://valid"), manager.safBookmarksFlow.first().map { it.uri })
        manager.addSafBookmark("content://valid", "Updated")

        val repaired =
            Json.parseToJsonElement(requireNotNull(store.data.first()[bookmarkKey])).jsonArray
        assertEquals(1, repaired.size)
        assertEquals(
            true,
            repaired.single().jsonObject["futureField"]
                ?.jsonPrimitive
                ?.content
                ?.toBooleanStrict()
        )
        assertEquals(
            "Updated",
            repaired.single().jsonObject["name"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun functionalMappingRepairPreservesFutureFunctionEntries() = runBlocking {
        ModelConfigManager(context).repairPersistedState()
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.FUNCTIONAL_CONFIGS)
        val futureEntry =
            Json.parseToJsonElement(
                """{"configId":"future-config","modelIndex":7,"futureField":true}"""
            )
        store.edit { preferences ->
            preferences.clear()
            preferences[stringPreferencesKey("function_config_mapping")] =
                """{"FUTURE_FUNCTION":$futureEntry}"""
        }
        val manager = FunctionalConfigManager(context)

        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        val repaired =
            requireNotNull(
                store.data.first()[stringPreferencesKey("function_config_mapping")]
            )
        assertEquals(
            futureEntry,
            Json.parseToJsonElement(repaired).jsonObject["FUTURE_FUNCTION"]
        )

        manager.setConfigForFunction(
            FunctionType.CHAT,
            FunctionalConfigManager.DEFAULT_CONFIG_ID,
            0
        )
        val saved =
            requireNotNull(
                store.data.first()[stringPreferencesKey("function_config_mapping")]
            )
        assertEquals(
            futureEntry,
            Json.parseToJsonElement(saved).jsonObject["FUTURE_FUNCTION"]
        )
    }

    @Test
    fun characterRepairFixesBrokenReferencesAndConverges() = runBlocking {
        ModelConfigManager(context).repairPersistedState()
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.CHARACTER_CARDS)
        store.edit { preferences ->
            preferences.clear()
            preferences[stringSetPreferencesKey("character_card_list")] =
                setOf("fixture", "index_only")
            preferences[stringPreferencesKey("character_card_fixture_name")] = "Fixture"
            preferences[stringPreferencesKey("character_card_fixture_chat_model_binding_mode")] =
                CharacterCardChatModelBindingMode.FIXED_CONFIG
            preferences[stringPreferencesKey("character_card_fixture_chat_model_config_id")] =
                "missing"
            preferences[intPreferencesKey("character_card_fixture_chat_model_index")] = -4
            preferences[booleanPreferencesKey("character_card_fixture_is_default")] = true
            preferences[stringPreferencesKey("character_card_fixture_tool_access_config_json")] =
                """{"enabled":true,"allowedBuiltinTools":[" shell ","shell"],"futureField":true}"""
            preferences[stringPreferencesKey("active_character_card_id")] = "missing"
        }
        val manager = CharacterCardManager.getInstance(context)

        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        val repaired = manager.getCharacterCard("fixture")
        assertEquals(CharacterCardChatModelBindingMode.FOLLOW_GLOBAL, repaired.chatModelBindingMode)
        assertEquals(0, repaired.chatModelIndex)
        assertFalse(repaired.isDefault)
        assertEquals("index_only", manager.getCharacterCard("index_only").name)
        val repairedToolAccess =
            Json.parseToJsonElement(
                requireNotNull(
                    store.data.first()[
                        stringPreferencesKey("character_card_fixture_tool_access_config_json")
                    ]
                )
            ).jsonObject
        assertEquals(
            true,
            repairedToolAccess["futureField"]?.jsonPrimitive?.content?.toBooleanStrict()
        )
    }

    @Test
    fun characterGroupRepairReconstructsBrokenRecordsAndConverges() = runBlocking {
        val characterStore =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.CHARACTER_CARDS)
        characterStore.edit { preferences ->
            preferences.clear()
            preferences[stringSetPreferencesKey("character_card_list")] =
                setOf(
                    CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
                    "group_order_fixture"
                )
        }
        CharacterCardManager.getInstance(context).repairPersistedState()
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.CHARACTER_GROUPS)
        store.edit { preferences ->
            preferences.clear()
            preferences[stringSetPreferencesKey("character_group_list")] =
                setOf("index_only", "future")
            preferences[stringPreferencesKey("character_group_broken_data")] = "{broken"
            preferences[stringPreferencesKey("character_group_future_data")] =
                """{"id":"wrong","name":"Future","description":"","members":[{"characterCardId":"group_order_fixture","orderIndex":1},{"characterCardId":"${CharacterCardManager.DEFAULT_CHARACTER_CARD_ID}","orderIndex":0}],"createdAt":1,"updatedAt":1,"futureField":true}"""
        }
        val manager = CharacterGroupCardManager.getInstance(context)

        assertTrue(manager.repairPersistedState())
        assertFalse(manager.repairPersistedState())
        assertEquals("index_only", manager.getCharacterGroupCard("index_only")?.name)
        assertEquals("broken", manager.getCharacterGroupCard("broken")?.name)
        assertEquals(
            listOf(
                CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
                "group_order_fixture"
            ),
            manager.getCharacterGroupCard("future")?.members?.map { member ->
                member.characterCardId
            }
        )
        val repairedFuture =
            Json.parseToJsonElement(
                requireNotNull(
                    store.data.first()[stringPreferencesKey("character_group_future_data")]
                )
            ).jsonObject
        assertEquals("future", repairedFuture["id"]?.jsonPrimitive?.content)
        assertEquals(
            true,
            repairedFuture["futureField"]?.jsonPrimitive?.content?.toBooleanStrict()
        )
    }

    @Test
    fun memorySpaceRepairReconstructsMetadataAndConverges() = runBlocking {
        val diskProfileId = "memory_recovery_fixture"
        val futureCompatibleMemorySpace =
            """{"id":"future","name":"","futureField":"preserved"}"""
        try {
            ObjectBoxManager.get(context, diskProfileId).validate(0L, true)
            ObjectBoxManager.close(diskProfileId)
            val store =
                RecoverablePreferenceDataStores.get(
                    context,
                    PreferenceStoreCatalog.USER_PREFERENCES
                )
            store.edit { preferences ->
                preferences.clear()
                preferences[stringPreferencesKey("memory_space_list")] =
                    "[\"fixture\",\"future\",\"../escape\"]"
                preferences[stringPreferencesKey("memory_space_fixture")] = "{broken"
                preferences[stringPreferencesKey("memory_space_future")] =
                    futureCompatibleMemorySpace
                preferences[stringPreferencesKey("memory_space_../escape")] =
                    """{"id":"../escape","name":"Unsafe"}"""
                preferences[stringPreferencesKey("active_memory_space_id")] = "missing"
            }
            val manager = UserPreferencesManager.getInstance(context)

            assertTrue(manager.repairPersistedState())
            assertFalse(manager.repairPersistedState())
            assertEquals("fixture", manager.getMemorySpaceFlow("fixture").first().id)
            assertEquals("future", manager.getMemorySpaceFlow("future").first().id)
            assertEquals(diskProfileId, manager.getMemorySpaceFlow(diskProfileId).first().id)
            assertEquals("default", manager.activeMemorySpaceIdFlow.first())
            assertFalse(
                store.data.first().asMap().keys.any { key ->
                    key.name == "memory_space_../escape"
                }
            )
            val repairedFutureSpace =
                Json.parseToJsonElement(
                    requireNotNull(
                        store.data.first()[stringPreferencesKey("memory_space_future")]
                    )
                ).jsonObject
            assertEquals(
                "preserved",
                repairedFutureSpace["futureField"]?.jsonPrimitive?.content
            )
            assertEquals("future", repairedFutureSpace["name"]?.jsonPrimitive?.content)
        } finally {
            ObjectBoxManager.delete(context, diskProfileId)
        }
    }

    @Test
    fun objectBoxArtifactDiscoveryRunsBeforeMemoryIndexRepair() = runBlocking {
        val profileId = "memory_preflight_order_fixture"
        val store =
            RecoverablePreferenceDataStores.get(context, PreferenceStoreCatalog.USER_PREFERENCES)
        try {
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            store.edit { preferences -> preferences.clear() }
            val liveDirectory = java.io.File(context.filesDir, "objectbox_$profileId")
            assertTrue(liveDirectory.deleteRecursively())

            StorageRecoveryCoordinator.recoverPreferences(context)

            assertTrue(
                java.io.File(
                    java.io.File(context.filesDir, "objectbox_$profileId"),
                    "data.mdb"
                ).isFile
            )
            assertEquals(
                profileId,
                UserPreferencesManager.getInstance(context)
                    .getMemorySpaceFlow(profileId)
                    .first()
                    .id
            )
        } finally {
            ObjectBoxManager.delete(context, profileId)
            store.edit { preferences -> preferences.clear() }
        }
    }

    @Test
    fun pendingMemorySpaceDeletionFinishesBeforeObjectBoxArtifactDiscovery() = runBlocking {
        val profileId = "memory_pending_delete_fixture"
        val cardId = "pending_delete_card_fixture"
        val userStore =
            RecoverablePreferenceDataStores.get(
                context,
                PreferenceStoreCatalog.USER_PREFERENCES
            )
        val characterStore =
            RecoverablePreferenceDataStores.get(
                context,
                PreferenceStoreCatalog.CHARACTER_CARDS
            )
        val profileDirectory =
            java.io.File(context.filesDir, "memory-space-profiles/$profileId")
        try {
            userStore.edit { preferences ->
                preferences[stringPreferencesKey("memory_space_list")] =
                    Json.encodeToString(listOf("default", profileId))
                preferences[stringPreferencesKey("memory_space_$profileId")] =
                    """{"id":"$profileId","name":"Pending deletion"}"""
                preferences[stringPreferencesKey("active_memory_space_id")] = profileId
            }
            assertTrue(profileDirectory.mkdirs() || profileDirectory.isDirectory)
            java.io.File(profileDirectory, "user.md").writeText("pending deletion fixture")
            ObjectBoxManager.get(context, profileId).validate(0L, true)
            ObjectBoxManager.close(profileId)
            characterStore.edit { preferences ->
                preferences[
                    stringPreferencesKey("character_card_${cardId}_memory_profile_binding_mode")
                ] = CharacterCardMemoryProfileBindingMode.FIXED_PROFILE
                preferences[
                    stringPreferencesKey("character_card_${cardId}_memory_profile_id")
                ] = profileId
            }

            val manager = UserPreferencesManager.getInstance(context)
            manager.beginMemorySpaceDeletion(profileId)
            assertTrue(java.io.File(context.filesDir, "objectbox_$profileId").isDirectory)

            StorageRecoveryCoordinator.recoverPreferences(context)

            val recoveredUserPreferences = userStore.data.first()
            val recoveredIds =
                Json.parseToJsonElement(
                    requireNotNull(
                        recoveredUserPreferences[stringPreferencesKey("memory_space_list")]
                    )
                ).jsonArray.map { element -> element.jsonPrimitive.content }
            assertFalse(profileId in recoveredIds)
            assertFalse(
                recoveredUserPreferences.asMap().keys.any { key ->
                    key.name == "memory_space_$profileId" ||
                        key.name ==
                            "storage_recovery_pending_memory_space_delete_$profileId"
                }
            )
            assertFalse(profileDirectory.exists())
            assertFalse(java.io.File(context.filesDir, "objectbox_$profileId").exists())
            assertFalse(
                profileId in ObjectBoxRecoveryStorage.profileIdsWithRecoveryArtifacts(context)
            )
            val recoveredCharacterPreferences = characterStore.data.first()
            assertEquals(
                CharacterCardMemoryProfileBindingMode.FOLLOW_GLOBAL,
                recoveredCharacterPreferences[
                    stringPreferencesKey(
                        "character_card_${cardId}_memory_profile_binding_mode"
                    )
                ]
            )
            assertFalse(
                recoveredCharacterPreferences.asMap().keys.any { key ->
                    key.name == "character_card_${cardId}_memory_profile_id"
                }
            )

            ObjectBoxManager.preflightAll(context)
            assertFalse(java.io.File(context.filesDir, "objectbox_$profileId").exists())
        } finally {
            ObjectBoxManager.delete(context, profileId)
            if (profileDirectory.exists()) profileDirectory.deleteRecursively()
            userStore.edit { preferences ->
                val rawIds = preferences[stringPreferencesKey("memory_space_list")]
                val ids =
                    rawIds?.let { encoded ->
                        Json.parseToJsonElement(encoded).jsonArray
                            .map { element -> element.jsonPrimitive.content }
                            .filterNot { id -> id == profileId }
                    }.orEmpty()
                preferences[stringPreferencesKey("memory_space_list")] =
                    Json.encodeToString(ids)
                preferences.remove(stringPreferencesKey("memory_space_$profileId"))
                preferences.remove(
                    booleanPreferencesKey(
                        "storage_recovery_pending_memory_space_delete_$profileId"
                    )
                )
                if (preferences[stringPreferencesKey("active_memory_space_id")] == profileId) {
                    preferences[stringPreferencesKey("active_memory_space_id")] = "default"
                }
            }
            characterStore.edit { preferences ->
                preferences.remove(
                    stringPreferencesKey(
                        "character_card_${cardId}_memory_profile_binding_mode"
                    )
                )
                preferences.remove(
                    stringPreferencesKey("character_card_${cardId}_memory_profile_id")
                )
                preferences.remove(
                    androidx.datastore.preferences.core.longPreferencesKey(
                        "character_card_${cardId}_updated_at"
                    )
                )
            }
        }
    }
}
