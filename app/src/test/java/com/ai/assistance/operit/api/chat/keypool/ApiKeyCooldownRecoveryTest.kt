package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.keypool.ApiKeyAvailabilityStatus
import com.ai.assistance.operit.api.chat.keypool.ApiKeyInfo
import com.ai.assistance.operit.data.config.model.ModelConfigData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiKeyCooldownRecoveryTest {
    private val now = 10_000L
    private val first = ApiKeyInfo(id = "first", key = "old-key")
    private val second = ApiKeyInfo(id = "second", key = "other-key")
    private val config = ModelConfigData(
        id = "config", name = "测试配置", modelName = "model-a",
        apiEndpoint = "https://example.com/v1", useMultipleApiKeys = true,
        apiKeyPool = listOf(first, second),
    )

    @Before
    fun reset() = ApiKeyCooldownStore.resetForTests()

    @After
    fun cleanup() = ApiKeyCooldownStore.resetForTests()

    @Test
    fun selectionRecoversAfterOneOrThreeMinutesWithoutClearingMarks() {
        listOf(
            ApiKeyErrorClass.AUTH to 60_000L,
            ApiKeyErrorClass.QUOTA to 60_000L,
            ApiKeyErrorClass.MODEL to 180_000L,
        ).forEach { (error, duration) ->
            ApiKeyCooldownStore.resetForTests()
            cool(first.id, error)
            fun candidates(time: Long) = ApiKeyPoolSelector.candidateCount(
                pool = listOf(first), excludedIds = emptySet(), nowMs = time, model = "model-a",
                cooldownUntilMs = { keyId, model ->
                    ApiKeyCooldownStore.cooldownUntilMs(config.id, keyId, model)
                },
            )
            assertEquals(0, candidates(now + duration - 1L))
            assertEquals(1, candidates(now + duration))
        }
    }

    @Test
    fun correctingKeyWithSameIdOnlyClearsThatKey() {
        cool(first.id)
        cool(second.id)
        cool(first.id, configId = "another-config")
        ApiKeyCooldownRecovery.onConfigSaved(
            config,
            config.copy(apiKeyPool = listOf(first.copy(key = "corrected-key"), second)),
        )
        assertEquals(0L, until(first.id))
        assertTrue(until(second.id) > now)
        assertTrue(ApiKeyCooldownStore.cooldownUntilMs("another-config", first.id, "model-a") > now)
    }

    @Test
    fun changingConnectionSettingsClearsOnlyCurrentConfig() {
        listOf(
            config.copy(apiEndpoint = "https://example.com/v1/chat/completions"),
            config.copy(modelName = "model-b"),
            config.copy(apiProviderTypeId = "OPENAI_GENERIC"),
        ).forEach { updated ->
            cool(first.id)
            cool(second.id)
            cool(first.id, configId = "another-config")
            ApiKeyCooldownRecovery.onConfigSaved(config, updated)
            assertEquals(0L, until(first.id))
            assertEquals(0L, until(second.id))
            assertTrue(ApiKeyCooldownStore.cooldownUntilMs("another-config", first.id, "model-a") > now)
        }
    }

    @Test
    fun successfulTestAndExplicitMarkResetReleaseCooldown() {
        val unavailable = first.copy(availabilityStatus = ApiKeyAvailabilityStatus.UNAVAILABLE)
        val previous = config.copy(apiKeyPool = listOf(unavailable))
        listOf(ApiKeyAvailabilityStatus.AVAILABLE, ApiKeyAvailabilityStatus.UNTESTED).forEach { status ->
            cool(first.id)
            ApiKeyCooldownRecovery.onConfigSaved(
                previous, previous.copy(apiKeyPool = listOf(first.copy(availabilityStatus = status))),
            )
            assertEquals(0L, until(first.id))
        }
    }

    @Test
    fun metadataUpdatesAndFailedTestKeepCooldown() {
        cool(first.id)
        ApiKeyCooldownRecovery.onConfigSaved(config, config.copy(name = "新名称", currentKeyIndex = 1))
        assertTrue(until(first.id) > now)
        ApiKeyCooldownRecovery.onConfigSaved(
            config,
            config.copy(apiKeyPool = listOf(first.copy(availabilityStatus = ApiKeyAvailabilityStatus.UNAVAILABLE), second)),
        )
        assertTrue(until(first.id) > now)
    }

    @Test
    fun successfulRequestPreservesOtherModelCooldowns() {
        cool(first.id, ApiKeyErrorClass.AUTH)
        cool(first.id, ApiKeyErrorClass.MODEL)
        cool(first.id, ApiKeyErrorClass.MODEL, model = "model-b")
        ApiKeyCooldownStore.clearForModel(config.id, first.id, "model-a")
        assertEquals(0L, until(first.id))
        assertTrue(ApiKeyCooldownStore.cooldownUntilMs(config.id, first.id, "model-b") > now)
        val slots = ApiKeyCooldownStore.activeCooldowns(config.id, now)
        assertEquals(listOf("model-b"), slots.map { it.model })
        assertTrue(ApiKeyCooldownStore.activeCooldowns(config.id, now + 180_000L).isEmpty())
    }

    @Test
    fun earliestWaitIgnoresDisabledUnmarkedAndExcludedKeys() {
        val available = ApiKeyAvailabilityStatus.AVAILABLE
        val pool = listOf(
            first.copy(availabilityStatus = available),
            second.copy(availabilityStatus = available),
            ApiKeyInfo(id = "untested", key = "key-3"),
            ApiKeyInfo(id = "disabled", key = "key-4", isEnabled = false),
            ApiKeyInfo(id = "excluded", key = "key-5", availabilityStatus = available),
        )
        val earliest = ApiKeyPoolSelector.earliestCooldownUntilMs(
            pool, excludedIds = setOf("excluded"), nowMs = now, model = "model-a",
            cooldownUntilMs = { id, _ ->
                when (id) {
                    first.id -> now + 60_000L
                    second.id -> now + 30_000L
                    else -> now + 1_000L
                }
            },
        )
        assertEquals(now + 30_000L, requireNotNull(earliest))
    }

    private fun cool(
        keyId: String,
        error: ApiKeyErrorClass = ApiKeyErrorClass.AUTH,
        configId: String = config.id,
        model: String = "model-a",
    ) = ApiKeyCooldownStore.apply(configId, keyId, model, error, null, now)

    private fun until(keyId: String) = ApiKeyCooldownStore.cooldownUntilMs(config.id, keyId, "model-a")
}
