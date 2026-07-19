package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarConfigPersistenceTest {

    private val gson = Gson()

    @Test
    fun decodePersistedAvatarConfigs_rejectsEntriesWithNullOrMissingData() {
        val json =
            """
            [
              {
                "id": "valid",
                "name": "Valid",
                "type": "WEBP",
                "isBuiltIn": false,
                "data": {"folderPath": "/avatars/valid"}
              },
              {
                "id": "null-data",
                "name": "Null data",
                "type": "WEBP",
                "isBuiltIn": false,
                "data": null
              },
              {
                "id": "missing-data",
                "name": "Missing data",
                "type": "WEBP",
                "isBuiltIn": false
              }
            ]
            """.trimIndent()

        val result = decodePersistedAvatarConfigs(json, gson)

        assertEquals(listOf(1, 2), result.invalidEntryIndexes)
        assertEquals(listOf("valid"), result.configs.map { it.id })
        assertEquals("/avatars/valid", result.configs.single().getBasePath())
    }

    @Test
    fun decodePersistedAvatarConfigs_preservesValidSavedConfig() {
        val original =
            AvatarConfig(
                id = "saved",
                name = "Saved avatar",
                type = AvatarType.GLTF,
                isBuiltIn = true,
                data = mapOf("basePath" to "/avatars/saved")
            )

        val result = decodePersistedAvatarConfigs(gson.toJson(listOf(original)), gson)

        assertEquals(emptyList<Int>(), result.invalidEntryIndexes)
        assertEquals(listOf(original), result.configs)
    }

    @Test
    fun decodePersistedAvatarConfigs_rejectsMalformedEntryWithoutDiscardingValidSiblings() {
        val json =
            """
            [
              {
                "id": "first",
                "name": "First",
                "type": "WEBP",
                "isBuiltIn": false,
                "data": {"folderPath": "/avatars/first"}
              },
              {
                "id": "wrong-data-type",
                "name": "Wrong data type",
                "type": "WEBP",
                "isBuiltIn": false,
                "data": 42
              },
              42,
              {
                "id": "last",
                "name": "Last",
                "type": "GLTF",
                "isBuiltIn": false,
                "data": {"basePath": "/avatars/last"}
              }
            ]
            """.trimIndent()

        val result = decodePersistedAvatarConfigs(json, gson)

        assertEquals(listOf(1, 2), result.invalidEntryIndexes)
        assertEquals(listOf("first", "last"), result.configs.map { it.id })
    }
}
