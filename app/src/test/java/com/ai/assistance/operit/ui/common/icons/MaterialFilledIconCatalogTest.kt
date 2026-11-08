package com.ai.assistance.operit.ui.common.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.*
import org.junit.Test

class MaterialFilledIconCatalogTest {
    private fun paths(group: VectorGroup): List<VectorPath> = buildList {
        for (index in 0 until group.size) {
            when (val node = group[index]) {
                is VectorPath -> add(node)
                is VectorGroup -> addAll(paths(node))
            }
        }
    }

    @Test fun everyPublishedFilledNameBuildsFromCompactData() {
        assertEquals(2132, MaterialFilledIconData.entries.size)
        MaterialFilledIconData.entries.keys.forEach { name ->
            val icon = requireNotNull(MaterialFilledIconCatalog.resolve(name))
            assertEquals(name, "Filled.$name", icon.name)
            assertTrue(name, paths(icon.root).isNotEmpty())
        }
        assertNull(MaterialFilledIconCatalog.resolve("DoesNotExist"))
    }

    @Test fun compactPathsAndFillRulesMatchOriginalCoreIcons() {
        for (expected in listOf(Icons.Filled.AccountBox, Icons.Filled.Settings)) {
            val actual = requireNotNull(MaterialFilledIconCatalog.resolve(expected.name.substringAfter('.')))
            assertEquals(expected.defaultWidth, actual.defaultWidth)
            assertEquals(expected.defaultHeight, actual.defaultHeight)
            assertEquals(expected.autoMirror, actual.autoMirror)
            assertEquals(paths(expected.root).map { it.pathData }, paths(actual.root).map { it.pathData })
            assertEquals(paths(expected.root).map { it.pathFillType }, paths(actual.root).map { it.pathFillType })
        }
    }

    @Test fun dynamicNamesKeepOriginalNormalizationAndUnknownNameBehavior() {
        assertNotNull(MaterialIconNameResolver.resolveOrNull(" settings "))
        assertNotNull(MaterialIconNameResolver.resolveOrNull("account_box"))
        assertNull(MaterialIconNameResolver.resolveOrNull(""))
        assertNull(MaterialIconNameResolver.resolveOrNull("does_not_exist"))
    }
}