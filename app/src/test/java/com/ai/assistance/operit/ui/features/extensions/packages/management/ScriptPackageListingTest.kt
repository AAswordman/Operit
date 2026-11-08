package com.ai.assistance.operit.ui.features.extensions.packages.management

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.ToolPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScriptPackageListingTest {
    @Test
    fun scriptsExcludeContainersAndChildrenRegardlessOfCategoryOrEnableDefaults() {
        val script = entry("standalone", "Automatic")
        val packages = linkedMapOf(
            "bundle" to entry("bundle", "Automatic"),
            "standalone" to script,
            "bundle.child" to entry("bundle.child", "Other"),
            "bundle.disabled" to entry("bundle.disabled", "Draw"),
        )
        val members = setOf("bundle", "bundle.child", "bundle.disabled")
        val result = selectStandaloneScripts(packages, members::contains)

        assertEquals(listOf("standalone"), result.keys.toList())
        assertSame(script, result.getValue("standalone"))
        assertEquals(4, packages.size)
    }

    @Test
    fun standaloneCategoriesAndMetadataRemainIntactAndAnAllPluginListIsEmpty() {
        val scripts = linkedMapOf("draw" to entry("draw", "Draw"), "custom" to entry("custom", "Personal"))
        val result = selectStandaloneScripts(scripts) { false }

        assertEquals(listOf("Draw", "Personal"), result.values.map { it.category })
        assertEquals(scripts, result)
        assertEquals(emptyMap<String, ToolPackage>(), selectStandaloneScripts(scripts) { true })
        assertEquals(emptyMap<String, ToolPackage>(), selectStandaloneScripts(emptyMap()) { false })
    }

    private fun entry(name: String, category: String) = ToolPackage(
        name = name,
        description = LocalizedText.of("Script $name"),
        tools = emptyList(),
        category = category,
        enabledByDefault = false,
    )
}