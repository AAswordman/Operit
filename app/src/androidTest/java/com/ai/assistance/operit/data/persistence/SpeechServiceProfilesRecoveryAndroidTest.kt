package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.HttpTtsResponsePipelineStep
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
class SpeechServiceProfilesRecoveryAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profileStore
        get() =
            RecoverablePreferenceDataStores.get(
                context,
                PreferenceStoreCatalog.SPEECH_SERVICE_PROFILES,
            )
    private val legacyStore
        get() =
            RecoverablePreferenceDataStores.get(
                context,
                PreferenceStoreCatalog.SPEECH_SERVICES,
            )

    @Test
    fun releasedSpeechSettingsMigrateExactlyOnce() = runBlocking {
        profileStore.edit { preferences -> preferences.clear() }
        legacyStore.edit { preferences ->
            preferences.clear()
            preferences[SpeechServicesPreferences.TTS_SERVICE_TYPE] =
                VoiceServiceFactory.VoiceServiceType.HTTP_TTS.name
            preferences[SpeechServicesPreferences.TTS_HTTP_CONFIG] =
                """{"urlTemplate":"https://migration.invalid/tts","apiKey":"fixture-key","headers":{"X-Fixture":"migration"},"httpMethod":"POST","requestBody":"{text}","contentType":"application/json","localeTag":"en-US","voiceId":"fixture-voice","modelName":"fixture-model","responsePipeline":[{"type":"pick","path":" result.items[0] "},{"type":"pick","path":"items[nope]"}]}"""
            preferences[SpeechServicesPreferences.TTS_VITS_PACKAGE_CONFIG] =
                """{"packagePath":"fixture-package","speakerId":"fixture-speaker","options":{"fixture":"value"}}"""
            preferences[SpeechServicesPreferences.TTS_CLEANER_REGEXS] =
                setOf(" fixture.* ")
            preferences[SpeechServicesPreferences.TTS_SPEECH_RATE] = 1.25f
            preferences[SpeechServicesPreferences.TTS_PITCH] = 0.75f
            preferences[SpeechServicesPreferences.STT_SERVICE_TYPE] =
                SpeechServiceFactory.SpeechServiceType.OPENAI_STT.name
            preferences[SpeechServicesPreferences.STT_HTTP_CONFIG] =
                """{"endpointUrl":"https://migration.invalid/stt","apiKey":"fixture-key","modelName":"fixture-stt"}"""
        }

        val profiles = SpeechServiceProfilesPreferences(context)
        assertFalse(profiles.initializeAndRepair())

        val migratedTts = profiles.ttsProfilesFlow.first().single()
        val migratedStt = profiles.sttProfilesFlow.first().single()
        assertEquals(VoiceServiceFactory.VoiceServiceType.HTTP_TTS, migratedTts.serviceType)
        assertEquals("https://migration.invalid/tts", migratedTts.httpConfig.urlTemplate)
        assertEquals("POST", migratedTts.httpConfig.httpMethod)
        assertEquals(listOf("fixture.*"), migratedTts.cleanerRegexs)
        assertEquals(
            listOf("result.items[0]"),
            migratedTts.httpConfig.responsePipeline.map { it.path },
        )
        assertEquals(1.25f, migratedTts.speechRate, 0f)
        assertEquals(0.75f, migratedTts.pitch, 0f)
        assertEquals(SpeechServiceFactory.SpeechServiceType.OPENAI_STT, migratedStt.serviceType)
        assertEquals("https://migration.invalid/stt", migratedStt.httpConfig.endpointUrl)
        assertEquals(migratedTts.id, profiles.currentTtsProfileIdFlow.first())
        assertEquals(migratedStt.id, profiles.currentSttProfileIdFlow.first())

        val firstPersistedState = profileStore.data.first().asMap()
        assertEquals(
            1,
            firstPersistedState[intPreferencesKey("speech_profiles_migration_version")],
        )
        legacyStore.edit { preferences ->
            preferences[SpeechServicesPreferences.TTS_SERVICE_TYPE] =
                VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS.name
            preferences[SpeechServicesPreferences.TTS_SPEECH_RATE] = 2.0f
            preferences[SpeechServicesPreferences.STT_SERVICE_TYPE] =
                SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT.name
        }

        assertFalse(profiles.initializeAndRepair())
        assertEquals(firstPersistedState, profileStore.data.first().asMap())
        assertEquals(
            VoiceServiceFactory.VoiceServiceType.HTTP_TTS,
            profiles.getCurrentTtsProfile().serviceType,
        )
        assertEquals(
            SpeechServiceFactory.SpeechServiceType.OPENAI_STT,
            profiles.getCurrentSttProfile().serviceType,
        )
    }

    @Test
    fun unreadableProfileListsCreateDefaultsAndRepairDanglingIds() = runBlocking {
        profileStore.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("speech_profiles_migration_version")] = 1
            preferences[stringPreferencesKey("tts_profiles")] = "{broken"
            preferences[stringPreferencesKey("stt_profiles")] = "false"
            preferences[stringPreferencesKey("current_tts_profile_id")] = "missing-tts"
            preferences[stringPreferencesKey("current_stt_profile_id")] = "missing-stt"
            preferences[stringPreferencesKey("future_store_field")] = "preserved"
        }
        val profiles = SpeechServiceProfilesPreferences(context)

        assertTrue(profiles.initializeAndRepair())
        assertFalse(profiles.initializeAndRepair())
        assertEquals(listOf("default-tts-profile"), profiles.ttsProfilesFlow.first().map { it.id })
        assertEquals(listOf("default-stt-profile"), profiles.sttProfilesFlow.first().map { it.id })
        assertEquals("default-tts-profile", profiles.currentTtsProfileIdFlow.first())
        assertEquals("default-stt-profile", profiles.currentSttProfileIdFlow.first())
        assertNotNull(profiles.getCurrentTtsProfile())
        assertNotNull(profiles.getCurrentSttProfile())
        assertEquals(
            "preserved",
            profileStore.data.first()[stringPreferencesKey("future_store_field")],
        )
    }

    @Test
    fun profileRepairNormalizesKnownFieldsPreservesFutureFieldsAndConverges() = runBlocking {
        profileStore.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("speech_profiles_migration_version")] = 1
            preferences[stringPreferencesKey("tts_profiles")] = CORRUPT_TTS_PROFILES
            preferences[stringPreferencesKey("stt_profiles")] = CORRUPT_STT_PROFILES
            preferences[stringPreferencesKey("current_tts_profile_id")] = "missing-tts"
            preferences[stringPreferencesKey("current_stt_profile_id")] = "missing-stt"
        }
        val profiles = SpeechServiceProfilesPreferences(context)

        assertTrue(profiles.initializeAndRepair())
        assertFalse(profiles.initializeAndRepair())

        val ttsProfiles = profiles.ttsProfilesFlow.first()
        assertEquals(
            setOf("stable-tts", "repaired-tts-profile-1", "repaired-tts-profile-2"),
            ttsProfiles.map { it.id }.toSet(),
        )
        assertEquals(3, ttsProfiles.size)
        assertEquals("stable-tts", profiles.currentTtsProfileIdFlow.first())
        assertEquals("Stable TTS", ttsProfiles.single { it.id == "stable-tts" }.name)
        ttsProfiles.forEach { profile ->
            assertTrue(profile.name.isNotBlank())
            assertTrue(profile.speechRate.isFinite() && profile.speechRate in 0.5f..2.0f)
            assertTrue(profile.pitch.isFinite() && profile.pitch in 0.5f..2.0f)
            profile.cleanerRegexs.forEach { expression -> Regex(expression) }
            assertTrue(profile.httpConfig.httpMethod in setOf("GET", "POST"))
            profile.httpConfig.responsePipeline.forEach { step ->
                assertTrue(step.type in HttpTtsResponsePipelineStep.SUPPORTED_TYPES)
                assertTrue(
                    step.type != HttpTtsResponsePipelineStep.TYPE_PICK || step.path.isNotBlank(),
                )
            }
        }
        val invalidScalarProfile = ttsProfiles.single { it.id == "repaired-tts-profile-1" }
        assertEquals(VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS, invalidScalarProfile.serviceType)
        assertEquals(
            SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE,
            invalidScalarProfile.speechRate,
            0f,
        )
        assertEquals(
            SpeechServicesPreferences.DEFAULT_TTS_PITCH,
            invalidScalarProfile.pitch,
            0f,
        )
        assertEquals(listOf("valid.*"), invalidScalarProfile.cleanerRegexs)
        assertTrue(invalidScalarProfile.httpConfig.responsePipeline.isEmpty())
        assertTrue(
            ttsProfiles
                .single { it.id == "repaired-tts-profile-2" }
                .httpConfig
                .responsePipeline
                .isEmpty(),
        )

        val sttProfiles = profiles.sttProfilesFlow.first()
        assertEquals(
            setOf("stable-stt", "repaired-stt-profile-1"),
            sttProfiles.map { it.id }.toSet(),
        )
        assertEquals(2, sttProfiles.size)
        assertEquals("stable-stt", profiles.currentSttProfileIdFlow.first())
        assertEquals("Stable STT", sttProfiles.single { it.id == "stable-stt" }.name)
        assertEquals(
            SpeechServicesPreferences.DEFAULT_STT_SERVICE_TYPE,
            sttProfiles.single { it.id == "repaired-stt-profile-1" }.serviceType,
        )
        assertEquals(
            SpeechServicesPreferences.DEFAULT_STT_HTTP_PRESET,
            sttProfiles.single { it.id == "repaired-stt-profile-1" }.httpConfig,
        )

        val persisted = profileStore.data.first()
        val persistedTts =
            Json.parseToJsonElement(
                requireNotNull(persisted[stringPreferencesKey("tts_profiles")]),
            ).jsonArray
        val stableTts =
            persistedTts.single { element ->
                element.jsonObject["id"]?.jsonPrimitive?.content == "stable-tts"
            }.jsonObject
        assertEquals("preserve-profile", stableTts["futureProfileField"]?.jsonPrimitive?.content)
        val stableHttp = requireNotNull(stableTts["httpConfig"]).jsonObject
        assertEquals("preserve-http", stableHttp["futureHttpField"]?.jsonPrimitive?.content)
        assertEquals("POST", stableHttp["httpMethod"]?.jsonPrimitive?.content)
        assertEquals(
            "preserve-step",
            stableHttp["responsePipeline"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("futureStepField")
                ?.jsonPrimitive
                ?.content,
        )
        val stableVits = requireNotNull(stableTts["vitsConfig"]).jsonObject
        assertEquals("preserve-vits", stableVits["futureVitsField"]?.jsonPrimitive?.content)

        val persistedStt =
            Json.parseToJsonElement(
                requireNotNull(persisted[stringPreferencesKey("stt_profiles")]),
            ).jsonArray
        val stableStt =
            persistedStt.single { element ->
                element.jsonObject["id"]?.jsonPrimitive?.content == "stable-stt"
            }.jsonObject
        assertEquals("preserve-stt", stableStt["futureProfileField"]?.jsonPrimitive?.content)
        assertEquals(
            "preserve-stt-http",
            stableStt["httpConfig"]
                ?.jsonObject
                ?.get("futureHttpField")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun profileUpdatesPreserveFutureTimestampsAndRemainRepairIdempotent() = runBlocking {
        profileStore.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("speech_profiles_migration_version")] = 1
            preferences[stringPreferencesKey("tts_profiles")] = FUTURE_TIMESTAMP_TTS_PROFILES
            preferences[stringPreferencesKey("stt_profiles")] = FUTURE_TIMESTAMP_STT_PROFILES
            preferences[stringPreferencesKey("current_tts_profile_id")] = "future-tts"
            preferences[stringPreferencesKey("current_stt_profile_id")] = "future-stt"
        }
        val profiles = SpeechServiceProfilesPreferences(context)

        assertFalse(profiles.initializeAndRepair())
        val originalTts = profiles.getCurrentTtsProfile()
        val originalStt = profiles.getCurrentSttProfile()

        val updatedTts = profiles.updateTtsProfile(originalTts.copy(name = "Updated future TTS"))
        val updatedStt = profiles.updateSttProfile(originalStt.copy(name = "Updated future STT"))

        assertEquals(FUTURE_CREATED_AT, updatedTts.createdAt)
        assertEquals(FUTURE_UPDATED_AT, updatedTts.updatedAt)
        assertTrue(updatedTts.updatedAt >= updatedTts.createdAt)
        assertEquals(FUTURE_CREATED_AT, updatedStt.createdAt)
        assertEquals(FUTURE_UPDATED_AT, updatedStt.updatedAt)
        assertTrue(updatedStt.updatedAt >= updatedStt.createdAt)
        assertEquals(updatedTts, profiles.getCurrentTtsProfile())
        assertEquals(updatedStt, profiles.getCurrentSttProfile())

        // A write API must not emit timestamp state that the next repair pass changes.
        val persistedAfterUpdates = profileStore.data.first().asMap()
        assertFalse(profiles.initializeAndRepair())
        assertEquals(persistedAfterUpdates, profileStore.data.first().asMap())
    }

    @Test
    fun profileCreationRequiresStoredTemplatesAndCopiesOnlyTheirFutureFields() = runBlocking {
        profileStore.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("speech_profiles_migration_version")] = 1
            preferences[stringPreferencesKey("tts_profiles")] = TEMPLATE_TTS_PROFILES
            preferences[stringPreferencesKey("stt_profiles")] = TEMPLATE_STT_PROFILES
            preferences[stringPreferencesKey("current_tts_profile_id")] = "active-tts"
            preferences[stringPreferencesKey("current_stt_profile_id")] = "active-stt"
        }
        val profiles = SpeechServiceProfilesPreferences(context)

        assertFalse(profiles.initializeAndRepair())
        val ttsTemplate = profiles.ttsProfilesFlow.first().single { it.id == "template-tts" }
        val sttTemplate = profiles.sttProfilesFlow.first().single { it.id == "template-stt" }
        val beforeRejectedCreates = profileStore.data.first().asMap()

        assertIllegalState {
            profiles.createTtsProfile(
                "Rejected external TTS",
                ttsTemplate.copy(id = "external-tts-template"),
            )
        }
        assertEquals(beforeRejectedCreates, profileStore.data.first().asMap())
        assertIllegalState {
            profiles.createSttProfile(
                "Rejected external STT",
                sttTemplate.copy(id = "external-stt-template"),
            )
        }
        assertEquals(beforeRejectedCreates, profileStore.data.first().asMap())

        val copiedTts = profiles.createTtsProfile("Copied TTS", ttsTemplate)
        val copiedStt = profiles.createSttProfile("Copied STT", sttTemplate)
        assertEquals(ttsTemplate.serviceType, copiedTts.serviceType)
        assertEquals(ttsTemplate.httpConfig, copiedTts.httpConfig)
        assertEquals(sttTemplate.serviceType, copiedStt.serviceType)
        assertEquals(sttTemplate.httpConfig, copiedStt.httpConfig)

        val persisted = profileStore.data.first()
        val copiedTtsRaw =
            Json.parseToJsonElement(requireNotNull(persisted[stringPreferencesKey("tts_profiles")]))
                .jsonArray
                .single { it.jsonObject["id"]?.jsonPrimitive?.content == copiedTts.id }
                .jsonObject
        val copiedTtsHttp = requireNotNull(copiedTtsRaw["httpConfig"]).jsonObject
        assertEquals("template-tts-profile", copiedTtsRaw["futureProfileField"]?.jsonPrimitive?.content)
        assertEquals("template-tts-http", copiedTtsHttp["futureHttpField"]?.jsonPrimitive?.content)
        assertFalse("activeOnlyProfileField" in copiedTtsRaw)
        assertFalse("activeOnlyHttpField" in copiedTtsHttp)

        val copiedSttRaw =
            Json.parseToJsonElement(requireNotNull(persisted[stringPreferencesKey("stt_profiles")]))
                .jsonArray
                .single { it.jsonObject["id"]?.jsonPrimitive?.content == copiedStt.id }
                .jsonObject
        val copiedSttHttp = requireNotNull(copiedSttRaw["httpConfig"]).jsonObject
        assertEquals("template-stt-profile", copiedSttRaw["futureProfileField"]?.jsonPrimitive?.content)
        assertEquals("template-stt-http", copiedSttHttp["futureHttpField"]?.jsonPrimitive?.content)
        assertFalse("activeOnlyProfileField" in copiedSttRaw)
        assertFalse("activeOnlyHttpField" in copiedSttHttp)
    }

    @Test
    fun profileStoreRestoresAValidSnapshotAfterPhysicalCorruption() = runBlocking {
        profileStore.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("speech_profiles_migration_version")] = 1
            preferences[stringPreferencesKey("tts_profiles")] = VALID_TTS_PROFILES
            preferences[stringPreferencesKey("stt_profiles")] = VALID_STT_PROFILES
            preferences[stringPreferencesKey("current_tts_profile_id")] = "physical-tts"
            preferences[stringPreferencesKey("current_stt_profile_id")] = "physical-stt"
            preferences[stringPreferencesKey("physical_recovery_marker")] = "preserved"
        }
        RecoverablePreferenceDataStores.closeAllAndAwait()
        StorageRecoveryFaultFixtures.corruptPreferenceStore(
            context,
            PreferenceStoreCatalog.SPEECH_SERVICE_PROFILES,
        )

        val profiles = SpeechServiceProfilesPreferences(context)
        assertFalse(profiles.initializeAndRepair())
        assertEquals("physical-tts", profiles.getCurrentTtsProfile().id)
        assertEquals("physical-stt", profiles.getCurrentSttProfile().id)
        assertEquals(
            "preserved",
            profileStore.data.first()[stringPreferencesKey("physical_recovery_marker")],
        )
    }

    private suspend fun assertIllegalState(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            return
        }
        throw AssertionError("Expected IllegalStateException")
    }

    private companion object {
        private const val FUTURE_CREATED_AT = 8_999_999_999_999_999_000L
        private const val FUTURE_UPDATED_AT = 9_000_000_000_000_000_000L

        val FUTURE_TIMESTAMP_TTS_PROFILES =
            """
            [{"id":"future-tts","name":"Future TTS","serviceType":"SIMPLE_TTS","httpConfig":{"urlTemplate":"","apiKey":"","headers":{},"httpMethod":"GET"},"vitsConfig":{},"cleanerRegexs":[],"speechRate":1.0,"pitch":1.0,"createdAt":$FUTURE_CREATED_AT,"updatedAt":$FUTURE_UPDATED_AT}]
            """.trimIndent()

        val FUTURE_TIMESTAMP_STT_PROFILES =
            """
            [{"id":"future-stt","name":"Future STT","serviceType":"SHERPA_NCNN","httpConfig":{"endpointUrl":"","apiKey":"","modelName":""},"createdAt":$FUTURE_CREATED_AT,"updatedAt":$FUTURE_UPDATED_AT}]
            """.trimIndent()

        val TEMPLATE_TTS_PROFILES =
            """
            [
              {
                "id":"active-tts",
                "name":"Active TTS",
                "serviceType":"SIMPLE_TTS",
                "httpConfig":{"urlTemplate":"https://active.example.invalid/tts","apiKey":"","headers":{},"httpMethod":"GET","futureHttpField":"active-tts-http","activeOnlyHttpField":"active-tts-only"},
                "vitsConfig":{},
                "cleanerRegexs":[],
                "speechRate":1.0,
                "pitch":1.0,
                "createdAt":1,
                "updatedAt":1,
                "futureProfileField":"active-tts-profile",
                "activeOnlyProfileField":"active-tts-only"
              },
              {
                "id":"template-tts",
                "name":"Template TTS",
                "serviceType":"HTTP_TTS",
                "httpConfig":{"urlTemplate":"https://template.example.invalid/tts","apiKey":"","headers":{},"httpMethod":"POST","futureHttpField":"template-tts-http"},
                "vitsConfig":{},
                "cleanerRegexs":["template.*"],
                "speechRate":1.25,
                "pitch":0.75,
                "createdAt":2,
                "updatedAt":2,
                "futureProfileField":"template-tts-profile"
              }
            ]
            """.trimIndent()

        val TEMPLATE_STT_PROFILES =
            """
            [
              {
                "id":"active-stt",
                "name":"Active STT",
                "serviceType":"SHERPA_NCNN",
                "httpConfig":{"endpointUrl":"https://active.example.invalid/stt","apiKey":"","modelName":"active-model","futureHttpField":"active-stt-http","activeOnlyHttpField":"active-stt-only"},
                "createdAt":1,
                "updatedAt":1,
                "futureProfileField":"active-stt-profile",
                "activeOnlyProfileField":"active-stt-only"
              },
              {
                "id":"template-stt",
                "name":"Template STT",
                "serviceType":"OPENAI_STT",
                "httpConfig":{"endpointUrl":"https://template.example.invalid/stt","apiKey":"","modelName":"template-model","futureHttpField":"template-stt-http"},
                "createdAt":2,
                "updatedAt":2,
                "futureProfileField":"template-stt-profile"
              }
            ]
            """.trimIndent()

        val CORRUPT_TTS_PROFILES =
            """
            [
              {
                "id":"stable-tts",
                "name":" Stable TTS ",
                "serviceType":"HTTP_TTS",
                "httpConfig":{
                  "urlTemplate":"https://profiles.invalid/tts",
                  "apiKey":"fixture-key",
                  "headers":{},
                  "httpMethod":" post ",
                  "responsePipeline":[{"type":" PARSE_JSON ","futureStepField":"preserve-step"}],
                  "futureHttpField":"preserve-http"
                },
                "vitsConfig":{"futureVitsField":"preserve-vits"},
                "cleanerRegexs":["stable.*"],
                "speechRate":1.25,
                "pitch":0.75,
                "createdAt":1,
                "updatedAt":2,
                "futureProfileField":"preserve-profile"
              },
              {
                "id":"stable-tts",
                "name":"",
                "serviceType":"BROKEN_TTS",
                "httpConfig":{
                  "urlTemplate":"https://profiles.invalid/broken",
                  "apiKey":"fixture-key",
                  "headers":{},
                  "httpMethod":"PATCH",
                  "responsePipeline":[{"type":"pick","path":""},{"type":"broken"}]
                },
                "vitsConfig":{},
                "cleanerRegexs":["[","valid.*","valid.*",""],
                "speechRate":1e1000,
                "pitch":-1e1000,
                "createdAt":3,
                "updatedAt":2
              },
              {
                "id":"",
                "name":"Empty identifier",
                "serviceType":"SIMPLE_TTS",
                "httpConfig":{"urlTemplate":"","apiKey":"","headers":{},"httpMethod":"GET","responsePipeline":[{"type":"pick","path":"items[nope]"}]},
                "vitsConfig":{},
                "cleanerRegexs":[],
                "speechRate":0.1,
                "pitch":1e1000,
                "createdAt":4,
                "updatedAt":4
              },
              42
            ]
            """.trimIndent()

        val CORRUPT_STT_PROFILES =
            """
            [
              {
                "id":"stable-stt",
                "name":" Stable STT ",
                "serviceType":"OPENAI_STT",
                "httpConfig":{
                  "endpointUrl":"https://profiles.invalid/stt",
                  "apiKey":"fixture-key",
                  "modelName":"fixture-model",
                  "futureHttpField":"preserve-stt-http"
                },
                "createdAt":1,
                "updatedAt":2,
                "futureProfileField":"preserve-stt"
              },
              {
                "id":"stable-stt",
                "name":"",
                "serviceType":"BROKEN_STT",
                "httpConfig":{"endpointUrl":4,"apiKey":false,"modelName":[]},
                "createdAt":3,
                "updatedAt":2
              },
              false
            ]
            """.trimIndent()

        val VALID_TTS_PROFILES =
            """
            [{"id":"physical-tts","name":"Physical TTS","serviceType":"SIMPLE_TTS","httpConfig":{"urlTemplate":"","apiKey":"","headers":{},"httpMethod":"GET"},"vitsConfig":{},"cleanerRegexs":[],"speechRate":1.0,"pitch":1.0,"createdAt":1,"updatedAt":1}]
            """.trimIndent()

        val VALID_STT_PROFILES =
            """
            [{"id":"physical-stt","name":"Physical STT","serviceType":"SHERPA_NCNN","httpConfig":{"endpointUrl":"","apiKey":"","modelName":""},"createdAt":1,"updatedAt":1}]
            """.trimIndent()
    }
}
