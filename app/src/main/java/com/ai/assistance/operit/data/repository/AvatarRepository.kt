package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.fbx.FbxInspector
import com.ai.assistance.fbx.FbxModelInfo
import com.ai.assistance.operit.util.AppLogger
import androidx.core.content.edit
import com.ai.assistance.operit.core.avatar.common.factory.AvatarModelFactory
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import com.ai.assistance.operit.core.avatar.common.state.AvatarCustomMoodDefinition
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarMoodTypes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.Charset
import com.ai.assistance.operit.util.AssetCopyUtils
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Data class representing the persisted configuration of a single avatar.
 * This is what gets stored in SharedPreferences.
 */
data class AvatarConfig(
    val id: String,
    val name: String,
    val type: AvatarType,
    val isBuiltIn: Boolean,
    /** Type-specific data, e.g., file paths, settings. */
    val data: Map<String, Any>
) {
    fun getBasePath(): String? {
        return (data["folderPath"] as? String) ?: (data["basePath"] as? String)
    }
}

private const val DATA_KEY_EMOTION_ANIMATION_MAPPING = "emotionAnimationMapping"
private const val DATA_KEY_MOOD_ANIMATION_MAPPING = "moodAnimationMapping"
private const val DATA_KEY_CUSTOM_MOOD_DEFINITIONS = "customMoodDefinitions"

fun AvatarConfig.getEmotionAnimationMapping(): Map<AvatarEmotion, String> {
    val rawMapping = data[DATA_KEY_EMOTION_ANIMATION_MAPPING] as? Map<*, *> ?: return emptyMap()

    return rawMapping.entries.mapNotNull { (rawEmotion, rawAnimationName) ->
        val emotionName = rawEmotion as? String ?: return@mapNotNull null
        val emotion = try {
            AvatarEmotion.valueOf(emotionName)
        } catch (_: IllegalArgumentException) {
            return@mapNotNull null
        }

        val animationName = rawAnimationName?.toString()?.trim().orEmpty()
        if (animationName.isBlank()) {
            return@mapNotNull null
        }

        emotion to animationName
    }.toMap()
}

fun AvatarConfig.getMoodAnimationMapping(): Map<String, String> {
    val rawMapping = data[DATA_KEY_MOOD_ANIMATION_MAPPING] as? Map<*, *> ?: return emptyMap()

    return rawMapping.entries.mapNotNull { (rawKey, rawAnimationName) ->
        val key = AvatarMoodTypes.normalizeKey(rawKey?.toString().orEmpty())
        val animationName = rawAnimationName?.toString()?.trim().orEmpty()
        if (key.isBlank() || animationName.isBlank()) {
            return@mapNotNull null
        }
        key to animationName
    }.toMap()
}

fun AvatarConfig.getCustomMoodDefinitions(): List<AvatarCustomMoodDefinition> {
    val rawDefinitions = data[DATA_KEY_CUSTOM_MOOD_DEFINITIONS] as? List<*> ?: return emptyList()

    val parsed = rawDefinitions.mapNotNull { entry ->
        val rawMap = entry as? Map<*, *> ?: return@mapNotNull null
        val key = rawMap["key"]?.toString().orEmpty()
        val promptHint = rawMap["promptHint"]?.toString().orEmpty()
        AvatarCustomMoodDefinition(
            key = key,
            promptHint = promptHint
        )
    }
    return AvatarMoodTypes.sanitizeCustomDefinitions(parsed)
}

fun AvatarConfig.withEmotionAnimationMapping(mapping: Map<AvatarEmotion, String>): AvatarConfig {
    val normalized = mapping
        .mapValues { (_, animationName) -> animationName.trim() }
        .filterValues { animationName -> animationName.isNotBlank() }
        .mapKeys { (emotion, _) -> emotion.name }

    val updatedData = data.toMutableMap()
    if (normalized.isEmpty()) {
        updatedData.remove(DATA_KEY_EMOTION_ANIMATION_MAPPING)
    } else {
        updatedData[DATA_KEY_EMOTION_ANIMATION_MAPPING] = normalized
    }

    return copy(data = updatedData)
}

fun AvatarConfig.withMoodAnimationMapping(mapping: Map<String, String>): AvatarConfig {
    val normalized = mapping.entries.mapNotNull { (rawKey, rawAnimationName) ->
        val key = AvatarMoodTypes.normalizeKey(rawKey)
        val animationName = rawAnimationName.trim()
        if (key.isBlank() || animationName.isBlank()) {
            return@mapNotNull null
        }
        key to animationName
    }.toMap()

    val updatedData = data.toMutableMap()
    if (normalized.isEmpty()) {
        updatedData.remove(DATA_KEY_MOOD_ANIMATION_MAPPING)
    } else {
        updatedData[DATA_KEY_MOOD_ANIMATION_MAPPING] = normalized
    }

    return copy(data = updatedData)
}

