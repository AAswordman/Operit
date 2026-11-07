package com.ai.assistance.operit.ui.features.management.resources.hub

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.resources.OfflineResources
import com.ai.assistance.operit.data.resources.models.*
import com.ai.assistance.operit.data.resources.packs.*
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import java.io.File

internal data class HubEntry(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val bytes: Long,
    val ready: Boolean,
    val stored: Boolean,
    val inUse: Boolean = false,
    val bundled: Boolean = false,
    val model: SpeechModelManifest? = null,
    val pack: ResourcePackSpec? = null,
    val legacy: OfflineResources.Group? = null,
    val location: String = "",
    val updateAvailable: Boolean = false,
) {
    val isModel get() = category in setOf("stt", "tts", "ocr")
    val downloadable get() = !bundled && (legacy != null || pack != null || model?.let { it.archive != null || it.hfRepository.isNotBlank() || it.files.all { file -> file.url.isNotBlank() } } == true)
}
internal data class HubSnapshot(val entries: List<HubEntry>, val ubuntuInstalled: Boolean, val ubuntuPending: Boolean, val errors: List<String>)
internal class ResourceHubRepository(private val context: Context) {
    val models = SpeechModels(context)
    suspend fun snapshot(): HubSnapshot {
        PackResources.cleanupObsoleteExtensions(context)
        val engineReady = PackResources.isReady(context, PackResources.SPEECH_ENGINE)
        val installed = models.installed()
        val catalog = models.catalog().associateBy(SpeechModelManifest::key)
        val combined = catalog + installed.models.associateBy(SpeechModelManifest::key)
        val entries = mutableListOf<HubEntry>()
        for (group in OfflineResources.groups(context)) {
            val category = when (group.id) { OfflineResources.SPEECH -> "stt"; OfflineResources.UBUNTU -> "runtime"; else -> "export" }
            val description = when (group.id) {
                OfflineResources.SPEECH -> R.string.resource_speech_description
                OfflineResources.UBUNTU -> R.string.hub_ubuntu_hint
                OfflineResources.ANDROID_EXPORT -> R.string.resource_android_description
                else -> R.string.resource_windows_description
            }
            entries += HubEntry(group.id, context.getString(group.label), category, context.getString(description), group.bytes,
                OfflineResources.isReady(context, group.id), OfflineResources.hasLocalData(context, group.id), legacy = group, location = OfflineResources.directory(context, group.id).absolutePath)
        }
        for (model in combined.values) {
            val complete = models.isInstalled(model)
            entries += HubEntry(model.key, model.name, model.purpose.name.lowercase(), model.description,
                catalog[model.key]?.bytes ?: model.bytes, complete && engineReady, models.hasData(model),
                inUse = LocalModelRuntimeRegistry.isInUse(models.directory(model)), model = model, location = models.directory(model).absolutePath)
        }
        for (pack in PackResources.catalog(context).packs.filter { it.extension == null }) {
            val category = when { pack.id == PackResources.OCR_MODELS -> "ocr"; pack.id.startsWith("template-") -> "template"; else -> "engine" }
            val title = when {
                pack.id == PackResources.OCR_MODELS -> context.getString(R.string.hub_ocr_name)
                pack.id.startsWith("template-") -> context.getString(R.string.hub_template_name, pack.name)
                pack.id == PackResources.APK_REVERSE -> context.getString(R.string.hub_apk_name)
                else -> pack.name
            }
            val bundled = PackResources.isBundled(context, pack.id)
            entries += HubEntry(pack.id, title, category,
                context.getString(when (category) { "ocr" -> R.string.hub_ocr_bundled_hint; "template" -> R.string.hub_template_hint; "plugin", "sandbox" -> R.string.hub_builtin_extension_hint; else -> R.string.hub_engine_hint }),
                pack.archive.bytes, PackResources.isReady(context, pack.id), bundled || PackResources.directory(context, pack.id).exists(),
                PackResources.isInUse(context, pack.id), bundled, pack = pack, location = if (bundled) context.packageResourcePath + "!/assets/" + when {
                    pack.extension != null -> "packages/${File(pack.extension.path).name}"
                    pack.id.startsWith("template-") -> "templates/${pack.id.removePrefix("template-")}"
                    else -> "mlkit-google-ocr-models"
                } else PackResources.directory(context, pack.id).absolutePath,
                updateAvailable = !bundled && PackResources.isReady(context, pack.id) && !PackResources.isCurrentReady(context, pack.id))
        }
        return HubSnapshot(entries, OfflineResources.ubuntuInstalled(context), OfflineResources.ubuntuDeletionPending(context), installed.errors)
    }
}
