package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyInfoTest {

    @Test fun `create with required fields`() {
        val info = ApiKeyInfo(id = "key1", key = "sk-abc123")
        assertEquals("key1", info.id)
        assertEquals("sk-abc123", info.key)
    }

    @Test fun `name defaults to empty`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertEquals("", info.name)
    }

    @Test fun `is enabled defaults to true`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertTrue(info.isEnabled)
    }

    @Test fun `availability status defaults to UNTESTED`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertEquals(ApiKeyAvailabilityStatus.UNTESTED, info.availabilityStatus)
    }

    @Test fun `usage count defaults to zero`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertEquals(0L, info.usageCount)
    }

    @Test fun `last used defaults to zero`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertEquals(0L, info.lastUsed)
    }

    @Test fun `error count defaults to zero`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key")
        assertEquals(0L, info.errorCount)
    }

    @Test fun `can set name`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", name = "My Key")
        assertEquals("My Key", info.name)
    }

    @Test fun `can disable key`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", isEnabled = false)
        assertFalse(info.isEnabled)
    }

    @Test fun `can set availability to AVAILABLE`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE)
        assertEquals(ApiKeyAvailabilityStatus.AVAILABLE, info.availabilityStatus)
    }

    @Test fun `can set availability to UNAVAILABLE`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", availabilityStatus = ApiKeyAvailabilityStatus.UNAVAILABLE)
        assertEquals(ApiKeyAvailabilityStatus.UNAVAILABLE, info.availabilityStatus)
    }

    @Test fun `can set usage count`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", usageCount = 42)
        assertEquals(42L, info.usageCount)
    }

    @Test fun `can set last used`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", lastUsed = 123456789L)
        assertEquals(123456789L, info.lastUsed)
    }

    @Test fun `can set error count`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", errorCount = 3)
        assertEquals(3L, info.errorCount)
    }

    @Test fun `copy with different id and key`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key1", name = "Key 1")
        val copy = info.copy(id = "k2", key = "sk-key2")
        assertEquals("k2", copy.id)
        assertEquals("sk-key2", copy.key)
        assertEquals("Key 1", copy.name)
    }

    @Test fun `copy with updated status`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", availabilityStatus = ApiKeyAvailabilityStatus.UNTESTED)
        val copy = info.copy(availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE)
        assertEquals(ApiKeyAvailabilityStatus.AVAILABLE, copy.availabilityStatus)
    }

    @Test fun `copy increments usage count`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", usageCount = 5)
        val copy = info.copy(usageCount = 6)
        assertEquals(6L, copy.usageCount)
    }

    @Test fun `copy with disabled state`() {
        val info = ApiKeyInfo(id = "k1", key = "sk-key", isEnabled = true)
        val copy = info.copy(isEnabled = false)
        assertFalse(copy.isEnabled)
    }

    @Test fun `api key with empty key is valid data class`() {
        val info = ApiKeyInfo(id = "empty", key = "")
        assertEquals("", info.key)
    }

    @Test fun `api key availability status enum values`() {
        assertEquals(3, ApiKeyAvailabilityStatus.values().size)
        assertTrue(ApiKeyAvailabilityStatus.values().contains(ApiKeyAvailabilityStatus.UNTESTED))
        assertTrue(ApiKeyAvailabilityStatus.values().contains(ApiKeyAvailabilityStatus.AVAILABLE))
        assertTrue(ApiKeyAvailabilityStatus.values().contains(ApiKeyAvailabilityStatus.UNAVAILABLE))
    }
}