fun AvatarConfig.withCustomMoodDefinitions(
    definitions: List<AvatarCustomMoodDefinition>
): AvatarConfig {
    val sanitized = AvatarMoodTypes.sanitizeCustomDefinitions(definitions)
    val updatedData = data.toMutableMap()
    if (sanitized.isEmpty()) {
        updatedData.remove(DATA_KEY_CUSTOM_MOOD_DEFINITIONS)
    } else {
        updatedData[DATA_KEY_CUSTOM_MOOD_DEFINITIONS] =
            sanitized.map { definition ->
                mapOf(
                    "key" to definition.key,
                    "promptHint" to definition.promptHint
                )
            }
    }

    return copy(data = updatedData)
}

private fun normalizeAvatarPath(path: String): String {
    return runCatching { File(path).canonicalPath }
        .getOrElse { path }
        .replace('\\', '/')
        .lowercase()
}

private fun buildAvatarConfigId(type: AvatarType, directory: File, isBuiltIn: Boolean): String {
    if (isBuiltIn) {
        return "built_in_${type.name.lowercase()}_${directory.name.lowercase()}"
    }

    val normalizedPath = normalizeAvatarPath(directory.absolutePath)
    val pathHash = normalizedPath.hashCode().toUInt().toString(16)
    return "user_${type.name.lowercase()}_$pathHash"
}

private fun avatarSourceKey(config: AvatarConfig): String? {
    val basePath = config.getBasePath() ?: return null
    return "${config.type.name}|${normalizeAvatarPath(basePath)}"
}

/**
 * Data class for avatar instance settings like scale and position.
 */
data class AvatarInstanceSettings(
    val scale: Float = 1.0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val customSettings: Map<String, Float> = emptyMap()
)

/**
 * Data class for global avatar settings.
 */
data class AvatarSettings(
    val currentAvatarId: String? = null,
    val isVoiceCallAvatarEnabled: Boolean = false
)

/**
 * Defines a contract for type-specific avatar persistence logic.
 * Each supported AvatarType should have an implementation of this delegate.
 */
interface AvatarPersistenceDelegate {
    val type: AvatarType

    /**
     * Scans a directory to find and parse model configurations for this delegate's avatar type.
     * @param directory The directory to scan. It could be a user directory or a temporary
     *                  directory from a ZIP import.
     * @param isBuiltIn Flag to mark if the scanned models are built-in assets.
     * @return A list of [AvatarConfig] found in the directory.
     */
    fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig>
}

/**
 * Persistence delegate for DragonBones models.
 */
class DragonBonesPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.DRAGONBONES

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "DragonBones scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val jsonFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()

        val skeletonFile = jsonFiles.firstOrNull { !it.name.endsWith("_tex.json", ignoreCase = true) }
        if (skeletonFile == null) {
            if (jsonFiles.isNotEmpty()) {
                AppLogger.d(
                    "AvatarRepository",
                    "DragonBones scan no skeleton json in ${directory.absolutePath}, jsons=${jsonFiles.joinToString { it.name }}"
                )
            }
            return allConfigs
        }

        val modelName = skeletonFile.nameWithoutExtension.removeSuffix("_ske")
        val textureJsonFile = File(directory, "${modelName}_tex.json")
        val textureImageFile = File(directory, "${modelName}_tex.png")

        if (!textureJsonFile.exists() || !textureImageFile.exists()) {
            AppLogger.d(
                "AvatarRepository",
                "DragonBones scan incomplete in ${directory.absolutePath}, expected=${textureJsonFile.name}, ${textureImageFile.name}"
            )
            return allConfigs
        }

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.DRAGONBONES, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.DRAGONBONES,
            isBuiltIn = isBuiltIn,
            data = mapOf(
                "folderPath" to directory.absolutePath,
                "skeletonFile" to skeletonFile.name,
                "textureJsonFile" to textureJsonFile.name,
                "textureImageFile" to textureImageFile.name,
                "isBuiltIn" to isBuiltIn
            )
        )
        AppLogger.i("AvatarRepository", "DragonBones config recognized: ${directory.absolutePath}")
        allConfigs.add(config)
        return allConfigs
    }
}

/**
 * Persistence delegate for WebP models.
 */
class WebPPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.WEBP

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "WebP scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val webpFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("webp", ignoreCase = true)
        } ?: emptyArray()

        if (webpFiles.isEmpty()) {
            return allConfigs
        }

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.WEBP, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.WEBP,
            isBuiltIn = isBuiltIn,
            data = mapOf(
                "basePath" to directory.absolutePath,
                "webpFiles" to webpFiles.map { it.name }
            )
        )
        AppLogger.i(
            "AvatarRepository",
            "WebP config recognized: ${directory.absolutePath}, files=${webpFiles.joinToString { it.name }}"
        )
        allConfigs.add(config)
        return allConfigs
    }
}

class Mp4PersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.MP4

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "MP4 scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val mp4Files = directory.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        } ?: emptyArray()

        if (mp4Files.isEmpty()) {
            return allConfigs
        }

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.MP4, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.MP4,
            isBuiltIn = isBuiltIn,
            data = mapOf(
                "basePath" to directory.absolutePath,
                "mp4Files" to mp4Files.map { it.name }
            )
        )
        AppLogger.i(
            "AvatarRepository",
            "MP4 config recognized: ${directory.absolutePath}, files=${mp4Files.joinToString { it.name }}"
        )
        allConfigs.add(config)
        return allConfigs
    }
}

class MmdPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.MMD

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "MMD scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val modelCandidates = directory.listFiles { file ->
            file.isFile && (file.extension.equals("pmx", ignoreCase = true) || file.extension.equals("pmd", ignoreCase = true))
        } ?: emptyArray()
        val modelFile = modelCandidates.firstOrNull() ?: return allConfigs

        val motionFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("vmd", ignoreCase = true)
        }?.sortedBy { it.name.lowercase() }.orEmpty()

        val data = mutableMapOf<String, Any>(
            "basePath" to directory.absolutePath,
            "modelFile" to modelFile.name
        )
        if (motionFiles.isNotEmpty()) {
            data["motionFile"] = motionFiles.first().name
            data["motionFiles"] = motionFiles.map { it.name }
        }

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.MMD, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.MMD,
            isBuiltIn = isBuiltIn,
            data = data
        )
        AppLogger.i(
            "AvatarRepository",
            "MMD config recognized: ${directory.absolutePath}, model=${modelFile.name}, motions=${if (motionFiles.isEmpty()) "<none>" else motionFiles.joinToString { it.name }}"
        )
        allConfigs.add(config)
        return allConfigs
    }
}

class GltfPersistenceDelegate : AvatarPersistenceDelegate {
    override val type = AvatarType.GLTF

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "glTF scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val modelCandidates = directory.listFiles { file ->
            file.isFile &&
                !file.name.startsWith(".operit_", ignoreCase = true) &&
                (file.extension.equals("glb", ignoreCase = true) || file.extension.equals("gltf", ignoreCase = true))
        }?.sortedWith(
            compareBy<File> { !it.extension.equals("glb", ignoreCase = true) }
                .thenBy { it.name.lowercase() }
        ).orEmpty()

        val modelFile = modelCandidates.firstOrNull() ?: return allConfigs

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.GLTF, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.GLTF,
            isBuiltIn = isBuiltIn,
            data = mapOf(
                "basePath" to directory.absolutePath,
                "modelFile" to modelFile.name
            )
        )
        AppLogger.i(
            "AvatarRepository",
            "glTF config recognized: ${directory.absolutePath}, model=${modelFile.name}"
        )
        allConfigs.add(config)
        return allConfigs
    }
}

class FbxPersistenceDelegate(
    private val inspectModel: (String) -> FbxModelInfo? = FbxInspector::inspectModel
) : AvatarPersistenceDelegate {
    override val type = AvatarType.FBX

    override fun scanDirectory(directory: File, isBuiltIn: Boolean): List<AvatarConfig> {
        val allConfigs = mutableListOf<AvatarConfig>()
        if (!directory.exists() || !directory.isDirectory) {
            AppLogger.d("AvatarRepository", "FBX scan skipped (not dir): ${directory.absolutePath}")
            return allConfigs
        }

        val modelFile =
            directory.listFiles { file ->
                file.isFile &&
                    !file.name.startsWith(".operit_", ignoreCase = true) &&
                    file.extension.equals("fbx", ignoreCase = true)
            }?.sortedBy { it.name.lowercase() }
                ?.firstOrNull()
                ?: return allConfigs

        val modelInfo = inspectModel(modelFile.absolutePath)
        if (modelInfo == null) {
            AppLogger.w(
                "AvatarRepository",
                "FBX scan failed to inspect ${modelFile.absolutePath}: ${FbxInspector.getLastError()}"
            )
            return allConfigs
        }

        if (modelInfo.missingExternalFiles.isNotEmpty()) {
            AppLogger.w(
                "AvatarRepository",
                "FBX scan skipped ${modelFile.absolutePath} due to missing resources: ${modelInfo.missingExternalFiles.joinToString()}"
            )
            return allConfigs
        }

        val data = buildMap<String, Any> {
            put("basePath", directory.absolutePath)
            put("modelFile", modelFile.name)
            put("animationNames", modelInfo.animationNames)
            modelInfo.defaultAnimation?.let { put("defaultAnimation", it) }
        }

        val config = AvatarConfig(
            id = buildAvatarConfigId(AvatarType.FBX, directory, isBuiltIn),
            name = directory.name,
            type = AvatarType.FBX,
            isBuiltIn = isBuiltIn,
            data = data
        )
        AppLogger.i(
            "AvatarRepository",
            "FBX config recognized: ${directory.absolutePath}, model=${modelFile.name}, animations=${if (modelInfo.animationNames.isEmpty()) "<none>" else modelInfo.animationNames.joinToString()}"
        )
        allConfigs.add(config)
        return allConfigs
    }
}

