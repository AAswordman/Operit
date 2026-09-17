package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.HttpStatusCodeException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyFailureClassifierTest {
    @Test
    fun classifies401AsAuth() {
        assertEquals(
            ApiKeyErrorClass.AUTH,
            ApiKeyFailureClassifier.classify(status(401, "Unauthorized")),
        )
    }

    @Test
    fun classifies402AndQuotaMessageAsQuota() {
        assertEquals(
            ApiKeyErrorClass.QUOTA,
            ApiKeyFailureClassifier.classify(status(402, "Payment required")),
        )
        assertEquals(
            ApiKeyErrorClass.QUOTA,
            ApiKeyFailureClassifier.classify(status(403, "insufficient_quota")),
        )
    }

    @Test
    fun classifies429AsRateLimit() {
        assertEquals(
            ApiKeyErrorClass.RATE_LIMIT,
            ApiKeyFailureClassifier.classify(status(429, "Too Many Requests")),
        )
    }

    @Test
    fun classifies404ModelAsModel() {
        assertEquals(
            ApiKeyErrorClass.MODEL,
            ApiKeyFailureClassifier.classify(status(404, "model_not_found")),
        )
    }

    @Test
    fun classifies5xxAsServer() {
        assertEquals(
            ApiKeyErrorClass.SERVER,
            ApiKeyFailureClassifier.classify(status(503, "unavailable")),
        )
    }

    @Test
    fun classifiesTimeoutAsNetwork() {
        assertEquals(
            ApiKeyErrorClass.NETWORK,
            ApiKeyFailureClassifier.classify(SocketTimeoutException("timeout")),
        )
    }

    @Test
    fun classifiesFirstTokenTimeout() {
        assertEquals(
            ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT,
            ApiKeyFailureClassifier.classify(FirstTokenTimeoutException()),
        )
    }

    @Test
    fun unwrapsCause() {
        val wrapped = IOException("wrapper", status(402, "insufficient_quota"))
        assertEquals(ApiKeyErrorClass.QUOTA, ApiKeyFailureClassifier.classify(wrapped))
        assertEquals(402, ApiKeyFailureClassifier.httpStatus(wrapped))
    }

    @Test
    fun switchingKeysSuppressesIntermediateRetryNotice() {
        val decision =
            ApiKeyRetryDecision(
                exhausted = false,
                delayMs = 0L,
                switchedKey = true,
            )
        assertTrue(
            shouldSuppressRetryNotice(
                status(401, "Invalid token"),
                decision,
                "test",
            )
        )
        assertEquals(
            false,
            shouldSuppressRetryNotice(
                status(401, "Invalid token"),
                decision.copy(switchedKey = false),
                "test",
            )
        )
    }

    private fun status(code: Int, message: String): Exception {
        return object : IOException(message), HttpStatusCodeException {
            override val statusCode: Int = code
        }
    }
}

class ApiKeyRetryPlannerTest {
    @Test
    fun poolAttemptsFollowCandidateCount() {
        assertEquals(3, ApiKeyRetryPlanner.maxHttpAttempts(3, poolMode = true))
        assertEquals(20, ApiKeyRetryPlanner.maxHttpAttempts(40, poolMode = true))
        assertEquals(6, ApiKeyRetryPlanner.maxHttpAttempts(1, poolMode = false))
    }

    @Test
    fun quotaSwitchesImmediatelyWithoutDelay() {
        val decision =
            ApiKeyRetryPlanner.decide(
                errorClass = ApiKeyErrorClass.QUOTA,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 2,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 3,
            )
        assertTrue(decision.switchedKey)
        assertEquals(0L, decision.delayMs)
        assertEquals(false, decision.exhausted)
    }

    @Test
    fun quotaExhaustsWhenNoOtherKeysRemain() {
        val decision =
            ApiKeyRetryPlanner.decide(
                errorClass = ApiKeyErrorClass.QUOTA,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 0,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 3,
            )
        assertTrue(decision.exhausted)
    }

    @Test
    fun networkSwitchesImmediatelyWhenOtherKeysRemain() {
        val decision =
            ApiKeyRetryPlanner.decide(
                errorClass = ApiKeyErrorClass.NETWORK,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 7,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 8,
            )
        assertTrue(decision.switchedKey)
        assertEquals(0L, decision.delayMs)
        assertEquals(false, decision.exhausted)
    }

    @Test
    fun eightKeysGetEightAttemptsThenStop() {
        val last =
            ApiKeyRetryPlanner.decide(
                errorClass = ApiKeyErrorClass.AUTH,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 0,
                httpAttemptsIncludingThis = 8,
                maxHttpAttempts = 8,
            )
        assertTrue(last.exhausted)
        assertEquals(false, last.switchedKey)
    }
}

