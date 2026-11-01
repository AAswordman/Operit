package com.ai.assistance.operit.core.tools.packTool.runtime

import com.ai.assistance.operit.core.tools.PackageActivationErrorCode
import com.ai.assistance.operit.core.tools.PackageActivationErrorData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolResultData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PackageActivationResultTest {
    @Test fun missingPackagesFromEverySourceAreFailures() {
        for (name in listOf("github", "custom_import", "org.example.plugin", "skill", "mcp-server")) {
            val message = "Package not found: $name. Please import it first or register it as an MCP server."
            val result = PackageActivationResult.Failed(PackageActivationErrorCode.PACKAGE_NOT_FOUND, name, message)
                .toToolResult("use_package")
            assertFalse(result.success)
            assertEquals("use_package", result.toolName)
            assertEquals(message, result.error)
            val data = result.result as PackageActivationErrorData
            assertEquals(PackageActivationErrorCode.PACKAGE_NOT_FOUND, data.code)
            assertEquals(name, data.packageName)
            assertEquals(message, data.toString())
        }
    }

    @Test fun everyActivationFailureRetainsItsSpecificCode() {
        for (code in PackageActivationErrorCode.values()) {
            val result = PackageActivationResult.Failed(code, "custom", "reason: $code").toToolResult("use_package")
            assertFalse(result.success)
            assertEquals(code, (result.result as PackageActivationErrorData).code)
            assertEquals("reason: $code", result.error)
        }
    }

    @Test fun successfulPromptIsNotClassifiedByItsWords() {
        val prompt = "Using package: custom\nDescription: Explains Package not found errors."
        val result = PackageActivationResult.Activated(prompt).toToolResult("use_package")
        assertTrue(result.success)
        assertNull(result.error)
        assertEquals(prompt, (result.result as StringResultData).value)
    }

    @Test fun structuredErrorSurvivesPolymorphicJsonSerialization() {
        val original: ToolResultData = PackageActivationErrorData(
            PackageActivationErrorCode.PACKAGE_NOT_FOUND, "external-package", "Package not found: external-package"
        )
        val json = Json { classDiscriminator = "__type" }
        val encoded = json.encodeToString(ToolResultData.serializer(), original)
        val fields = Json.parseToJsonElement(encoded).jsonObject
        assertEquals("PACKAGE_NOT_FOUND", fields.getValue("code").jsonPrimitive.content)
        assertEquals("external-package", fields.getValue("packageName").jsonPrimitive.content)
        assertEquals(original, json.decodeFromString(ToolResultData.serializer(), encoded))
    }
}
