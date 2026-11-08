package com.ai.assistance.operit.data.backup

import androidx.datastore.preferences.PreferencesProto.PreferenceMap
import androidx.datastore.preferences.PreferencesProto.StringSet
import androidx.datastore.preferences.PreferencesProto.Value
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RawSnapshotMediaRestoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sourcePackageName = "com.ai.assistance.operit"
    private val sourceFiles = "/data/user/0/$sourcePackageName/files"

    private fun preferences(vararg entries: Pair<String, String>): ByteArray {
        val builder = PreferenceMap.newBuilder()
        entries.forEach { (key, value) -> builder.putPreferences(key, Value.newBuilder().setString(value).build()) }
        return builder.build().toByteArray()
    }

    private fun savePreferences(workDir: File, bytes: ByteArray, directory: String = "files/datastore") {
        File(workDir, "payload/$directory/user_preferences.preferences_pb").apply {
            parentFile!!.mkdirs()
            writeBytes(bytes)
        }
    }

    private fun saveResource(workDir: File, path: String): File {
        return File(workDir, "payload/$path").apply {
            parentFile!!.mkdirs()
            writeText("resource")
        }
    }

    private fun collect(workDir: File, filesDir: File, externalFilesDir: File? = null): RawSnapshotMediaRestore.Result {
        return RawSnapshotMediaRestore.collectPlans(
            workDir, sourcePackageName, filesDir, externalFilesDir, emptyList(), emptySet()
        )
    }

    @Test
    fun releaseSnapshot_restoresDefaultCardGroupAndGlobalMediaWithoutManifestMappings() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("current-install")
        val keys = listOf(
            "global_user_avatar_uri",
            "character_card_theme_default_character_custom_user_avatar_uri",
            "character_card_theme_default_character_custom_ai_avatar_uri",
            "character_card_theme_card_background_image_uri",
            "character_group_theme_group_bubble_user_image_uri",
            "character_group_theme_group_bubble_ai_image_uri",
            "character_card_theme_card_custom_font_path",
            "character_group_theme_group_bubble_user_custom_font_path",
            "character_group_theme_group_bubble_ai_custom_font_path",
        )
        val entries = keys.mapIndexed { index, key ->
            val name = "media_$index.bin"
            saveResource(workDir, "files/$name")
            key to if (key.endsWith("font_path")) "$sourceFiles/$name" else "file://$sourceFiles/$name"
        }
        val original = preferences(*entries.toTypedArray())
        savePreferences(workDir, original)

        val result = collect(workDir, filesDir)
        val rewritten = RawSnapshotMediaRestore.rewritePreferences(
            original, result.plans.associate { it.originalUri to it.destinationUri }
        )
        val restored = PreferenceMap.parseFrom(rewritten).preferencesMap

        assertEquals(9, result.plans.size)
        assertEquals(0, result.skippedCount)
        entries.forEachIndexed { index, (key, _) ->
            val plan = result.plans.single { it.destination.name == "media_$index.bin" }
            assertEquals(plan.destinationUri, restored.getValue(key).string)
            assertEquals(File(filesDir, "media_$index.bin"), plan.destination)
            assertTrue(plan.snapshotFile.isFile)
        }
    }

    @Test
    fun releaseSnapshot_supportsDataDataPathsAndStandaloneDatastoreDirectory() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        saveResource(workDir, "files/fonts/main.ttf")
        val source = "/data/data/$sourcePackageName/files/fonts/main.ttf"
        savePreferences(workDir, preferences("custom_font_path" to source), "datastore")

        val result = collect(workDir, filesDir)

        assertEquals(0, result.skippedCount)
        assertEquals(File(filesDir, "fonts/main.ttf").path, result.plans.single().destinationUri)
    }

    @Test
    fun releaseSnapshot_rebindsExternalResourceWithEncodedFileName() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        val externalFilesDir = temporaryFolder.newFolder("external")
        saveResource(workDir, "external_files/background image.jpg")
        val source = "file:///storage/emulated/10/Android/data/$sourcePackageName/files/background%20image.jpg"
        savePreferences(workDir, preferences("background_image_uri" to source))

        val result = collect(workDir, filesDir, externalFilesDir)

        assertEquals(0, result.skippedCount)
        assertEquals(File(externalFilesDir, "background image.jpg"), result.plans.single().destination)
        assertTrue(result.plans.single().destinationUri.endsWith("/background%20image.jpg"))
    }

    @Test
    fun releaseSnapshot_missingResourceDoesNotProduceReplacement() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        val original = preferences("background_image_uri" to "file://$sourceFiles/missing.jpg")
        savePreferences(workDir, original)

        val result = collect(workDir, filesDir)

        assertEquals(1, result.skippedCount)
        assertTrue(result.plans.isEmpty())
        assertArrayEquals(original, RawSnapshotMediaRestore.rewritePreferences(original, emptyMap()))
    }

    @Test
    fun releaseSnapshot_rejectsTraversalAndDoesNotUseFilesFromAnotherPackage() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        saveResource(workDir, "files/avatar.png")
        savePreferences(workDir, preferences(
            "custom_user_avatar_uri" to "file://$sourceFiles/%2E%2E/avatar.png",
            "custom_ai_avatar_uri" to "file:///data/user/0/com.example.other/files/avatar.png",
        ))

        val result = collect(workDir, filesDir)

        assertTrue(result.plans.isEmpty())
        assertEquals(1, result.skippedCount)
        assertFalse(File(filesDir, "avatar.png").exists())
    }

    @Test
    fun releaseSnapshot_ignoresAssetsContentUrisAndUnrelatedPreferenceKeys() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        saveResource(workDir, "files/unused.jpg")
        savePreferences(workDir, preferences(
            "background_image_uri" to "file:///android_asset/background.jpg",
            "custom_user_avatar_uri" to "content://media/images/1",
            "unrelated_background_image_uri" to "file://$sourceFiles/unused.jpg",
        ))

        val result = collect(workDir, filesDir)

        assertTrue(result.plans.isEmpty())
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun mappedSnapshot_rebindsAliasesOfOnePackagedResource() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        val snapshotFile = saveResource(workDir, "resources/global/font.ttf")
        val original = "$sourceFiles/font.ttf"
        val destination = File(filesDir, "font.ttf")
        val mapped = RawSnapshotResourceRestorePlan(original, snapshotFile, destination, destination.path)
        savePreferences(workDir, preferences("bubble_user_custom_font_path" to "file://$original"))

        val result = RawSnapshotMediaRestore.collectPlans(
            workDir, sourcePackageName, filesDir, null, listOf(mapped), setOf(original)
        )

        assertEquals(0, result.skippedCount)
        assertEquals(snapshotFile, result.plans.single().snapshotFile)
        assertEquals(destination, result.plans.single().destination)
        assertTrue(result.plans.single().destinationUri.startsWith("file:///"))
    }

    @Test
    fun rewritePreferences_preservesStructureTypesKeysAndDisabledThemeFlags() {
        val source = "file://$sourceFiles/旧背景.jpg"
        val destination = "file:///data/user/10/com.ai.assistance.operit.debug/files/" + "背景".repeat(80) + ".jpg"
        val sourceSet = StringSet.newBuilder().addStrings(source).addStrings("unchanged").build()
        val original = PreferenceMap.newBuilder()
            .putPreferences("background_image_uri", Value.newBuilder().setString(source).build())
            .putPreferences("use_background_image", Value.newBuilder().setBoolean(false).build())
            .putPreferences("background_image_opacity", Value.newBuilder().setFloat(0.5f).build())
            .putPreferences("font_scale", Value.newBuilder().setDouble(1.25).build())
            .putPreferences("counter", Value.newBuilder().setLong(123456789L).build())
            .putPreferences("json", Value.newBuilder().setString("{\"avatar\":\"$source\"}").build())
            .putPreferences("set", Value.newBuilder().setStringSet(sourceSet).build())
            .putPreferences(source, Value.newBuilder().setInteger(42).build())
            .build()

        val restored = PreferenceMap.parseFrom(RawSnapshotMediaRestore.rewritePreferences(
            original.toByteArray(), mapOf(source to destination)
        )).preferencesMap

        assertEquals(destination, restored.getValue("background_image_uri").string)
        assertEquals("{\"avatar\":\"$destination\"}", restored.getValue("json").string)
        assertEquals(listOf(destination, "unchanged"), restored.getValue("set").stringSet.stringsList)
        listOf("use_background_image", "background_image_opacity", "font_scale", "counter", source).forEach { key ->
            assertEquals(original.preferencesMap.getValue(key), restored.getValue(key))
        }
    }

    @Test
    fun rewriteStringReferences_doesNotCascadeOrRewriteAnotherFileWithSamePrefix() {
        val source = "file:///old/font.ttf"
        val destination = "file:///new/font.ttf"
        val replacements = mapOf(source to destination, destination to "file:///third/font.ttf")

        assertEquals(destination, RawSnapshotMediaRestore.rewriteStringReferences(source, replacements))
        assertEquals("$source.backup", RawSnapshotMediaRestore.rewriteStringReferences("$source.backup", replacements))
    }

    @Test(expected = IOException::class)
    fun releaseSnapshot_corruptPreferencesFailBeforeReplacingLiveFiles() {
        val workDir = temporaryFolder.newFolder("snapshot")
        val filesDir = temporaryFolder.newFolder("install")
        File(filesDir, "existing.txt").writeText("existing data")
        savePreferences(workDir, byteArrayOf(0x0a, 0x7f))

        try {
            collect(workDir, filesDir)
        } finally {
            assertEquals("existing data", File(filesDir, "existing.txt").readText())
        }
    }
}