class ApiKeyPoolSelectorTest {
    @Test
    fun skipsExcludedAndCoolingKeys() {
        val pool =
            listOf(
                com.ai.assistance.operit.data.model.ApiKeyInfo(id = "a", key = "sk-a"),
                com.ai.assistance.operit.data.model.ApiKeyInfo(id = "b", key = "sk-b"),
                com.ai.assistance.operit.data.model.ApiKeyInfo(id = "c", key = "sk-c"),
            )
        val pick =
            ApiKeyPoolSelector.select(
                pool = pool,
                fallbackSingleKey = "sk-fallback",
                currentIndex = 0,
                excludedIds = setOf("a"),
                nowMs = 1_000L,
                model = "grok-4.6",
                cooldownUntilMs = { keyId, _ -> if (keyId == "b") 2_000L else 0L },
            )
        val selected = pick as ApiKeyPick.Selected
        assertEquals("c", selected.id)
        assertEquals(1, selected.candidateCount)
    }

    @Test
    fun emptyPoolFallsBackToSingleKey() {
        val pick =
            ApiKeyPoolSelector.select(
                pool = emptyList(),
                fallbackSingleKey = "sk-fallback",
                currentIndex = 0,
                excludedIds = emptySet(),
                nowMs = 0L,
                model = "model",
                cooldownUntilMs = { _, _ -> 0L },
            )
        assertEquals("sk-fallback", (pick as ApiKeyPick.FallbackSingle).key)
    }
}
class ApiKeyScoreCalculatorTest {
    @Test
    fun stickyKeyWinsWhenHealthy() {
        val scores = mapOf(
            "slow" to ApiKeyScore("slow", 20, 0.9, cold = false),
            "sticky" to ApiKeyScore("sticky", 20, 0.2, cold = false),
        )
        val (chosen, stickyHit) =
            ApiKeyScoreCalculator.pickInitial(
                keyIds = listOf("slow", "sticky"),
                scores = scores,
                stickyKeyId = "sticky",
                roundRobinIndex = 0,
            )
        assertEquals("sticky", chosen)
        assertTrue(stickyHit)
    }

    @Test
    fun failoverDoesNotReselectSticky() {
        val scores = mapOf(
            "a" to ApiKeyScore("a", 20, 0.2, cold = false),
            "b" to ApiKeyScore("b", 20, 0.9, cold = false),
        )
        val chosen =
            ApiKeyScoreCalculator.pickFailover(
                keyIds = listOf("a", "b"),
                scores = scores,
                roundRobinIndex = 0,
            )
        assertEquals("b", chosen)
    }

    @Test
    fun coldKeysStayRoundRobin() {
        val scores = mapOf(
            "a" to ApiKeyScore("a", 1, 0.1, cold = true),
            "b" to ApiKeyScore("b", 2, 0.1, cold = true),
        )
        val (chosen, stickyHit) =
            ApiKeyScoreCalculator.pickInitial(
                keyIds = listOf("a", "b"),
                scores = scores,
                stickyKeyId = null,
                roundRobinIndex = 1,
            )
        assertEquals("b", chosen)
        assertEquals(false, stickyHit)
    }

    @Test
    fun wilsonLowerBoundIsBelowRawRateForSmallSamples() {
        val bound = ApiKeyScoreCalculator.wilsonLowerBound(5, 5)
        assertTrue(bound < 1.0)
        assertTrue(bound > 0.4)
    }
}

class ApiKeyStickyStoreTest {
    @Test
    fun pinSurvivesUntilTtl() {
        ApiKeyStickyStore.resetForTests()
        ApiKeyStickyStore.pin("cfg", "model", "k1", 1_000L)
        assertEquals("k1", ApiKeyStickyStore.get("cfg", "model", 1_000L + 1_000L))
        assertEquals(null, ApiKeyStickyStore.get("cfg", "model", 1_000L + ApiKeyStickyStore.TTL_MS + 1))
    }

    @Test
    fun unpinClearsMatchingKeyOnly() {
        ApiKeyStickyStore.resetForTests()
        ApiKeyStickyStore.pin("cfg", "model", "k1", 1_000L)
        ApiKeyStickyStore.unpin("cfg", "model", "k2")
        assertEquals("k1", ApiKeyStickyStore.get("cfg", "model", 1_000L))
        ApiKeyStickyStore.unpin("cfg", "model", "k1")
        assertEquals(null, ApiKeyStickyStore.get("cfg", "model", 1_000L))
    }
}

class ApiKeyPoolSelectorWeightedTest {
    @Test
    fun failoverSortsByScoreInsteadOfRoundRobin() {
        val pool =
            listOf(
                com.ai.assistance.operit.data.model.ApiKeyInfo(id = "a", key = "sk-a"),
                com.ai.assistance.operit.data.model.ApiKeyInfo(id = "b", key = "sk-b"),
            )
        val pick =
            ApiKeyPoolSelector.select(
                pool = pool,
                fallbackSingleKey = "",
                currentIndex = 0,
                excludedIds = setOf("a"),
                nowMs = 0L,
                model = "model",
                cooldownUntilMs = { _, _ -> 0L },
                scores = mapOf("b" to ApiKeyScore("b", 20, 0.8, cold = false)),
                failover = true,
            )
        assertEquals("b", (pick as ApiKeyPick.Selected).id)
        assertEquals(false, pick.stickyHit)
    }
}
