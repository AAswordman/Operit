package com.ai.assistance.operit.data.themesharing

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResourcePathsTest {
    @Test
    fun existingParentFoldersCanContainOrdinaryMedia() {
        assertEquals("assets/role-media/1.png", ThemeResourcePaths.allocate("png", emptySet(), setOf("assets/role-media")))
    }

    @Test
    fun allocationSkipsFilesDirectoriesAndParentsOfProjectResources() {
        val files = setOf("assets/role-media/1.png", "assets/role-media/2.png", "assets/role-media/4.png/body.ttf")
        val folders = setOf("assets/role-media", "assets/role-media/3.png")
        assertEquals("assets/role-media/5.png", ThemeResourcePaths.allocate("png", files, folders))
    }

    @Test
    fun distinctExtensionsAndUnrelatedProjectFoldersDoNotConsumeFileNames() {
        assertEquals("assets/role-media/1.jpg", ThemeResourcePaths.allocate("jpg", setOf("assets/role-media/1.png"), setOf("assets/images")))
    }
}