/**
 * A generic repository for managing all types of virtual avatars.
 * It handles loading, saving, and managing avatar configurations from both
 * built-in assets and user-provided files.
 */
class AvatarRepository(
    private val context: Context,
    private val modelFactory: AvatarModelFactory
) {

    companion object {
        private const val TAG = "AvatarRepository"
        private const val PREFS_NAME = "avatar_preferences"
        private const val KEY_CONFIGS = "avatar_configs"
        private const val KEY_SETTINGS = "avatar_settings"
        private const val KEY_INSTANCE_SETTINGS = "avatar_instance_settings"

        private const val ASSETS_AVATAR_DIR = "pets"
        private const val USER_AVATAR_DIR = "avatars"
        private val ZIP_IMPORT_CHARSETS: List<Charset> =
            listOf("UTF-8", "GBK", "GB18030", "CP437").mapNotNull { name ->
                runCatching { Charset.forName(name) }.getOrNull()
            }

        @Volatile private var INSTANCE: AvatarRepository? = null

        fun getInstance(context: Context, modelFactory: AvatarModelFactory): AvatarRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AvatarRepository(context.applicationContext, modelFactory).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val delegates: Map<AvatarType, AvatarPersistenceDelegate> = listOf(
        DragonBonesPersistenceDelegate(),
        WebPPersistenceDelegate(),
        Mp4PersistenceDelegate(),
        MmdPersistenceDelegate(),
        GltfPersistenceDelegate(),
        FbxPersistenceDelegate()
    ).associateBy { it.type }

    private val _configs = MutableStateFlow<List<AvatarConfig>>(emptyList())
    val configs: StateFlow<List<AvatarConfig>> = _configs.asStateFlow()

    private val _currentAvatar = MutableStateFlow<AvatarModel?>(null)
    val currentAvatar: StateFlow<AvatarModel?> = _currentAvatar.asStateFlow()

    private val _settings = MutableStateFlow(AvatarSettings())
    val settings: StateFlow<AvatarSettings> = _settings.asStateFlow()
    
    private val _instanceSettings = MutableStateFlow<Map<String, AvatarInstanceSettings>>(emptyMap())
    val instanceSettings: StateFlow<Map<String, AvatarInstanceSettings>> = _instanceSettings.asStateFlow()
    
    private val userAvatarDir: File by lazy {
        File(context.getExternalFilesDir(null), USER_AVATAR_DIR)
    }

    init {
        userAvatarDir.mkdirs()
        synchronizeAssets()
        loadAvatars()
    }

    private fun synchronizeAssets() {
        try {
            val assetManager = context.assets
            val avatarTypeDirs = assetManager.list(ASSETS_AVATAR_DIR)?.filter {
                try { assetManager.list("$ASSETS_AVATAR_DIR/$it")?.isNotEmpty() == true } catch (e: Exception) { false }
            } ?: return

            for (typeDir in avatarTypeDirs) {
                val modelFolders = assetManager.list("$ASSETS_AVATAR_DIR/$typeDir") ?: continue
                for (modelFolder in modelFolders) {
                    val destDir = File(userAvatarDir, modelFolder)
                    if (!destDir.exists()) {
                        AppLogger.d(TAG, "Populating asset model '$modelFolder' of type '$typeDir'")
                        AssetCopyUtils.copyAssetDirRecursive(
                            context,
                            "$ASSETS_AVATAR_DIR/$typeDir/$modelFolder",
                            destDir,
                            overwrite = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error synchronizing assets: ${e.message}", e)
        }
    }

    private fun loadAvatars() {
        val configsFromPrefs = loadConfigsFromPrefs()
        val configsFromDisk = scanUserAvatarDirectory()

        val mergedFromDisk = configsFromDisk.map { disk ->
            val diskSourceKey = avatarSourceKey(disk)
            val matchedPref =
                configsFromPrefs.firstOrNull { pref ->
                    diskSourceKey != null && avatarSourceKey(pref) == diskSourceKey
                }

            if (matchedPref != null) {
                disk.copy(
                    name = matchedPref.name,
                    data = matchedPref.data + disk.data
                )
            } else {
                disk
            }
        }

        val preservedPrefs =
            configsFromPrefs.filter { pref ->
                val prefSourceKey = avatarSourceKey(pref)
                val existsOnDisk =
                    configsFromDisk.any { disk ->
                        prefSourceKey != null && avatarSourceKey(disk) == prefSourceKey
                    }

                !existsOnDisk && pref.getBasePath()?.let { File(it).exists() } == true
            }

        val finalConfigs =
            (mergedFromDisk + preservedPrefs)
                .distinctBy { config -> avatarSourceKey(config) ?: "id:${config.id}" }

        _configs.value = finalConfigs
        saveConfigsToPrefs(finalConfigs)

        _instanceSettings.value = loadInstanceSettingsFromPrefs()

        val settings = loadSettingsFromPrefs()
        _settings.value = settings
        updateCurrentAvatar(settings.currentAvatarId)
    }

    private fun scanUserAvatarDirectory(): List<AvatarConfig> {
        val modelFolders = userAvatarDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return modelFolders.flatMap { folder ->
            delegates.values.flatMap { delegate ->
                val isBuiltIn = isPathFromAssets(folder.path)
                delegate.scanDirectory(folder, isBuiltIn)
            }
        }
    }
    
    private fun isPathFromAssets(path: String): Boolean {
        // A simple heuristic to determine if a model was copied from assets.
        // This could be improved by storing metadata.
        return _configs.value.any { it.isBuiltIn && it.getBasePath() == path }
    }

    suspend fun refreshAvatars() = withContext(Dispatchers.IO) {
        loadAvatars()
    }
    
    fun switchAvatar(avatarId: String) {
        val currentSettings = loadSettingsFromPrefs()
        if (currentSettings.currentAvatarId != avatarId) {
            saveSettingsToPrefs(currentSettings.copy(currentAvatarId = avatarId))
            updateCurrentAvatar(avatarId)
        }
    }

    fun updateVoiceCallAvatarEnabled(enabled: Boolean) {
        val currentSettings = loadSettingsFromPrefs()
        if (currentSettings.isVoiceCallAvatarEnabled == enabled) {
            return
        }

        saveSettingsToPrefs(currentSettings.copy(isVoiceCallAvatarEnabled = enabled))
    }

    private fun updateCurrentAvatar(targetId: String?) {
        val config = _configs.value.find { it.id == targetId }
            ?: _configs.value.firstOrNull()

        if (config == null) {
            _currentAvatar.value = null
            return
        }
        
        _currentAvatar.value = modelFactory.createModel(
            id = config.id,
            name = config.name,
            type = config.type,
            data = config.data
        )

        if (config.id != _settings.value.currentAvatarId) {
            saveSettingsToPrefs(_settings.value.copy(currentAvatarId = config.id))
        }
    }

    suspend fun deleteAvatar(avatarId: String): Boolean = withContext(Dispatchers.IO) {
        val config = _configs.value.find { it.id == avatarId } ?: return@withContext false
        if (config.isBuiltIn) {
            AppLogger.w(TAG, "Cannot delete a built-in avatar.")
            return@withContext false
        }

        val folderPath = config.getBasePath()
        if (folderPath == null) {
            AppLogger.e(TAG, "Avatar config for ${config.id} is missing folderPath or basePath.")
            return@withContext false
        }
        val modelDir = File(folderPath)

        val deletionSucceeded = if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            AppLogger.w(TAG, "Avatar directory to delete did not exist, proceeding to remove config entry: $folderPath")
            true // If directory doesn't exist, we can still proceed to remove it from config
        }

        if (deletionSucceeded) {
            val updatedConfigs = _configs.value.filter { it.id != avatarId }
            _configs.value = updatedConfigs
            saveConfigsToPrefs(updatedConfigs)

            if (loadSettingsFromPrefs().currentAvatarId == avatarId) {
                updateCurrentAvatar(updatedConfigs.firstOrNull()?.id)
            }
            AppLogger.i(TAG, "Successfully removed avatar config: ${config.name}")
            true
        } else {
            AppLogger.e(TAG, "Failed to delete avatar directory: $folderPath")
            false
        }
    }

    suspend fun renameAvatar(avatarId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedName = newName.trim()
        if (normalizedName.isBlank()) {
            return@withContext false
        }

        val targetConfig = _configs.value.find { it.id == avatarId } ?: return@withContext false
        if (targetConfig.name == normalizedName) {
            return@withContext true
        }

        val updatedConfigs = _configs.value.map { config ->
            if (config.id == avatarId) {
                config.copy(name = normalizedName)
            } else {
                config
            }
        }

        _configs.value = updatedConfigs
        saveConfigsToPrefs(updatedConfigs)

        if (_currentAvatar.value?.id == avatarId) {
            updateCurrentAvatar(avatarId)
        }

        true
    }
    
    fun updateAvatarSettings(avatarId: String, newSettings: AvatarInstanceSettings) {
        val updatedSettings = _instanceSettings.value.toMutableMap()
        updatedSettings[avatarId] = newSettings
        _instanceSettings.value = updatedSettings
        saveInstanceSettingsToPrefs(updatedSettings)
    }

    fun updateAvatarEmotionAnimationMapping(
        avatarId: String,
        mapping: Map<AvatarEmotion, String>
    ) {
        updateAvatarConfig(avatarId) { config ->
            config.withEmotionAnimationMapping(mapping)
        }
    }

    fun updateAvatarMoodAnimationMapping(
        avatarId: String,
        mapping: Map<String, String>
    ) {
        updateAvatarConfig(avatarId) { config ->
            config.withMoodAnimationMapping(mapping)
        }
    }

    fun updateAvatarCustomMoodDefinitions(
        avatarId: String,
        definitions: List<AvatarCustomMoodDefinition>
    ) {
        updateAvatarConfig(avatarId) { config ->
            config.withCustomMoodDefinitions(definitions)
        }
    }

    fun updateAvatarMoodConfig(
        avatarId: String,
        definitions: List<AvatarCustomMoodDefinition>,
        moodAnimationMapping: Map<String, String>
    ) {
        updateAvatarConfig(avatarId) { config ->
            config.withCustomMoodDefinitions(definitions)
                .withMoodAnimationMapping(moodAnimationMapping)
        }
    }

    fun getAvatarSettings(avatarId: String): AvatarInstanceSettings {
        return _instanceSettings.value[avatarId] ?: AvatarInstanceSettings()
    }

    private fun updateAvatarConfig(
        avatarId: String,
        transform: (AvatarConfig) -> AvatarConfig
    ) {
        val updatedConfigs = _configs.value.map { config ->
            if (config.id == avatarId) {
                transform(config)
            } else {
                config
            }
        }

        _configs.value = updatedConfigs
        saveConfigsToPrefs(updatedConfigs)
    }

    private enum class AvatarImportKind {
        ZIP,
        MODEL_FILE,
        UNSUPPORTED
    }

    suspend fun importAvatarFromUri(uri: Uri): Boolean {
        return when (detectImportKind(uri)) {
            AvatarImportKind.ZIP -> importAvatarFromZip(uri)
            AvatarImportKind.MODEL_FILE -> importAvatarFromModelFile(uri)
            AvatarImportKind.UNSUPPORTED -> {
                AppLogger.w(TAG, "Unsupported avatar import file type: uri=$uri")
                false
            }
        }
    }

    private fun detectImportKind(uri: Uri): AvatarImportKind {
        val fileName = (resolveImportDisplayName(uri) ?: uri.lastPathSegment)
            .orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .lowercase()
        if (fileName.endsWith(".zip")) {
            return AvatarImportKind.ZIP
        }
        if (
            fileName.endsWith(".glb") ||
            fileName.endsWith(".gltf") ||
            fileName.endsWith(".mp4") ||
            fileName.endsWith(".fbx")
        ) {
            return AvatarImportKind.MODEL_FILE
        }

        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (mimeType.contains("zip")) {
            return AvatarImportKind.ZIP
        }
        if (mimeType.contains("gltf") || mimeType.contains("mp4") || mimeType.contains("fbx")) {
            return AvatarImportKind.MODEL_FILE
        }

        return AvatarImportKind.UNSUPPORTED
    }

    private fun resolveImportDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) {
                    return@use null
                }

                cursor.getString(index)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeImportFileName(rawName: String): String {
        val normalized = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .ifBlank { "imported_avatar" }

        val sanitized = normalized.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return sanitized.ifBlank { "imported_avatar" }
    }

    private fun sanitizeImportDirectoryName(rawName: String): String {
        val sanitized = rawName
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "imported_avatar" }

        return sanitized
    }

    private fun createUniqueImportDirectory(baseName: String): File {
        val safeBaseName = sanitizeImportDirectoryName(baseName)

        var targetDir = File(userAvatarDir, safeBaseName)
        var suffix = 1
        while (targetDir.exists()) {
            targetDir = File(userAvatarDir, "${safeBaseName}_$suffix")
            suffix += 1
        }

        targetDir.mkdirs()
        return targetDir
    }

    private suspend fun importAvatarFromModelFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val displayName = resolveImportDisplayName(uri)
                ?: uri.lastPathSegment
                ?: "imported_avatar"
            val safeFileName = sanitizeImportFileName(displayName)
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            val currentExt = File(safeFileName).extension.lowercase()
            val normalizedExt = when {
                currentExt == "glb" || currentExt == "gltf" || currentExt == "mp4" || currentExt == "fbx" -> currentExt
                mimeType.contains("gltf+json") -> "gltf"
                mimeType.contains("gltf") -> "glb"
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("fbx") -> "fbx"
                else -> {
                    AppLogger.w(TAG, "Unable to determine model extension for uri=$uri mime=$mimeType name=$displayName")
                    return@withContext false
                }
            }

            val baseName = File(safeFileName).nameWithoutExtension
                .ifBlank { "imported_avatar" }
            val targetDir = createUniqueImportDirectory(baseName)
            val targetFile = File(targetDir, "$baseName.$normalizedExt")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use(inputStream::copyTo)
            } ?: run {
                AppLogger.w(TAG, "Import failed: unable to open model stream for uri=$uri")
                return@withContext false
            }

            if (normalizedExt == "fbx") {
                val modelInfo = FbxInspector.inspectModel(targetFile.absolutePath)
                if (modelInfo == null) {
                    val reason = FbxInspector.getLastError()
                    targetDir.deleteRecursively()
                    AppLogger.w(
                        TAG,
                        "Imported FBX inspection failed: ${targetFile.absolutePath}, reason=${reason.ifBlank { "<unknown>" }}"
                    )
                    return@withContext false
                }

                if (modelInfo.requiredExternalFiles.isNotEmpty()) {
                    targetDir.deleteRecursively()
                    AppLogger.w(
                        TAG,
                        "Imported FBX requires external resources and must be packaged as ZIP: ${modelInfo.requiredExternalFiles.joinToString()}"
                    )
                    return@withContext false
                }
            }

            if (normalizedExt == "gltf") {
                AppLogger.w(
                    TAG,
                    "Imported a standalone .gltf file. If it references external .bin/textures, import as ZIP instead."
                )
            }

            refreshAvatars()
            AppLogger.i(TAG, "Imported avatar model file: ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import avatar model file", e)
            false
        }
    }
    
    suspend fun importAvatarFromZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "avatar_import_${System.currentTimeMillis()}")
        try {
            tempDir.mkdirs()
            AppLogger.i(TAG, "Start import from ZIP: uri=$uri tempDir=${tempDir.absolutePath}")

            var entryCount = 0
            val sampledEntries = mutableListOf<String>()
            var extractedCharset: Charset? = null
            var malformedDecodeError: IllegalArgumentException? = null

            val charsetCandidates = ZIP_IMPORT_CHARSETS.ifEmpty { listOf(Charsets.UTF_8) }
            for (charset in charsetCandidates) {
                entryCount = 0
                sampledEntries.clear()
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                val tempRootCanonical = tempDir.canonicalPath + File.separator

                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream, charset).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                entryCount += 1
                                if (sampledEntries.size < 40) {
                                    sampledEntries.add(entry.name)
                                }

                                val targetFile = File(tempDir, entry.name)
                                val targetCanonical = targetFile.canonicalPath
                                if (!targetCanonical.startsWith(tempRootCanonical)) {
                                    AppLogger.w(TAG, "Skip suspicious ZIP entry outside target dir: ${entry.name}")
                                    zis.closeEntry()
                                    entry = zis.nextEntry
                                    continue
                                }

                                if (entry.isDirectory) {
                                    targetFile.mkdirs()
                                } else {
                                    targetFile.parentFile?.mkdirs()
                                    targetFile.outputStream().use(zis::copyTo)
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } ?: run {
                        AppLogger.w(TAG, "Import failed: unable to open ZIP stream for uri=$uri")
                        return@withContext false
                    }

                    extractedCharset = charset
                    if (charset != Charsets.UTF_8) {
                        AppLogger.i(TAG, "ZIP entry decode fallback applied: charset=${charset.name()}")
                    }
                    break
                } catch (e: IllegalArgumentException) {
                    val isMalformedEncoding = e.message?.contains("MALFORMED", ignoreCase = true) == true
                    if (!isMalformedEncoding) {
                        throw e
                    }

                    malformedDecodeError = e
                    AppLogger.w(
                        TAG,
                        "ZIP decode failed with charset=${charset.name()}, retrying with next charset."
                    )
                }
            }

            if (extractedCharset == null) {
                throw malformedDecodeError ?: IllegalArgumentException(
                    "Unable to decode ZIP entry names with supported charsets."
                )
            }

            AppLogger.i(
                TAG,
                "ZIP extracted: entryCount=$entryCount sampleEntries=${sampledEntries.joinToString()}"
            )

            val candidateDirs = tempDir.walkTopDown().filter { it.isDirectory }.toList()
            AppLogger.i(TAG, "Scanning ${candidateDirs.size} directory candidates for avatar configs.")

            val foundConfigs = mutableListOf<AvatarConfig>()
            candidateDirs.forEach { folder ->
                delegates.values.forEach { delegate ->
                    try {
                        val scanned = delegate.scanDirectory(folder, false)
                        if (scanned.isNotEmpty()) {
                            AppLogger.i(
                                TAG,
                                "Delegate ${delegate.type} matched ${scanned.size} config(s) at ${folder.absolutePath}"
                            )
                            foundConfigs.addAll(scanned)
                        }
                    } catch (scanError: Exception) {
                        AppLogger.e(
                            TAG,
                            "Delegate ${delegate.type} scan failed at ${folder.absolutePath}",
                            scanError
                        )
                    }
                }
            }

            val uniqueConfigs = foundConfigs.distinctBy { config ->
                "${config.type}|${config.getBasePath()}|${config.name}"
            }

            if (uniqueConfigs.isEmpty()) {
                val topLevelEntries = tempDir.listFiles()?.joinToString { file ->
                    if (file.isDirectory) "[D]${file.name}" else "[F]${file.name}"
                } ?: "<empty>"
                AppLogger.w(TAG, "No valid avatar configs found in the imported ZIP. topLevel=$topLevelEntries")
                AppLogger.w(
                    TAG,
                    "Import hints: DragonBones needs *_tex.json + *_tex.png; WebP needs .webp; MP4 needs .mp4; MMD needs .pmx/.pmd (optional .vmd); glTF needs .glb/.gltf (+ referenced resources); FBX supports direct .fbx only for self-contained assets, otherwise import ZIP with textures/resources."
                )
                return@withContext false
            }

            uniqueConfigs.forEach { config ->
                val sourcePath = config.getBasePath()
                if (sourcePath != null) {
                    val sourceDir = File(sourcePath)
                    if (sourceDir.exists()) {
                        val targetDir = File(userAvatarDir, sourceDir.name)
                        sourceDir.copyRecursively(targetDir, true)
                        AppLogger.i(
                            TAG,
                            "Imported avatar model: name=${sourceDir.name}, type=${config.type}, target=${targetDir.absolutePath}"
                        )
                    } else {
                        AppLogger.w(TAG, "Source directory does not exist for imported config: ${config.id} at $sourcePath")
                    }
                } else {
                    AppLogger.w(TAG, "Could not find path for imported config: ${config.id}")
                }
            }

            refreshAvatars()
            AppLogger.i(TAG, "Import completed: importedConfigs=${uniqueConfigs.size}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import avatar from ZIP", e)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun loadConfigsFromPrefs(): List<AvatarConfig> {
        val json = prefs.getString(KEY_CONFIGS, null) ?: return emptyList()
        return try {
            val result = decodePersistedAvatarConfigs(json, gson)
            if (result.invalidEntryIndexes.isNotEmpty()) {
                AppLogger.w(
                    TAG,
                    "Discarded invalid persisted avatar configs at indexes: " +
                        result.invalidEntryIndexes.joinToString()
                )
            }
            result.configs
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing avatar configs from JSON", e)
            emptyList()
        }
    }

    private fun saveConfigsToPrefs(configs: List<AvatarConfig>) {
        val json = gson.toJson(configs)
        prefs.edit { putString(KEY_CONFIGS, json) }
    }

    private fun loadInstanceSettingsFromPrefs(): Map<String, AvatarInstanceSettings> {
        val json = prefs.getString(KEY_INSTANCE_SETTINGS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, AvatarInstanceSettings>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing instance settings from JSON", e)
            emptyMap()
        }
    }

    private fun saveInstanceSettingsToPrefs(settings: Map<String, AvatarInstanceSettings>) {
        val json = gson.toJson(settings)
        prefs.edit { putString(KEY_INSTANCE_SETTINGS, json) }
    }

    private fun loadSettingsFromPrefs(): AvatarSettings {
        val json = prefs.getString(KEY_SETTINGS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AvatarSettings::class.java)
            } catch (e: Exception) {
                AvatarSettings()
            }
        } else {
            AvatarSettings()
        }
    }

    private fun saveSettingsToPrefs(settings: AvatarSettings) {
        val json = gson.toJson(settings)
        prefs.edit { putString(KEY_SETTINGS, json) }
        _settings.value = settings
    }
}
