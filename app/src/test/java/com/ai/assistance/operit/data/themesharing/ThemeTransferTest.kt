package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest
import com.ai.assistance.operit.data.resources.theme.ThemeScriptBundleStore
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeTransferTest {
    @get:Rule val folder = TemporaryFolder()

    private fun document(
        full: Boolean = false,
        values: ThemePreferenceValues = ThemePreferenceValues.defaultVisual(),
    ) =
        ThemeTransferSchema.create(
            "雾青极客版",
            "Panda",
            values,
            ChatLayoutSettings(aiMarkdownLineHeightMultiplier = 1.2f),
            full,
        )

    private fun failure(error: ThemeTransferError, action: () -> Unit) {
        try {
            action()
            fail("应拒绝无效主题")
        } catch (e: ThemeTransferException) {
            assertEquals(error, e.error)
        }
    }

    private fun edit(
        document: ThemeTransferDocument,
        transform: (MutableMap<String, JsonElement>) -> Unit,
    ): ByteArray {
        val root =
            Json.parseToJsonElement(ThemeTransferSchema.encode(document).toString(Charsets.UTF_8))
                .jsonObject
                .toMutableMap()
        transform(root)
        return JsonObject(root).toString().toByteArray()
    }

    private fun editConfig(
        document: ThemeTransferDocument,
        key: String,
        value: JsonElement,
    ): ByteArray =
        edit(document) { root ->
            root["config"] =
                JsonObject(
                    root.getValue("config").jsonObject.toMutableMap().apply { put(key, value) }
                )
        }

    private fun compressed(bytes: ByteArray): String {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }

    @Test
    fun unicodeAndAllOrdinaryParametersRoundTrip() {
        val source = document()
        val restored = ThemeShareCode.decode(ThemeShareCode.encode(source))
        assertEquals("雾青极客版", restored.name)
        assertEquals(source, restored)
        assertEquals(130, restored.fields.size)
        assertEquals(1.2f, restored.layout.aiMarkdownLineHeightMultiplier)
    }

    @Test
    fun acceptsBodyWithoutLink() {
        val source = document()
        val code = ThemeShareCode.encode(source).removePrefix(ThemeShareCode.PREFIX)
        assertEquals(source, ThemeShareCode.decode(code))
    }

    @Test
    fun encodingIsDeterministicAndCompressed() {
        val source = document()
        val first = ThemeShareCode.encode(source)
        assertEquals(first, ThemeShareCode.encode(source))
        assertTrue(first.length < ThemeTransferSchema.encode(source).size)
    }

    @Test
    fun rejectsTruncatedCompressedData() {
        val code = ThemeShareCode.encode(document())
        failure(ThemeTransferError.INVALID_CODE) { ThemeShareCode.decode(code.dropLast(12)) }
    }

    @Test
    fun rejectsTrailingCompressedData() {
        val body = ThemeShareCode.encode(document()).removePrefix(ThemeShareCode.PREFIX)
        val bytes = Base64.getUrlDecoder().decode(body) + byteArrayOf(1, 2, 3)
        failure(ThemeTransferError.INVALID_CODE) {
            ThemeShareCode.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }
    }

    @Test
    fun rejectsNewLinkVersion() {
        failure(ThemeTransferError.UNSUPPORTED_VERSION) {
            ThemeShareCode.decode(ThemeShareCode.encode(document()).replace("schema=1", "schema=2"))
        }
    }

    @Test
    fun rejectsOtherCompressionIdentifier() {
        failure(ThemeTransferError.INVALID_CODE) {
            ThemeShareCode.decode(
                ThemeShareCode.encode(document()).replace("zlib-base64url", "gzip-base64url")
            )
        }
    }

    @Test
    fun rejectsDuplicateQueryParameters() {
        failure(ThemeTransferError.INVALID_CODE) {
            ThemeShareCode.decode(ThemeShareCode.encode(document()) + "&schema=1")
        }
    }

    @Test
    fun rejectsOversizedDecompression() {
        failure(ThemeTransferError.TOO_LARGE) {
            ThemeShareCode.decode(compressed(ByteArray(65537) { 32 }))
        }
    }

    @Test
    fun rejectsMediaInShareCode() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(
                editConfig(document(), "background_image_opacity", JsonPrimitive(0.5f)),
                false,
            )
        }
    }

    @Test
    fun rejectsWrongFieldType() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(
                editConfig(document(), "show_role_name", JsonPrimitive("true")),
                false,
            )
        }
    }

    @Test
    fun rejectsOutOfRangeFontScale() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(
                editConfig(document(), "font_scale", JsonPrimitive(100f)),
                false,
            )
        }
    }

    @Test
    fun rejectsUnknownConfigurationField() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(
                editConfig(document(), "model_api_key", JsonPrimitive("not-a-real-key")),
                false,
            )
        }
    }

    @Test
    fun rejectsMissingOrdinaryParameter() {
        val bytes =
            edit(document()) { root ->
                root["config"] = JsonObject(root.getValue("config").jsonObject - "font_scale")
            }
        failure(ThemeTransferError.INVALID_CONFIG) { ThemeTransferSchema.decode(bytes, false) }
    }

    @Test
    fun rejectsNewManifestVersion() {
        val bytes = edit(document(true)) { it["schema"] = JsonPrimitive(2) }
        failure(ThemeTransferError.UNSUPPORTED_VERSION) { ThemeTransferSchema.decode(bytes, true) }
    }

    @Test
    fun rejectsUnsafeResourcePathsInManifest() {
        listOf(
                "../image.png",
                "assets/../image.png",
                "/sdcard/image.png",
                "https://example.invalid/image.png",
            )
            .forEach { path ->
                failure(ThemeTransferError.INVALID_CONFIG) {
                    ThemeTransferSchema.decode(
                        editConfig(document(true), "background_image_uri", JsonPrimitive(path)),
                        true,
                    )
                }
            }
    }

    @Test
    fun sharePreservesResourcesAndMediaAdjustments() {
        val current =
            ThemePreferenceValues.defaultVisual()
                .withString("background_image_uri", "file:///private/background.png")
                .withString("bubble_user_image_uri", "file:///private/bubble.png")
                .withString("custom_ai_avatar_uri", "file:///private/avatar.png")
                .withString("custom_chat_title", "当前角色标题")
                .withBoolean("use_background_image", true)
                .withBoolean("bubble_user_use_image", true)
                .withFloat("background_image_opacity", 0.76f)
                .withFloat("bubble_user_image_crop_left", 0.2f)
                .withFloat("bubble_user_image_scale", 1.8f)
        val shared =
            document(values = ThemePreferenceValues.defaultVisual().withFloat("font_scale", 1.4f))
        val merged = ThemeTransferSchema.merge(current, shared)
        assertEquals(
            current.strings["background_image_uri"],
            merged.strings["background_image_uri"],
        )
        assertEquals(
            current.strings["bubble_user_image_uri"],
            merged.strings["bubble_user_image_uri"],
        )
        assertEquals(
            current.strings["custom_ai_avatar_uri"],
            merged.strings["custom_ai_avatar_uri"],
        )
        assertEquals("当前角色标题", merged.string("custom_chat_title"))
        assertEquals(true, merged.boolean("use_background_image"))
        assertEquals(true, merged.boolean("bubble_user_use_image"))
        assertEquals(0.76f, merged.requiredFloat("background_image_opacity"))
        assertEquals(0.2f, merged.requiredFloat("bubble_user_image_crop_left"))
        assertEquals(1.8f, merged.requiredFloat("bubble_user_image_scale"))
        assertEquals(1.4f, merged.requiredFloat("font_scale"))
    }

    @Test
    fun shareOmitsSenderFileFontSelection() {
        val source =
            ThemePreferenceValues.defaultVisual()
                .withString("font_type", "file")
                .withString("custom_font_path", "/private/font.ttf")
                .withBoolean("use_custom_font", true)
        val shared = ThemeShareCode.decode(ThemeShareCode.encode(document(values = source)))
        assertFalse("font_type" in shared.fields)
        assertFalse("use_custom_font" in shared.fields)
        assertFalse("custom_font_path" in shared.fields)
    }

    @Test
    fun shareKeepsReceiverFileFontEnabled() {
        val current =
            ThemePreferenceValues.defaultVisual()
                .withString("font_type", "file")
                .withString("custom_font_path", "/private/font.ttf")
                .withBoolean("use_custom_font", true)
        val merged = ThemeTransferSchema.merge(current, document())
        assertEquals("file", merged.string("font_type"))
        assertEquals(true, merged.boolean("use_custom_font"))
        assertEquals("/private/font.ttf", merged.string("custom_font_path"))
    }

    @Test
    fun nullColorsResetReceiverCustomColors() {
        val current = ThemePreferenceValues.defaultVisual().withInt("custom_primary_color", 123)
        assertNull(ThemeTransferSchema.merge(current, document()).int("custom_primary_color"))
    }

    @Test
    fun defaultThemeAndRoleIdentityDoNotRequireOverwrite() {
        val defaults = ThemePreferenceValues.defaultVisual()
        assertTrue(ThemeTransferSchema.isDefaultVisualTheme(defaults))
        assertTrue(
            ThemeTransferSchema.isDefaultVisualTheme(
                defaults
                    .withString("custom_ai_avatar_uri", "file:///private/character-avatar.png")
                    .withString("custom_chat_title", "角色标题")
            )
        )
    }

    @Test
    fun customizedThemeRequiresOverwrite() {
        assertFalse(
            ThemeTransferSchema.isDefaultVisualTheme(
                ThemePreferenceValues.defaultVisual().withFloat("font_scale", 1.1f)
            )
        )
        assertFalse(
            ThemeTransferSchema.isDefaultVisualTheme(
                ThemePreferenceValues.defaultVisual()
                    .withString("custom_user_avatar_uri", "file:///custom/avatar.png")
            )
        )
    }

    @Test
    fun fullThemeReplacesAssetsAndPreservesRoleTitle() {
        val current =
            ThemePreferenceValues.defaultVisual()
                .withString("custom_chat_title", "接收角色")
                .withString("background_image_uri", "file:///old/background.png")
        val full =
            document(
                true,
                ThemePreferenceValues.defaultVisual()
                    .withString("custom_ai_avatar_uri", "assets/1.png"),
            )
        val merged = ThemeTransferSchema.merge(current, full)
        assertEquals("接收角色", merged.string("custom_chat_title"))
        assertEquals("assets/1.png", merged.string("custom_ai_avatar_uri"))
        assertNull(merged.string("background_image_uri"))
    }

    private fun archive(entries: Map<String, ByteArray>): File {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun staging(): File = File(folder.root, "staging-${java.util.UUID.randomUUID()}")

    @Test
    fun packageRoundTripsManifestAndResourceBytes() {
        // 此处验证素材字节的封装和还原，不模拟图片解码。
        val bytes = byteArrayOf(10, 20, 30, 40)
        val asset = folder.newFile().apply { writeBytes(bytes) }
        val full =
            document(
                true,
                ThemePreferenceValues.defaultVisual()
                    .withString("custom_ai_avatar_uri", "assets/1.png"),
            )
        val output = folder.newFile()
        output.outputStream().use { ThemeArchive.write(it, full, mapOf("assets/1.png" to asset)) }
        ThemeArchive.read(output, staging()).use { prepared ->
            assertEquals(full, prepared.document)
            assertArrayEquals(bytes, prepared.assets.getValue("assets/1.png").readBytes())
        }
    }

    @Test
    fun packageWithoutResourcesIsValid() {
        val full = document(true)
        val output = folder.newFile()
        output.outputStream().use { ThemeArchive.write(it, full, emptyMap()) }
        ThemeArchive.read(output, staging()).use { assertEquals(full, it.document) }
    }

    @Test
    fun packageRequiresRootManifest() {
        val file = archive(emptyMap())
        failure(ThemeTransferError.MISSING_MANIFEST) { ThemeArchive.read(file, staging()) }
    }

    @Test
    fun rejectsMissingReferencedAssetAndDeletesStaging() {
        val full =
            document(
                true,
                ThemePreferenceValues.defaultVisual()
                    .withString("custom_ai_avatar_uri", "assets/1.png"),
            )
        val file = archive(mapOf("manifest.json" to ThemeTransferSchema.encode(full)))
        val staging = staging()
        failure(ThemeTransferError.MISSING_ASSET) { ThemeArchive.read(file, staging) }
        assertFalse(staging.exists())
    }

    @Test
    fun rejectsNonZipFile() {
        val file = folder.newFile().apply { writeText("not a theme archive") }
        failure(ThemeTransferError.INVALID_ARCHIVE) { ThemeArchive.read(file, staging()) }
    }

    @Test
    fun rejectsZipTraversalWithoutWritingOutsideStaging() {
        val file =
            archive(
                mapOf(
                    "manifest.json" to ThemeTransferSchema.encode(document(true)),
                    "../escaped.png" to byteArrayOf(1),
                )
            )
        failure(ThemeTransferError.INVALID_ARCHIVE) { ThemeArchive.read(file, staging()) }
        assertFalse(File(folder.root, "escaped.png").exists())
    }

    @Test
    fun rejectsEmptyReferencedAsset() {
        val full =
            document(
                true,
                ThemePreferenceValues.defaultVisual()
                    .withString("custom_ai_avatar_uri", "assets/1.png"),
            )
        val file =
            archive(
                mapOf(
                    "manifest.json" to ThemeTransferSchema.encode(full),
                    "assets/1.png" to byteArrayOf(),
                )
            )
        failure(ThemeTransferError.MISSING_ASSET) { ThemeArchive.read(file, staging()) }
    }

    @Test
    fun rejectsExcessiveJsonNesting() {
        val nested = ("[".repeat(1000) + "0" + "]".repeat(1000)).toByteArray()
        failure(ThemeTransferError.INVALID_CONFIG) { ThemeTransferSchema.decode(nested, false) }
    }

    @Test
    fun rejectsInvalidUtf8() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(byteArrayOf(0xC3.toByte(), 0x28), false)
        }
    }

    @Test
    fun independentMessageStylesSurviveBothShareFormats() {
        val values = ThemePreferenceValues.defaultVisual()
            .withString("user_chat_style", "bubble")
            .withString("ai_chat_style", "cursor")
        for (full in listOf(false, true)) {
            val source = document(full, values)
            val decoded = ThemeTransferSchema.decode(ThemeTransferSchema.encode(source), full)
            val merged = ThemeTransferSchema.merge(ThemePreferenceValues.defaultVisual(), decoded)
            assertEquals("bubble", merged.string("user_chat_style"))
            assertEquals("cursor", merged.string("ai_chat_style"))
        }
    }

    @Test
    fun legacyShareExpandsBothSidesAndReplacesPreviousMixedStyles() {
        val current = ThemePreferenceValues.defaultVisual()
            .withString("user_chat_style", "cursor")
            .withString("ai_chat_style", "bubble")
        for (full in listOf(false, true)) {
            val source = document(full)
            val legacy = edit(source) { root ->
                root["config"] = JsonObject(root.getValue("config").jsonObject.toMutableMap().apply {
                    remove("user_chat_style")
                    remove("ai_chat_style")
                    put("chat_style", JsonPrimitive("bubble"))
                })
            }
            val decoded = ThemeTransferSchema.decode(legacy, full)
            val merged = ThemeTransferSchema.merge(current, decoded)
            assertEquals("bubble", merged.string("user_chat_style"))
            assertEquals("bubble", merged.string("ai_chat_style"))
        }
    }

    @Test
    fun scriptManifestUsesUnbrandedFormatAndNeverSharesPrivateReference() {
        val privateValues = ThemePreferenceValues.defaultVisual()
            .withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///private/theme_script_example.zip")
        val script = ThemeScriptManifest(1, "assets/main.js")
        val source = ThemeTransferSchema.create("脚本主题", "", privateValues, ChatLayoutSettings(), true, script)
        val bytes = ThemeTransferSchema.encode(source)
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals("oktheme", root.getValue("format").jsonPrimitive.content)
        assertEquals(1, root.getValue("schema").jsonPrimitive.int)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("file:///private"))
        assertEquals(script, ThemeTransferSchema.decode(bytes, true).script)
        failure(ThemeTransferError.UNSUPPORTED_VERSION) { ThemeTransferSchema.decode(bytes, false) }
    }

    @Test
    fun rejectsLegacyFilesWithNewScriptFieldsAndNewFilesWithoutSchema() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(edit(document(true)) {
                it["format"] = JsonPrimitive("operit-theme-v1")
                it.remove("schema")
                it["script"] = buildJsonObject { put("apiVersion", 1); put("entry", "assets/main.js") }
            }, true)
        }
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(edit(document(true)) { it.remove("schema") }, true)
        }
    }

    @Test
    fun acceptsPublishedPackagesCodesAndLinksAndOnlyWritesNewNames() {
        for (full in listOf(false, true)) {
            val source = document(full)
            val bytes = edit(source) {
                it["format"] = JsonPrimitive(if (full) "operit-theme-v1" else "operit-theme-params-v1")
                it.remove("schema")
            }
            assertEquals(source, ThemeTransferSchema.decode(bytes, full))
            val exported = ThemeTransferSchema.encode(source).toString(Charsets.UTF_8)
            assertFalse(exported.contains("operit"))
            if (!full) {
                val legacy = "operit://theme/import?v=1&encoding=zlib-base64url&data=" + compressed(bytes)
                assertEquals(source, ThemeShareCode.decode(legacy))
                assertTrue(ThemeShareCode.encode(source).startsWith("oktheme://"))
            }
        }
    }

    @Test
    fun rejectsStringSchemaAndExtraLegacySchema() {
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(edit(document(true)) { it["schema"] = JsonPrimitive("1") }, true)
        }
        failure(ThemeTransferError.INVALID_CONFIG) {
            ThemeTransferSchema.decode(edit(document(true)) { it["format"] = JsonPrimitive("operit-theme-v1") }, true)
        }
    }

    @Test
    fun shareCodePreservesScriptButFullReplacementClearsIt() {
        val current = ThemePreferenceValues.defaultVisual()
            .withString(ThemePreferenceValues.SCRIPT_URI_KEY, "file:///private/theme_script_example.zip")
        val code = ThemeShareCode.decode(ThemeShareCode.encode(document(values = current)))
        assertEquals(current.string(ThemePreferenceValues.SCRIPT_URI_KEY), ThemeTransferSchema.merge(current, code).string(ThemePreferenceValues.SCRIPT_URI_KEY))
        assertNull(ThemeTransferSchema.merge(current, document(true)).string(ThemePreferenceValues.SCRIPT_URI_KEY))
        assertFalse(ThemeTransferSchema.isDefaultVisualTheme(current))
    }

    @Test
    fun scriptPackageRoundTripsSourceAndDeletesPreviewOnClose() {
        val script = ThemeScriptManifest(1, "assets/main.js")
        val full = document(true).copy(script = script)
        val source = folder.newFile().apply { writeText("module.exports = {apiVersion:1,render(){return {apiVersion:1}}}") }
        val output = folder.newFile()
        output.outputStream().use { ThemeArchive.write(it, full, mapOf(script.entry to source)) }
        val prepared = ThemeArchive.read(output, staging())
        val preview = requireNotNull(prepared.scriptPreviewFile)
        assertEquals(source.readText(), ThemeScriptBundleStore.read(preview).source)
        assertEquals(script, prepared.document.script)
        prepared.close()
        assertFalse(preview.exists())
    }

    @Test
    fun rejectsMissingScriptEntryAndUnreferencedFiles() {
        val full = document(true).copy(script = ThemeScriptManifest(1, "assets/main.js"))
        failure(ThemeTransferError.MISSING_ASSET) {
            ThemeArchive.read(archive(mapOf("manifest.json" to ThemeTransferSchema.encode(full))), staging())
        }
        failure(ThemeTransferError.INVALID_ARCHIVE) {
            ThemeArchive.read(archive(mapOf(
                "manifest.json" to ThemeTransferSchema.encode(full),
                "assets/main.js" to "module.exports={}".toByteArray(),
                "assets/unused.js" to "module.exports={}".toByteArray(),
            )), staging())
        }
    }
}
