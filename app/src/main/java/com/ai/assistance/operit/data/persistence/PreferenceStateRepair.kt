package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PreferenceStateRepairResult(
    val preferences: Preferences,
    val issueKeys: Set<String>
)

/**
 * Applies changes to fields known by this build without deleting fields written by a newer build.
 * Arrays are merged by position only when their shape is unchanged; structural array repairs use
 * the normalized array because retaining removed or reordered entries would reintroduce damage.
 */
internal fun mergeNormalizedJsonFields(
    persisted: JsonElement,
    decoded: JsonElement,
    normalized: JsonElement
): JsonElement {
    if (decoded == normalized) return persisted

    return when {
        persisted is JsonObject && decoded is JsonObject && normalized is JsonObject -> {
            val merged = persisted.toMutableMap()
            decoded.keys.filterNot(normalized::containsKey).forEach(merged::remove)
            normalized.forEach { (key, normalizedValue) ->
                val decodedValue = decoded[key]
                merged[key] =
                    if (decodedValue == null) {
                        normalizedValue
                    } else {
                        mergeNormalizedJsonFields(
                            persisted = persisted[key] ?: decodedValue,
                            decoded = decodedValue,
                            normalized = normalizedValue
                        )
                    }
            }
            JsonObject(merged)
        }
        persisted is JsonArray &&
            decoded is JsonArray &&
            normalized is JsonArray &&
            persisted.size == decoded.size &&
            decoded.size == normalized.size -> {
            JsonArray(
                normalized.indices.map { index ->
                    mergeNormalizedJsonFields(
                        persisted = persisted[index],
                        decoded = decoded[index],
                        normalized = normalized[index]
                    )
                }
            )
        }
        else -> normalized
    }
}

/**
 * Commits a logical repair through the DataStore actor. The unreadable logical state is copied to
 * quarantine before the repaired value becomes visible, so repair never destroys the only copy of
 * user data.
 */
suspend fun repairPreferenceState(
    context: Context,
    storeName: String,
    dataStore: DataStore<Preferences>,
    transform: (Preferences) -> PreferenceStateRepairResult
): Boolean {
    var repaired = false
    dataStore.updateData { current ->
        val result = transform(current)
        if (result.issueKeys.isEmpty()) {
            current
        } else {
            val validation = transform(result.preferences)
            val validationChangedState =
                validation.preferences.asMap() != result.preferences.asMap()
            check(validation.issueKeys.isEmpty() && !validationChangedState) {
                "Preference repair for $storeName did not converge; " +
                    "remainingIssueCount=${validation.issueKeys.size}, " +
                    "validationChangedState=$validationChangedState"
            }
            RecoverablePreferenceDataStores.quarantineLogicalState(
                context,
                storeName,
                current,
                result.issueKeys
            )
            repaired = true
            result.preferences
        }
    }
    return repaired
}
