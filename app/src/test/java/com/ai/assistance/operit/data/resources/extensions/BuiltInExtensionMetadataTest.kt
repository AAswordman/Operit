package com.ai.assistance.operit.data.resources.extensions

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuiltInExtensionMetadataTest {
    @Test
    fun localizedJsonManifestUsesTheExistingLocaleResolver() {
        val metadata = JSONObject().put("plugin", """{"description":{"zh":"插件用途","en":"Plugin purpose"}}""").toString()
        val descriptions = BuiltInExtensionMetadata.decodeDescriptions(metadata)
        assertEquals("插件用途", descriptions.getValue("plugin").resolve("zh-CN"))
        assertEquals("Plugin purpose", descriptions.getValue("plugin").resolve("en-US"))
    }

    @Test
    fun hjsonScriptMetadataAndPlainDescriptionsAreSupported() {
        val metadata = JSONObject().put("sandbox", """{name: sample
            description: {zh: "脚本用途", en: "Script purpose"}
        }""").put("plain", """{"description":"Plain description"}""").toString()
        val descriptions = BuiltInExtensionMetadata.decodeDescriptions(metadata)
        assertEquals("脚本用途", descriptions.getValue("sandbox").resolve("zh"))
        assertEquals("Plain description", descriptions.getValue("plain").resolve("en"))
    }

    @Test
    fun missingDescriptionIsReportedInsteadOfShowingAGenericHint() {
        val metadata = JSONObject().put("plugin", """{"display_name":"Missing"}""").toString()
        assertThrows(IllegalArgumentException::class.java) {
            BuiltInExtensionMetadata.decodeDescriptions(metadata)
        }
    }
}
