package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal data class AvatarConfigDecodeResult(
    val configs: List<AvatarConfig>,
    val invalidEntryIndexes: List<Int>
)

private data class PersistedAvatarConfig(
    val id: String?,
    val name: String?,
    val type: AvatarType?,
    val isBuiltIn: Boolean?,
    val data: Map<String, Any>?
)

internal fun decodePersistedAvatarConfigs(
    json: String,
    gson: Gson
): AvatarConfigDecodeResult {
    // Gson bypasses Kotlin constructors, so persisted fields remain nullable until validated here.
    val root = JsonParser.parseString(json)
    require(root.isJsonArray) {
        "Persisted avatar configs must be a JSON array"
    }
    val persistedConfigs = root.asJsonArray

    val configs = ArrayList<AvatarConfig>(persistedConfigs.size())
    val invalidEntryIndexes = mutableListOf<Int>()

    persistedConfigs.forEachIndexed { index, element ->
        val persisted: PersistedAvatarConfig? =
            try {
                gson.fromJson(element, PersistedAvatarConfig::class.java)
            } catch (_: JsonParseException) {
                invalidEntryIndexes += index
                return@forEachIndexed
            }
        val id = persisted?.id
        val name = persisted?.name
        val type = persisted?.type
        val isBuiltIn = persisted?.isBuiltIn
        val data = persisted?.data

        if (
            id.isNullOrBlank() ||
                name.isNullOrBlank() ||
                type == null ||
                isBuiltIn == null ||
                data == null
        ) {
            invalidEntryIndexes += index
            return@forEachIndexed
        }

        configs +=
            AvatarConfig(
                id = id,
                name = name,
                type = type,
                isBuiltIn = isBuiltIn,
                data = data
            )
    }

    return AvatarConfigDecodeResult(
        configs = configs,
        invalidEntryIndexes = invalidEntryIndexes
    )
}
