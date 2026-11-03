package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterCardWorkspaceBindingTest {

    private val bookmarks = listOf("novel", "source_code", "office_docs")

    @Test fun `resolves an existing bookmark to the repo workspace`() {
        val binding = CharacterCardWorkspaceBinding.resolveRepoBookmark("novel", bookmarks)

        assertEquals("/", binding?.workspacePath)
        assertEquals("repo:novel", binding?.workspaceEnv)
    }

    @Test fun `no default workspace resolves to no binding`() {
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark(null, bookmarks))
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark("", bookmarks))
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark("   ", bookmarks))
    }

    @Test fun `deleted or renamed bookmark resolves to no binding`() {
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark("gone", bookmarks))
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark("novel", emptyList<String>()))
    }

    @Test fun `stored name is trimmed before matching`() {
        val binding = CharacterCardWorkspaceBinding.resolveRepoBookmark("  novel  ", bookmarks)

        assertEquals("repo:novel", binding?.workspaceEnv)
    }

    @Test fun `bookmark names match exactly`() {
        assertNull(CharacterCardWorkspaceBinding.resolveRepoBookmark("Novel", bookmarks))
    }

    @Test fun `character card saved before this feature has no default workspace`() {
        val legacyCard = CharacterCard(id = "legacy", name = "Legacy")

        assertNull(legacyCard.defaultWorkspaceName)
        assertNull(
            CharacterCardWorkspaceBinding.resolveRepoBookmark(legacyCard.defaultWorkspaceName, bookmarks)
        )
    }
}
