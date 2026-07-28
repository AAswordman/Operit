package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.outlined.MoreVert
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterGroupCard
import com.ai.assistance.operit.data.model.GroupMemberConfig
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.preferences.CharacterCardBilingualData
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardDialog
import com.ai.assistance.operit.ui.features.settings.components.CompactAvatarPicker
import com.ai.assistance.operit.ui.features.settings.components.CompactTextFieldWithExpand
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.util.ColorQrCodeUtil
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64
import java.util.zip.CRC32
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ModelPromptsSettingsScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToMarket: () -> Unit = {},
    onNavigateToPersonaGeneration: () -> Unit = {},
    onNavigateToChatManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTagSavedHighlight by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var showDuplicateSuccessMessage by remember { mutableStateOf(false) }

    // 管理器
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val promptTagManager = remember { PromptTagManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }

    // 获取当前活跃目标（角色卡或群组）
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )

    // 状态
    var currentTab by remember { mutableStateOf(0) } // 0: 角色卡, 1: 标签, 2: 群组
    var refreshTrigger by remember { mutableStateOf(0) }

    // 角色卡相关状态
    val characterCardList by characterCardManager.characterCardListFlow.collectAsState(initial = emptyList())
    var showAddCharacterCardDialog by remember { mutableStateOf(false) }
    var showEditCharacterCardDialog by remember { mutableStateOf(false) }
    var editingCharacterCard by remember { mutableStateOf<CharacterCard?>(null) }
    var editingOriginalName by remember { mutableStateOf<String?>(null) }

    // 删除确认对话框状态
    var showDeleteCharacterCardConfirm by remember { mutableStateOf(false) }
    var deletingCharacterCardId by remember { mutableStateOf("") }
    var deletingCharacterCardName by remember { mutableStateOf("") }

    // 群组相关状态
    var showAddGroupCardDialog by remember { mutableStateOf(false) }
    var showEditGroupCardDialog by remember { mutableStateOf(false) }
    var editingGroupCard by remember { mutableStateOf<CharacterGroupCard?>(null) }
    var showDeleteGroupCardConfirm by remember { mutableStateOf(false) }
    var deletingGroupCard by remember { mutableStateOf<CharacterGroupCard?>(null) }

    // 重置确认对话框状态
    var showResetDefaultConfirm by remember { mutableStateOf(false) }

    // 酒馆角色卡导入相关状态
    var showImportSuccessMessage by remember { mutableStateOf(false) }
    var showImportErrorMessage by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    var showChatManagementPrompt by remember { mutableStateOf(false) }

    var showExportModeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportCharacterCardId by remember { mutableStateOf("") }
    var exportCharacterCardName by remember { mutableStateOf("") }
    var exportMode by remember { mutableStateOf(ExportMode.COLOR_QR) }
    var exportColorCount by remember { mutableStateOf(8) }
    var exportQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportTavernJson by remember { mutableStateOf("") }
    var exportTavernPngBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportErrorMessage by remember { mutableStateOf("") }
    var isExportGenerating by remember { mutableStateOf(false) }
    var showExportSavedDialog by remember { mutableStateOf(false) }
    var exportSavedPath by remember { mutableStateOf("") }

    // Avatar picker and cropper launcher
    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    editingCharacterCard?.let { card ->
                        val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, "avatar_${card.id}")
                        if (internalUri != null) {
                            activePromptManager.saveAiAvatarForPrompt(
                                ActivePrompt.CharacterCard(card.id),
                                internalUri.toString(),
                            )
                            Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    val cropGroupAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    editingGroupCard?.let { group ->
                        val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, "group_avatar_${group.id}")
                        if (internalUri != null) {
                            activePromptManager.saveAiAvatarForPrompt(
                                ActivePrompt.CharacterGroup(group.id),
                                internalUri.toString(),
                            )
                            Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        } else {
                            Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = context.getString(R.string.crop_avatar)
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropAvatarLauncher.launch(cropOptions)
    }

    fun launchGroupAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = context.getString(R.string.crop_avatar)
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropGroupAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchAvatarCrop(uri)
        }
    }

    val groupAvatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchGroupAvatarCrop(uri)
        }
    }

    suspend fun importTavernCharacterCardPng(fileUri: Uri): Result<String> {
        val result = context.contentResolver.openInputStream(fileUri).use { inputStream ->
            requireNotNull(inputStream) { context.getString(R.string.file_read_error_message) }
            characterCardManager.createCharacterCardFromTavernPng(inputStream)
        }

        val characterCardId = result.getOrNull() ?: return result
        runCatching {
            val internalUri = FileUtils.copyFileToInternalStorage(context, fileUri, "avatar_${characterCardId}")
                ?: throw IllegalStateException(context.getString(R.string.theme_copy_failed))
            activePromptManager.saveAiAvatarForPrompt(
                ActivePrompt.CharacterCard(characterCardId),
                internalUri.toString(),
            )
        }.onFailure { error ->
            AppLogger.w(
                "ModelPromptsSettingsScreen",
                "导入酒馆角色卡 PNG 后自动设置头像失败: characterCardId=$characterCardId, uri=$fileUri, error=${error.message}",
                error
            )
        }

        return result
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(fileUri)
                    val fileName = fileUri.lastPathSegment ?: ""

                    val result = when {
                        mimeType == "image/png" || fileName.lowercase().endsWith(".png") -> {
                            importTavernCharacterCardPng(fileUri)
                        }
                        mimeType == "application/json" || fileName.lowercase().endsWith(".json") -> {
                            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                requireNotNull(inputStream) { context.getString(R.string.file_read_error_message) }
                                val jsonContent = inputStream.bufferedReader().readText()
                                characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                            }
                        }
                        else -> {
                            // Fallback for unknown file types
                            try {
                                // Try JSON first
                                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                    requireNotNull(inputStream) { context.getString(R.string.file_read_error_message) }
                                    val jsonContent = inputStream.bufferedReader().readText()
                                    characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                                }
                            } catch (e: Exception) {
                                // If JSON fails, try PNG
                                importTavernCharacterCardPng(fileUri)
                            }
                        }
                    }

                    result.onSuccess {
                        showImportSuccessMessage = true
                        refreshTrigger++
                    }.onFailure { exception ->
                        importErrorMessage = exception.message ?: context.getString(R.string.unknown_error)
                        showImportErrorMessage = true
                    }
                } catch (e: Exception) {
                    importErrorMessage = context.getString(R.string.file_read_error, e.message ?: "")
                    showImportErrorMessage = true
                }
            }
        }
    }

    suspend fun handleColorQrImportFromUri(imageUri: Uri) {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(imageUri).use { inputStream ->
                    requireNotNull(inputStream) { context.getString(R.string.image_read_error) }
                    BitmapFactory.decodeStream(inputStream) ?: throw Exception(context.getString(R.string.image_parse_error))
                }
            }

            AppLogger.d(
                "ColorQrImport",
                "Start import uri=$imageUri bitmap=${bitmap.width}x${bitmap.height}"
            )

            fun formatThrowable(t: Throwable): String {
                val primary = t.message?.takeIf { it.isNotBlank() }
                val cause = t.cause?.message?.takeIf { it.isNotBlank() && it != primary }
                return buildString {
                    append(t::class.java.simpleName)
                    if (primary != null) append(": ").append(primary)
                    if (cause != null) append(" (cause: ").append(cause).append(")")
                }
            }

            val jsonContent = withContext(Dispatchers.Default) {
                var lastError: Throwable? = null

                val cropped = ColorQrCodeUtil.cropToQrRegion(bitmap)
                val baseCandidates = if (cropped != null) listOf(cropped, bitmap) else listOf(bitmap)
                AppLogger.d(
                    "ColorQrImport",
                    "Candidates=${baseCandidates.size} cropped=${cropped != null}"
                )

                for ((baseIndex, base) in baseCandidates.withIndex()) {
                    val maxDim = maxOf(base.width, base.height)
                    val scales = if (maxDim <= 900) {
                        floatArrayOf(1.0f, 1.5f, 2.0f, 0.75f, 0.5f)
                    } else {
                        floatArrayOf(1.0f, 1.25f, 1.5f, 0.75f, 0.5f, 0.35f)
                    }

                    AppLogger.d(
                        "ColorQrImport",
                        "Base[$baseIndex]=${base.width}x${base.height} scales=${scales.joinToString() }"
                    )

                    for (s in scales) {
                        try {
                            val scaled = if (s == 1.0f) {
                                base
                            } else {
                                val w = (base.width * s).toInt().coerceAtLeast(64)
                                val h = (base.height * s).toInt().coerceAtLeast(64)
                                Bitmap.createScaledBitmap(base, w, h, false)
                            }

                            val roi = ColorQrCodeUtil.cropToQrRegion(scaled) ?: scaled
                            AppLogger.d(
                                "ColorQrImport",
                                "Try base[$baseIndex] scale=$s scaled=${scaled.width}x${scaled.height} roi=${roi.width}x${roi.height}"
                            )
                            return@withContext ColorQrCodeUtil.decodeToString(roi)
                        } catch (t: Throwable) {
                            lastError = t
                            AppLogger.w(
                                "ColorQrImport",
                                "Fail base[$baseIndex] scale=$s: ${formatThrowable(t)}"
                            )
                        }
                    }
                }
                throw (lastError ?: Exception(context.getString(R.string.color_qr_not_recognized)))
            }

            val result = characterCardManager.createCharacterCardFromTavernJson(jsonContent)
            result.onSuccess {
                showImportSuccessMessage = true
                refreshTrigger++
            }.onFailure { exception ->
                importErrorMessage = exception.message ?: context.getString(R.string.unknown_error)
                showImportErrorMessage = true
            }
        } catch (e: Exception) {
            AppLogger.e("ColorQrImport", "Import failed", e)
            val msg = e.message ?: e::class.java.simpleName
            val causeMsg = e.cause?.message
            importErrorMessage = context.getString(
                R.string.file_read_error,
                if (causeMsg.isNullOrBlank() || causeMsg == msg) msg else "$msg (cause: $causeMsg)"
            )
            showImportErrorMessage = true
        }
    }

    val colorQrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            scope.launch {
                handleColorQrImportFromUri(imageUri)
            }
        }
    }

    var tempColorQrCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takeColorQrPictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempColorQrCameraUri?.let { imageUri ->
                scope.launch {
                    handleColorQrImportFromUri(imageUri)
                }
            }
        }
    }

    var isTagImporting by remember { mutableStateOf(false) }
    var isTagExporting by remember { mutableStateOf(false) }
    var showTagExportSelectionDialog by remember { mutableStateOf(false) }
    var selectedTagExportIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun getTmpCameraUri(context: Context): Uri {
        val authority = "${context.applicationContext.packageName}.fileprovider"
        val tmpFile = File.createTempFile("color_qr_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        return FileProvider.getUriForFile(context, authority, tmpFile)
    }

    val tagImportFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            if (isTagImporting) return@rememberLauncherForActivityResult
            scope.launch {
                isTagImporting = true
                try {
                    val jsonContent = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(fileUri).use { inputStream ->
                            requireNotNull(inputStream) { context.getString(R.string.file_read_error_message) }
                            BufferedReader(InputStreamReader(inputStream)).readText()
                        }
                    }
                    val result = importPromptTagsFromJsonContent(
                        jsonContent = jsonContent,
                        promptTagManager = promptTagManager
                    )
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.tag_import_summary,
                            result.createdCount,
                            result.updatedCount,
                            result.skippedCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    val errorMessage = when (e.message) {
                        "invalid format", "missing tags array" -> context.getString(R.string.tag_import_invalid_format)
                        else -> e.message ?: context.getString(R.string.unknown_error)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_failed, errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isTagImporting = false
                }
            }
        }
    }

    fun exportCustomTagsToJson(selectedIds: Set<String>) {
        if (isTagExporting) return
        scope.launch {
            isTagExporting = true
            try {
                val tags = promptTagManager.getAllTags()
                if (tags.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.tag_export_no_custom_tags), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val selectedTags = tags.filter { it.id in selectedIds }
                if (selectedTags.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.tag_export_select_at_least_one), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val exportJson = buildPromptTagExportJson(selectedTags)
                val fileName = "prompt_tags_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val ok = saveBytesToDownloads(
                    context = context,
                    bytes = exportJson.toByteArray(Charsets.UTF_8),
                    fileName = fileName,
                    mimeType = "application/json"
                )
                if (ok) {
                    exportSavedPath = "${Environment.DIRECTORY_DOWNLOADS}/Operit/exports/$fileName"
                    showExportSavedDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.export_failed_with_reason, e.message ?: context.getString(R.string.unknown_error)),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isTagExporting = false
            }
        }
    }

    // 标签相关状态
    val allTags by promptTagManager.allTagsFlow.collectAsState(initial = emptyList())
    val customTagsForExport by remember(allTags) {
        derivedStateOf {
            allTags.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showEditTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<PromptTag?>(null) }

    // 标签删除确认对话框状态
    var showDeleteTagConfirm by remember { mutableStateOf(false) }
    var deletingTagId by remember { mutableStateOf("") }
    var deletingTagName by remember { mutableStateOf("") }

    // 初始化
    LaunchedEffect(Unit) {
        characterCardManager.initializeIfNeeded()
        characterGroupCardManager.initializeIfNeeded()
    }

    // 获取所有角色卡
    var allCharacterCards by remember { mutableStateOf(emptyList<CharacterCard>()) }
    LaunchedEffect(characterCardList, refreshTrigger) {
        scope.launch {
            val cards = characterCardManager.getAllCharacterCards()
            allCharacterCards = cards
        }
    }
    val allCharacterGroups by characterGroupCardManager.allCharacterGroupCardsFlow.collectAsState(initial = emptyList())

    // 保存角色卡
    fun saveCharacterCard() {
        editingCharacterCard?.let { card ->
            val originalNameSnapshot = editingOriginalName
            val isExistingCard = card.id.isNotEmpty()
            val nameChanged = isExistingCard &&
                    !originalNameSnapshot.isNullOrEmpty() &&
                    originalNameSnapshot != card.name

            scope.launch {
                if (!isExistingCard) {
                    val newCardId = characterCardManager.createCharacterCard(card)
                    activePromptManager.saveCustomChatTitleForPrompt(
                        ActivePrompt.CharacterCard(newCardId),
                        card.name.ifEmpty { null },
                    )
                } else {
                    characterCardManager.updateCharacterCard(card)
                    activePromptManager.saveCustomChatTitleForPrompt(
                        ActivePrompt.CharacterCard(card.id),
                        card.name.ifEmpty { null },
                    )
                }
                showAddCharacterCardDialog = false
                showEditCharacterCardDialog = false
                editingCharacterCard = null
                editingOriginalName = null
                showSaveSuccessMessage = true
                refreshTrigger++
                if (nameChanged) {
                    showChatManagementPrompt = true
                }
            }
        }
    }

    // 保存标签
    fun saveTag() {
        editingTag?.let { tag ->
            scope.launch {
                if (tag.id.isEmpty()) {
                    // 新建
                    promptTagManager.createPromptTag(
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent,
                        tagType = tag.tagType
                    )
                    showAddTagDialog = false
                    editingTag = null
                    showTagSavedHighlight = true
                } else {
                    // 更新
                    promptTagManager.updatePromptTag(
                        id = tag.id,
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent,
                        tagType = tag.tagType
                    )
                    showSaveSuccessMessage = true
                }
                showEditTagDialog = false
            }
        }
    }

    // 删除角色卡
    fun deleteCharacterCard(id: String) {
        scope.launch {
            characterCardManager.deleteCharacterCard(id)
            refreshTrigger++
        }
    }

    // 显示删除角色卡确认对话框
    fun showDeleteCharacterCardConfirm(id: String, name: String) {
        deletingCharacterCardId = id
        deletingCharacterCardName = name
        showDeleteCharacterCardConfirm = true
    }

    // 确认删除角色卡
    fun confirmDeleteCharacterCard() {
        scope.launch {
            characterCardManager.deleteCharacterCard(deletingCharacterCardId)
            showDeleteCharacterCardConfirm = false
            deletingCharacterCardId = ""
            deletingCharacterCardName = ""
            refreshTrigger++
            showChatManagementPrompt = true
        }
    }

    // 确认重置默认角色卡
    fun confirmResetDefaultCharacterCard() {
        scope.launch {
            characterCardManager.resetDefaultCharacterCard()
            showResetDefaultConfirm = false
            refreshTrigger++
            Toast.makeText(context, context.getString(R.string.reset_successful), Toast.LENGTH_SHORT).show()
        }
    }

    // 复制角色卡
    fun duplicateCharacterCard(card: CharacterCard) {
        scope.launch {
            val duplicatedCard = card.copy(
                id = "", // 将由createCharacterCard生成新ID
                name = "${card.name}" + context.getString(R.string.card_copy_suffix),
                isDefault = false
            )
            val newCardId = characterCardManager.createCharacterCard(duplicatedCard)
            characterCardManager.cloneBindingsFromCharacterCard(card.id, newCardId)
            activePromptManager.saveCustomChatTitleForPrompt(
                ActivePrompt.CharacterCard(newCardId),
                duplicatedCard.name.ifEmpty { null },
            )
            showDuplicateSuccessMessage = true
            refreshTrigger++
        }
    }

    // 保存群组角色卡
    fun saveGroupCard() {
        editingGroupCard?.let { group ->
            scope.launch {
                val isNew = group.id.isBlank()
                if (isNew) {
                    val newGroupId = characterGroupCardManager.createCharacterGroupCard(group)
                    activePromptManager.saveCustomChatTitleForPrompt(
                        ActivePrompt.CharacterGroup(newGroupId),
                        group.name.ifEmpty { null },
                    )
                } else {
                    characterGroupCardManager.updateCharacterGroupCard(group)
                    activePromptManager.saveCustomChatTitleForPrompt(
                        ActivePrompt.CharacterGroup(group.id),
                        group.name.ifEmpty { null },
                    )
                }
                showAddGroupCardDialog = false
                showEditGroupCardDialog = false
                editingGroupCard = null
                showSaveSuccessMessage = true
                refreshTrigger++
            }
        }
    }

    fun duplicateGroupCard(group: CharacterGroupCard) {
        scope.launch {
            val duplicatedName = group.name + context.getString(R.string.card_copy_suffix)
            val newGroupId = characterGroupCardManager.duplicateCharacterGroupCard(group.id, duplicatedName)
            if (!newGroupId.isNullOrBlank()) {
                activePromptManager.saveCustomChatTitleForPrompt(
                    ActivePrompt.CharacterGroup(newGroupId),
                    duplicatedName.ifEmpty { null },
                )
            }
            showDuplicateSuccessMessage = true
            refreshTrigger++
        }
    }

    fun confirmDeleteGroupCard() {
        val group = deletingGroupCard ?: return
        scope.launch {
            characterGroupCardManager.deleteCharacterGroupCard(group.id)
            val currentPrompt = activePrompt
            if (currentPrompt is ActivePrompt.CharacterGroup && currentPrompt.id == group.id) {
                activePromptManager.setActivePrompt(
                    ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
                )
            }
            deletingGroupCard = null
            showDeleteGroupCardConfirm = false
            refreshTrigger++
        }
    }

    // 删除标签
    fun deleteTag(id: String) {
        scope.launch {
            promptTagManager.deletePromptTag(id)
            }
        }

    // 显示删除标签确认对话框
    fun showDeleteTagConfirm(id: String, name: String) {
        deletingTagId = id
        deletingTagName = name
        showDeleteTagConfirm = true
    }

    // 确认删除标签
    fun confirmDeleteTag() {
        scope.launch {
            promptTagManager.deletePromptTag(deletingTagId)
            showDeleteTagConfirm = false
            deletingTagId = ""
            deletingTagName = ""
        }
    }

    CustomScaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标签栏（移除旧配置选项）
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.character_cards), fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.tags), fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.character_groups), fontSize = 12.sp)
                        }
                    }
                }

                // 内容区域
                when (currentTab) {
                    0 -> CharacterCardTab(
                        characterCards = allCharacterCards,
                        activePrompt = activePrompt,
                        allTags = allTags,
                        onAddCharacterCard = {
                            editingOriginalName = null
                            editingCharacterCard = CharacterCard(
                                id = "",
                                name = "",
                                description = "",
                                characterSetting = CharacterCardBilingualData.getDefaultCharacterSetting(context),
                                otherContentChat = CharacterCardBilingualData.getDefaultOtherContentChat(context),
                                otherContentVoice = "",
                                attachedTagIds = emptyList(),
                                advancedCustomPrompt = "",
                                marks = ""
                            )
                            showAddCharacterCardDialog = true
                        },
                        onEditCharacterCard = { card ->
                            editingOriginalName = card.name
                            editingCharacterCard = card.copy()
                            showEditCharacterCardDialog = true
                        },
                        onDeleteCharacterCard = { card -> showDeleteCharacterCardConfirm(card.id, card.name) },
                        onDuplicateCharacterCard = { card -> duplicateCharacterCard(card) },
                        onResetDefaultCharacterCard = { showResetDefaultConfirm = true },
                        onSetActiveCharacterCard = { cardId ->
                            scope.launch {
                                activePromptManager.setActivePrompt(ActivePrompt.CharacterCard(cardId))
                                refreshTrigger++
                            }
                        },
                        onNavigateToPersonaGeneration = onNavigateToPersonaGeneration,
                        onImportTavernCard = {
                            filePickerLauncher.launch("*/*")
                        },
                        onImportColorQrCode = {
                            colorQrImagePickerLauncher.launch("image/*")
                        },
                        onScanColorQrCode = {
                            val uri = getTmpCameraUri(context)
                            tempColorQrCameraUri = uri
                            takeColorQrPictureLauncher.launch(uri)
                        },
                        onExportCharacterCard = { cardId, cardName ->
                            exportCharacterCardId = cardId
                            exportCharacterCardName = cardName
                            exportMode = ExportMode.COLOR_QR
                            exportTavernJson = ""
                            exportColorCount = 8
                            exportQrBitmap = null
                            exportTavernPngBytes = null
                            exportErrorMessage = ""
                            showExportModeDialog = true
                        }
                    )
                    1 -> TagTab(
                        tags = allTags,
                        onAddTag = {
                            editingTag = PromptTag(
                                id = "",
                                name = "",
                                description = "",
                                promptContent = "",
                                tagType = TagType.CUSTOM
                            )
                            showAddTagDialog = true
                        },
                        onEditTag = { tag ->
                            editingTag = tag.copy()
                            showEditTagDialog = true
                        },
                        onDeleteTag = { tag -> showDeleteTagConfirm(tag.id, tag.name) },
                        onImportTags = { tagImportFilePickerLauncher.launch("*/*") },
                        onExportTags = {
                            if (customTagsForExport.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.tag_export_no_custom_tags), Toast.LENGTH_SHORT).show()
                            } else {
                                selectedTagExportIds = customTagsForExport.map { it.id }.toSet()
                                showTagExportSelectionDialog = true
                            }
                        },
                        isImporting = isTagImporting,
                        isExporting = isTagExporting,
                        onNavigateToMarket = onNavigateToMarket
                    )
                    2 -> GroupCardTab(
                        groups = allCharacterGroups,
                        characterCards = allCharacterCards,
                        activePrompt = activePrompt,
                        onAddGroup = {
                            editingGroupCard = CharacterGroupCard(
                                id = "",
                                name = "",
                                description = ""
                            )
                            showAddGroupCardDialog = true
                        },
                        onEditGroup = { group ->
                            editingGroupCard = group
                            showEditGroupCardDialog = true
                        },
                        onDeleteGroup = { group ->
                            deletingGroupCard = group
                            showDeleteGroupCardConfirm = true
                        },
                        onDuplicateGroup = { group ->
                            duplicateGroupCard(group)
                        },
                        onSetActiveGroup = { groupId ->
                            scope.launch {
                                activePromptManager.setActivePrompt(ActivePrompt.CharacterGroup(groupId))
                                refreshTrigger++
                            }
                        }
                    )
                }
            }

            // 成功保存消息
            if (showSaveSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    showSaveSuccessMessage = false
                }

                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                            imageVector = Icons.Default.Check,
                                        contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                                    )
                        Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                            text = stringResource(R.string.save_successful),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                                    )
                                }
                            }
                        }

            // 创建副本成功消息
            if (showDuplicateSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    showDuplicateSuccessMessage = false
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.duplicate_successful),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 导入成功消息
            if (showImportSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showImportSuccessMessage = false
                }

                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tavern_card_import_success),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 导入失败消息
            if (showImportErrorMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showImportErrorMessage = false
                }

                Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                            ) {
                                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                                    Text(
                                text = stringResource(R.string.tavern_card_import_failed),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (importErrorMessage.isNotBlank()) {
                                Text(
                                    text = importErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    // 新建角色卡对话框
    if (showAddCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContentChat = "",
                otherContentVoice = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showAddCharacterCardDialog = false
                editingCharacterCard = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
            },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        activePromptManager.saveAiAvatarForPrompt(
                            ActivePrompt.CharacterCard(it.id),
                            null,
                        )
                        refreshTrigger++
                    }
                }
            }
        )
    }

    // 编辑角色卡对话框
    if (showEditCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContentChat = "",
                otherContentVoice = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showEditCharacterCardDialog = false
                editingCharacterCard = null
                editingOriginalName = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
                                },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        activePromptManager.saveAiAvatarForPrompt(
                            ActivePrompt.CharacterCard(it.id),
                            null,
                        )
                        refreshTrigger++
                    }
                }
            }
        )
                        }

    // 新建标签对话框
    if (showAddTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showAddTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
            }
        )
    }

    // 编辑标签对话框
    if (showEditTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showEditTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
                                        }
        )
    }

    if (showAddGroupCardDialog) {
        GroupCardDialog(
            group = editingGroupCard ?: CharacterGroupCard(id = "", name = ""),
            allCharacterCards = allCharacterCards,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showAddGroupCardDialog = false
                editingGroupCard = null
            },
            onSave = { group ->
                editingGroupCard = group
                saveGroupCard()
            },
            onAvatarChange = {
                groupAvatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingGroupCard?.let { group ->
                        activePromptManager.saveAiAvatarForPrompt(
                            ActivePrompt.CharacterGroup(group.id),
                            null,
                        )
                        Toast.makeText(context, context.getString(R.string.avatar_reset), Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    }
                }
            }
        )
    }

    if (showEditGroupCardDialog) {
        GroupCardDialog(
            group = editingGroupCard ?: CharacterGroupCard(id = "", name = ""),
            allCharacterCards = allCharacterCards,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showEditGroupCardDialog = false
                editingGroupCard = null
            },
            onSave = { group ->
                editingGroupCard = group
                saveGroupCard()
            },
            onAvatarChange = {
                groupAvatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingGroupCard?.let { group ->
                        activePromptManager.saveAiAvatarForPrompt(
                            ActivePrompt.CharacterGroup(group.id),
                            null,
                        )
                        Toast.makeText(context, context.getString(R.string.avatar_reset), Toast.LENGTH_SHORT).show()
                        refreshTrigger++
                    }
                }
            }
        )
    }

    if (showDeleteGroupCardConfirm && deletingGroupCard != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteGroupCardConfirm = false
                deletingGroupCard = null
            },
            title = { Text(stringResource(R.string.delete_character_group)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_character_group_confirm,
                        deletingGroupCard?.name ?: ""
                    )
                )
            },
            confirmButton = {
                Button(onClick = { confirmDeleteGroupCard() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteGroupCardConfirm = false
                        deletingGroupCard = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除角色卡确认对话框
    if (showDeleteCharacterCardConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteCharacterCardConfirm = false
                deletingCharacterCardId = ""
                deletingCharacterCardName = ""
            },
            title = { Text(stringResource(R.string.delete_character_card)) },
            text = { Text(stringResource(R.string.delete_character_card_confirm, deletingCharacterCardName)) },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteCharacterCard() }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteCharacterCardConfirm = false
                        deletingCharacterCardId = ""
                        deletingCharacterCardName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 角色卡变更提示
    if (showChatManagementPrompt) {
        AlertDialog(
            onDismissRequest = { showChatManagementPrompt = false },
            title = { Text(stringResource(R.string.character_card_change_title)) },
            text = { Text(stringResource(R.string.character_card_change_prompt)) },
            confirmButton = {
                Button(
                    onClick = {
                        showChatManagementPrompt = false
                        onNavigateToChatManagement()
                    }
                ) {
                    Text(stringResource(R.string.go_to_chat_management))
                }
            },
            dismissButton = {
                TextButton(onClick = { showChatManagementPrompt = false }) {
                    Text(stringResource(R.string.maybe_later))
                }
            }
        )
    }

    // 重置默认角色卡确认对话框
    if (showResetDefaultConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDefaultConfirm = false },
            title = { Text(stringResource(R.string.reset_default_character)) },
            text = { Text(stringResource(R.string.reset_default_character_confirm)) },
            confirmButton = {
                Button(
                    onClick = { confirmResetDefaultCharacterCard() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDefaultConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除标签确认对话框
    if (showDeleteTagConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteTagConfirm = false
                deletingTagId = ""
                deletingTagName = ""
            },
            title = { Text(stringResource(R.string.delete_tag)) },
            text = { Text(stringResource(R.string.delete_tag_confirm, deletingTagName)) },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteTag() }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteTagConfirm = false
                        deletingTagId = ""
                        deletingTagName = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showTagExportSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showTagExportSelectionDialog = false },
            title = { Text(stringResource(R.string.tag_export_select_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedTagExportIds = customTagsForExport.map { it.id }.toSet()
                            },
                            enabled = !isTagExporting,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.tag_export_select_all), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { selectedTagExportIds = emptySet() },
                            enabled = !isTagExporting,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.tag_export_clear_all), fontSize = 12.sp)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        customTagsForExport.forEach { tag ->
                            val checked = selectedTagExportIds.contains(tag.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isTagExporting) {
                                        selectedTagExportIds =
                                            if (checked) selectedTagExportIds - tag.id
                                            else selectedTagExportIds + tag.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedTagExportIds =
                                            if (isChecked) selectedTagExportIds + tag.id
                                            else selectedTagExportIds - tag.id
                                    },
                                    enabled = !isTagExporting
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (tag.description.isNotBlank()) {
                                        Text(
                                            text = tag.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedTagExportIds
                        showTagExportSelectionDialog = false
                        exportCustomTagsToJson(ids)
                    },
                    enabled = selectedTagExportIds.isNotEmpty() && !isTagExporting
                ) {
                    Text(stringResource(R.string.export))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTagExportSelectionDialog = false },
                    enabled = !isTagExporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showExportModeDialog) {
        AlertDialog(
            onDismissRequest = { showExportModeDialog = false },
            title = { Text(stringResource(R.string.export)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = exportCharacterCardName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (exportCharacterCardId.isBlank() || isExportGenerating) return@OutlinedCard
                                showExportModeDialog = false
                                scope.launch {
                                    try {
                                        isExportGenerating = true
                                        exportErrorMessage = ""
                                        val json = characterCardManager
                                            .exportCharacterCardToTavernJson(exportCharacterCardId)
                                            .getOrThrow()
                                        val fileName = "tavern_card_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                                        val ok = saveBytesToDownloads(
                                            context = context,
                                            bytes = json.toByteArray(Charsets.UTF_8),
                                            fileName = fileName,
                                            mimeType = "application/json"
                                        )
                                        if (ok) {
                                            exportSavedPath = "${Environment.DIRECTORY_DOWNLOADS}/Operit/exports/$fileName"
                                            showExportSavedDialog = true
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, (e.message ?: context.getString(R.string.save_failed)), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isExportGenerating = false
                                    }
                                }
                            }
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.tavern_json)) },
                                supportingContent = { Text(stringResource(R.string.export_tavern_json_desc)) },
                                leadingContent = { Icon(Icons.Default.DataObject, contentDescription = null) }
                            )
                        }

                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (exportCharacterCardId.isBlank() || isExportGenerating) return@OutlinedCard
                                showExportModeDialog = false
                                scope.launch {
                                    try {
                                        isExportGenerating = true
                                        exportErrorMessage = ""
                                        val json = characterCardManager
                                            .exportCharacterCardToTavernJson(exportCharacterCardId)
                                            .getOrThrow()

                                        val avatarUri = withContext(Dispatchers.IO) {
                                            runCatching {
                                                userPreferencesManager.getAiAvatarForCharacterCardFlow(exportCharacterCardId).first()
                                            }.getOrNull()
                                        }

                                        val base = withContext(Dispatchers.IO) {
                                            val src = avatarUri?.let { uriStr ->
                                                runCatching {
                                                    val uri = Uri.parse(uriStr)
                                                    context.contentResolver.openInputStream(uri).use { input ->
                                                        if (input == null) null else BitmapFactory.decodeStream(input)
                                                    }
                                                }.getOrNull()
                                            }
                                            if (src != null) {
                                                centerCropSquare(src, 512)
                                            } else {
                                                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
                                                    eraseColor(android.graphics.Color.WHITE)
                                                }
                                            }
                                        }

                                        val rawPng = withContext(Dispatchers.Default) {
                                            ByteArrayOutputStream().use { out ->
                                                base.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                                out.toByteArray()
                                            }
                                        }
                                        val pngBytes = insertTavernTextChunk(rawPng, json)
                                        val fileName = "tavern_card_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
                                        val ok = saveBytesToDownloads(
                                            context = context,
                                            bytes = pngBytes,
                                            fileName = fileName,
                                            mimeType = "image/png"
                                        )
                                        if (ok) {
                                            exportSavedPath = "${Environment.DIRECTORY_DOWNLOADS}/Operit/exports/$fileName"
                                            showExportSavedDialog = true
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, (e.message ?: context.getString(R.string.save_failed)), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isExportGenerating = false
                                    }
                                }
                            }
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.tavern_png)) },
                                supportingContent = { Text(stringResource(R.string.export_tavern_png_desc)) },
                                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) }
                            )
                        }

                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (exportCharacterCardId.isBlank()) return@OutlinedCard
                                exportMode = ExportMode.COLOR_QR
                                exportTavernJson = ""
                                exportColorCount = 8
                                exportQrBitmap = null
                                exportTavernPngBytes = null
                                exportErrorMessage = ""
                                showExportModeDialog = false
                                showExportDialog = true
                            }
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.color_qr_code)) },
                                supportingContent = { Text(stringResource(R.string.export_color_qr_desc)) },
                                leadingContent = { Icon(Icons.Default.QrCode2, contentDescription = null) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportModeDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showExportSavedDialog) {
        AlertDialog(
            onDismissRequest = { showExportSavedDialog = false },
            title = { Text(stringResource(R.string.save_successful)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.saved_to), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(exportSavedPath)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("export_path", exportSavedPath))
                        Toast.makeText(context, context.getString(R.string.copy), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportSavedDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showExportDialog) {
        LaunchedEffect(exportCharacterCardId) {
            if (exportCharacterCardId.isBlank()) return@LaunchedEffect
            isExportGenerating = true
            exportErrorMessage = ""
            exportQrBitmap = null
            exportTavernJson = ""
            exportTavernPngBytes = null

            val jsonResult = characterCardManager.exportCharacterCardToTavernJson(exportCharacterCardId)
            jsonResult.onSuccess { json ->
                exportTavernJson = json
            }.onFailure { e ->
                exportErrorMessage = e.message ?: context.getString(R.string.unknown_error)
            }
            isExportGenerating = false
        }

        LaunchedEffect(exportTavernJson, exportColorCount) {
            if (exportTavernJson.isBlank()) return@LaunchedEffect
            exportErrorMessage = ""
            isExportGenerating = true
            exportQrBitmap = null
            try {
                exportQrBitmap = withContext(Dispatchers.Default) {
                    ColorQrCodeUtil.generate(
                        text = exportTavernJson,
                        colorCount = exportColorCount,
                        moduleSizePx = 10,
                        marginModules = 4
                    )
                }
            } catch (e: Exception) {
                exportErrorMessage = e.message ?: context.getString(R.string.unknown_error)
            }
            isExportGenerating = false
        }

        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                exportCharacterCardId = ""
                exportCharacterCardName = ""
                exportTavernJson = ""
                exportQrBitmap = null
                exportTavernPngBytes = null
                exportErrorMessage = ""
                isExportGenerating = false
            },
            title = { Text(stringResource(R.string.export)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = exportCharacterCardName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf(2, 4, 8, 16)
                        options.forEach { count ->
                            val selected = exportColorCount == count
                            val onClick = { exportColorCount = count }
                            if (selected) {
                                Button(
                                    onClick = onClick,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.color_count_suffix, count), fontSize = 12.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onClick,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(R.string.color_count_suffix, count), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    when {
                        isExportGenerating -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.processing), fontSize = 12.sp)
                            }
                        }
                        exportErrorMessage.isNotBlank() -> {
                            Text(
                                text = exportErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                        exportQrBitmap != null -> {
                            Image(
                                bitmap = exportQrBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(260.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bmp = exportQrBitmap
                        if (bmp == null) {
                            Toast.makeText(context, context.getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val fileName = "character_card_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
                            val ok = saveBitmapToGallery(context, bmp, fileName)
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.image_saved) else context.getString(R.string.save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = exportQrBitmap != null && !isExportGenerating
                ) {
                    Text(stringResource(R.string.save_image))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // 手动添加标签成功的高亮提示（底部显著提示，1.5s 自动消失）
    if (showTagSavedHighlight) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showTagSavedHighlight = false
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.save_successful),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

enum class CharacterCardSortOption {
    DEFAULT,
    NAME_ASC,
    CREATED_DESC
}

enum class ExportMode {
    TAVERN_JSON,
    TAVERN_PNG,
    COLOR_QR
}

// 角色卡标签页
@Composable
fun CharacterCardTab(
    characterCards: List<CharacterCard>,
    activePrompt: ActivePrompt,
    allTags: List<PromptTag>,
    onAddCharacterCard: () -> Unit,
    onEditCharacterCard: (CharacterCard) -> Unit,
    onDeleteCharacterCard: (CharacterCard) -> Unit,
    onDuplicateCharacterCard: (CharacterCard) -> Unit,
    onResetDefaultCharacterCard: () -> Unit,
    onSetActiveCharacterCard: (String) -> Unit,
    onNavigateToPersonaGeneration: () -> Unit,
    onImportTavernCard: () -> Unit,
    onImportColorQrCode: () -> Unit,
    onScanColorQrCode: () -> Unit,
    onExportCharacterCard: (String, String) -> Unit
) {
    val sortOptionNameState = rememberLocal(
        key = "ModelPromptsSettingsScreen.CharacterCardTab.sortOption",
        defaultValue = CharacterCardSortOption.DEFAULT.name
    )
    val sortOption = remember(sortOptionNameState.value) {
        runCatching { CharacterCardSortOption.valueOf(sortOptionNameState.value) }
            .getOrDefault(CharacterCardSortOption.DEFAULT)
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val sortedCharacterCards = remember(characterCards, sortOption) {
        when (sortOption) {
            CharacterCardSortOption.DEFAULT -> characterCards
            CharacterCardSortOption.NAME_ASC -> characterCards.sortedBy { it.name.lowercase() }
            CharacterCardSortOption.CREATED_DESC -> characterCards.sortedByDescending { it.updatedAt }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.character_card_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：功能按钮 + 排序图标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var importMenuExpanded by remember { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onAddCharacterCard,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.create_new),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToPersonaGeneration,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.ai_creation),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { importMenuExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = stringResource(R.string.import_tavern_card),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = importMenuExpanded,
                                onDismissRequest = { importMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_tavern_card)) },
                                    onClick = {
                                        importMenuExpanded = false
                                        onImportTavernCard()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileDownload, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_color_qr)) },
                                    onClick = {
                                        importMenuExpanded = false
                                        onImportColorQrCode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Image, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.scan_color_qr)) },
                                    onClick = {
                                        importMenuExpanded = false
                                        onScanColorQrCode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = stringResource(R.string.character_card_sort),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.character_card_sort_default)) },
                                onClick = {
                                    sortOptionNameState.value = CharacterCardSortOption.DEFAULT.name
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.character_card_sort_by_name)) },
                                onClick = {
                                    sortOptionNameState.value = CharacterCardSortOption.NAME_ASC.name
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.character_card_sort_by_created)) },
                                onClick = {
                                    sortOptionNameState.value = CharacterCardSortOption.CREATED_DESC.name
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 角色卡列表
        items(sortedCharacterCards) { characterCard ->
            CharacterCardItem(
                characterCard = characterCard,
                isActive = (activePrompt as? ActivePrompt.CharacterCard)?.id == characterCard.id,
                allTags = allTags,
                onEdit = { onEditCharacterCard(characterCard) },
                onDelete = { onDeleteCharacterCard(characterCard) },
                onDuplicate = { onDuplicateCharacterCard(characterCard) },
                onReset = onResetDefaultCharacterCard,
                onSetActive = { onSetActiveCharacterCard(characterCard.id) },
                onExport = { onExportCharacterCard(characterCard.id, characterCard.name) }
            )
        }
    }
}

@Composable
private fun SettingsListAvatar(
    avatarUri: String?,
    fallbackIcon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = Uri.parse(avatarUri),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// 角色卡项目
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardItem(
    characterCard: CharacterCard,
    isActive: Boolean,
    allTags: List<PromptTag>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onSetActive: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val avatarUri by remember(characterCard.id) {
        userPreferencesManager.getAiAvatarForCharacterCardFlow(characterCard.id)
    }.collectAsState(initial = null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题和操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsListAvatar(
                        avatarUri = avatarUri,
                        fallbackIcon = Icons.Default.AccountCircle,
                        contentDescription = "Character Avatar"
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = characterCard.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.height(4.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.currently_active), fontSize = 10.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.currently_active),
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }

                // 三点菜单
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                    ) {
                        if (!isActive) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_active)) },
                                onClick = {
                                    onSetActive()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate)) },
                            onClick = {
                                onDuplicate()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export)) },
                            onClick = {
                                onExport()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    
                        if (characterCard.isDefault) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset)) },
                                onClick = {
                                    onReset()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    
                        if (!characterCard.isDefault) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }

        // 角色设定预览
        if (characterCard.characterSetting.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
                                Text(
                    text = stringResource(R.string.character_setting_preview, characterCard.characterSetting.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // 其他内容（聊天）预览
            if (characterCard.otherContentChat.isNotBlank()) {
                                    Text(
                    text = stringResource(R.string.other_content_chat_preview, characterCard.otherContentChat.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // 附着的标签
            if (characterCard.attachedTagIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    characterCard.attachedTagIds.take(3).forEach { tagId ->
                        val tag = allTags.find { it.id == tagId }
                        tag?.let {
                            AssistChip(
                                onClick = { },
                                label = { Text(it.name, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.height(24.dp)
                                        )
                        }
                    }
                    if (characterCard.attachedTagIds.size > 3) {
                                        Text(
                            text = "+${characterCard.attachedTagIds.size - 3}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 高级自定义提示词预览
            if (characterCard.advancedCustomPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                    text = stringResource(R.string.advanced_custom_preview, characterCard.advancedCustomPrompt.take(40)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        }
    }
// 标签标签页
@Composable
fun TagTab(
    tags: List<PromptTag>,
    onAddTag: () -> Unit,
    onEditTag: (PromptTag) -> Unit,
    onDeleteTag: (PromptTag) -> Unit,
    onImportTags: () -> Unit,
    onExportTags: () -> Unit,
    isImporting: Boolean,
    isExporting: Boolean,
    onNavigateToMarket: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                    Text(
                        text = stringResource(R.string.tag_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onAddTag,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_new_tag), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：导入/导出/市场
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onImportTags,
                        enabled = !isImporting && !isExporting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.import_action), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onExportTags,
                        enabled = !isImporting && !isExporting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.export), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToMarket,
                        enabled = !isImporting && !isExporting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.tag_market), fontSize = 12.sp)
                    }
                }

                if (isImporting || isExporting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.processing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val sortedTags = tags.sortedBy { it.name.lowercase(Locale.getDefault()) }
        if (sortedTags.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tags),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(sortedTags) { tag ->
                TagItem(
                    tag = tag,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag) }
                )
            }
        }
    }
}

@Composable
fun GroupCardTab(
    groups: List<CharacterGroupCard>,
    characterCards: List<CharacterCard>,
    activePrompt: ActivePrompt,
    onAddGroup: () -> Unit,
    onEditGroup: (CharacterGroupCard) -> Unit,
    onDeleteGroup: (CharacterGroupCard) -> Unit,
    onDuplicateGroup: (CharacterGroupCard) -> Unit,
    onSetActiveGroup: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.character_groups),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onAddGroup, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.create),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.character_group_empty_state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    val memberNames = group.members.mapNotNull { member ->
                        characterCards.firstOrNull { it.id == member.characterCardId }?.name
                    }
                    GroupCardItem(
                        group = group,
                        isActive = (activePrompt as? ActivePrompt.CharacterGroup)?.id == group.id,
                        memberNames = memberNames,
                        onEdit = { onEditGroup(group) },
                        onDelete = { onDeleteGroup(group) },
                        onDuplicate = { onDuplicateGroup(group) },
                        onSetActive = { onSetActiveGroup(group.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCardItem(
    group: CharacterGroupCard,
    isActive: Boolean,
    memberNames: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onSetActive: () -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val groupAvatarUri by remember(group.id) {
        userPreferencesManager.getAiAvatarForCharacterGroupFlow(group.id)
    }.collectAsState(initial = null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 0.5.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsListAvatar(
                        avatarUri = groupAvatarUri,
                        fallbackIcon = Icons.Default.Group,
                        contentDescription = "Group Avatar"
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isActive) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(R.string.currently_active), fontSize = 10.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        if (!isActive) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.set_active)) },
                                onClick = {
                                    onSetActive()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate)) },
                            onClick = {
                                onDuplicate()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }

            if (group.description.isNotBlank()) {
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Text(
                text = stringResource(R.string.character_group_member_count, group.members.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (memberNames.isNotEmpty()) {
                Text(
                    text = memberNames.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun GroupCardDialog(
    group: CharacterGroupCard,
    allCharacterCards: List<CharacterCard>,
    userPreferencesManager: UserPreferencesManager,
    onDismiss: () -> Unit,
    onSave: (CharacterGroupCard) -> Unit,
    onAvatarChange: () -> Unit,
    onAvatarReset: () -> Unit
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var description by remember(group.id) { mutableStateOf(group.description) }
    var members by remember(group.id) {
        mutableStateOf(group.members.sortedBy { it.orderIndex })
    }

    var addMemberMenuExpanded by remember { mutableStateOf(false) }
    var selectedAddMemberId by remember { mutableStateOf<String?>(null) }

    val selectableCards = remember(allCharacterCards, members) {
        val existing = members.map { it.characterCardId }.toSet()
        allCharacterCards.filter { it.id !in existing }
    }

    if (selectedAddMemberId == null && selectableCards.isNotEmpty()) {
        selectedAddMemberId = selectableCards.first().id
    }

    val avatarUri by userPreferencesManager.getAiAvatarForCharacterGroupFlow(group.id)
        .collectAsState(initial = null)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .imePadding(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 头部区域 - 头像 + 基本信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 紧凑型头像
                    CompactAvatarPicker(
                        avatarUri = avatarUri,
                        onAvatarChange = onAvatarChange,
                        onAvatarReset = onAvatarReset
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 基本信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        CompactTextFieldWithExpand(
                            value = name,
                            onValueChange = { name = it },
                            label = stringResource(R.string.group_name),
                            singleLine = true,
                            onExpandClick = { /* 可选：添加全屏编辑 */ }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        CompactTextFieldWithExpand(
                            value = description,
                            onValueChange = { description = it },
                            label = stringResource(R.string.description_optional),
                            maxLines = 2,
                            onExpandClick = { /* 可选：添加全屏编辑 */ }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 可滚动内容区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 群组成员标题
                    Text(
                        text = stringResource(R.string.group_members),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 添加成员
                    if (selectableCards.isNotEmpty()) {
                        val selectedCardName = selectableCards.firstOrNull { it.id == selectedAddMemberId }?.name ?: ""
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedCardName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.group_add_member), fontSize = 11.sp) },
                                trailingIcon = {
                                    Icon(
                                        if (addMemberMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { addMemberMenuExpanded = !addMemberMenuExpanded }
                            )
                            DropdownMenu(
                                expanded = addMemberMenuExpanded,
                                onDismissRequest = { addMemberMenuExpanded = false }
                            ) {
                                selectableCards.forEach { card ->
                                    DropdownMenuItem(
                                        text = { Text(card.name, fontSize = 12.sp) },
                                        onClick = {
                                            selectedAddMemberId = card.id
                                            addMemberMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = {
                                val selected = selectedAddMemberId ?: return@OutlinedButton
                                members = (members + GroupMemberConfig(
                                    characterCardId = selected,
                                    orderIndex = members.size
                                )).mapIndexed { index, member -> member.copy(orderIndex = index) }
                                val next = selectableCards.firstOrNull { it.id != selected }?.id
                                selectedAddMemberId = next
                            },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 成员列表
                    if (members.isEmpty()) {
                        Text(
                            text = stringResource(R.string.group_member_empty_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            members.forEachIndexed { index, member ->
                                val cardName = allCharacterCards.firstOrNull { it.id == member.characterCardId }?.name
                                    ?: member.characterCardId
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cardName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                members = members.toMutableList().also { it.removeAt(index) }
                                                    .mapIndexed { orderIndex, it -> it.copy(orderIndex = orderIndex) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val normalizedName = name.trim()
                            if (normalizedName.isBlank()) return@Button

                            val normalizedMembers = members.mapIndexed { order, member ->
                                member.copy(orderIndex = order)
                            }

                            onSave(
                                group.copy(
                                    name = normalizedName,
                                    description = description.trim(),
                                    members = normalizedMembers,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(stringResource(R.string.save), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 标签项目
@Composable
fun TagItem(
    tag: PromptTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
                                        ) {
                                            Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
                                                        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                    text = tag.name,
                                            style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                                        )
                if (tag.description.isNotBlank()) {
                                                        Text(
                        text = tag.description,
                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (tag.promptContent.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.content_preview, tag.promptContent.take(50)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                                        )
                                    }
                                }

                                    Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// 标签对话框
@Composable
fun TagDialog(
    tag: PromptTag,
    onDismiss: () -> Unit,
    onSave: (PromptTag) -> Unit
) {
    var name by remember { mutableStateOf(tag.name) }
    var description by remember { mutableStateOf(tag.description) }
    var promptContent by remember { mutableStateOf(tag.promptContent) }

        AlertDialog(
        onDismissRequest = onDismiss,
            title = {
                Text(
                if (tag.id.isEmpty()) stringResource(R.string.create_tag) else stringResource(R.string.edit_tag),
                style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = promptContent,
                    onValueChange = { promptContent = it },
                    label = { Text(stringResource(R.string.prompt_content)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                    minLines = 4,
                    maxLines = 12
                )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                    onSave(
                        tag.copy(
                            name = name,
                            description = description,
                            promptContent = promptContent,
                            tagType = tag.tagType
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
            },
            dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class PromptTagImportSummary(
    val createdCount: Int,
    val updatedCount: Int,
    val skippedCount: Int
)

private fun buildPromptTagExportJson(tags: List<PromptTag>): String {
    val tagsArray = JSONArray()
    tags.forEach { tag ->
        tagsArray.put(
            JSONObject().apply {
                put("name", tag.name)
                put("description", tag.description)
                put("promptContent", tag.promptContent)
                put("tagType", tag.tagType.name)
            }
        )
    }

    return JSONObject().apply {
        put("format", "operit_prompt_tags")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("tags", tagsArray)
    }.toString(2)
}

private suspend fun importPromptTagsFromJsonContent(
    jsonContent: String,
    promptTagManager: PromptTagManager
): PromptTagImportSummary {
    val root = JSONObject(jsonContent)
    val format = root.optString("format")
    if (format.isNotBlank() && format != "operit_prompt_tags") {
        throw IllegalArgumentException("invalid format")
    }

    val tagsArray = root.optJSONArray("tags")
        ?: throw IllegalArgumentException("missing tags array")

    val existingCustomTagsByName = promptTagManager.getAllTags()
        .associateBy(
            keySelector = { it.name.trim().lowercase(Locale.getDefault()) },
            valueTransform = { it.id }
        )
        .toMutableMap()

    var createdCount = 0
    var updatedCount = 0
    var skippedCount = 0

    for (i in 0 until tagsArray.length()) {
        val obj = tagsArray.optJSONObject(i)
        if (obj == null) {
            skippedCount++
            continue
        }

        val name = obj.optString("name").trim()
        if (name.isBlank()) {
            skippedCount++
            continue
        }

        val description = obj.optString("description")
        val promptContent = obj.optString("promptContent")
        val tagType = runCatching {
            TagType.valueOf(obj.optString("tagType", TagType.CUSTOM.name))
        }.getOrDefault(TagType.CUSTOM)
        val nameKey = name.lowercase(Locale.getDefault())
        val existingTagId = existingCustomTagsByName[nameKey]

        if (existingTagId != null) {
            promptTagManager.updatePromptTag(
                id = existingTagId,
                name = name,
                description = description,
                promptContent = promptContent,
                tagType = tagType
            )
            updatedCount++
        } else {
            val newId = promptTagManager.createPromptTag(
                name = name,
                description = description,
                promptContent = promptContent,
                tagType = tagType
            )
            existingCustomTagsByName[nameKey] = newId
            createdCount++
        }
    }

    return PromptTagImportSummary(
        createdCount = createdCount,
        updatedCount = updatedCount,
        skippedCount = skippedCount
    )
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Operit"
                    )
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let { imageUri ->
                    context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        return@withContext true
                    }
                }
                return@withContext false
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(imagesDir, "Operit").apply { if (!exists()) mkdirs() }
                val imageFile = File(targetDir, fileName)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                return@withContext true
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

private suspend fun saveBytesToDownloads(context: Context, bytes: ByteArray, fileName: String, mimeType: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Operit/exports"
                    )
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let { downloadUri ->
                    context.contentResolver.openOutputStream(downloadUri)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                        return@withContext true
                    }
                }
                return@withContext false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "Operit/exports").apply { if (!exists()) mkdirs() }
                val outFile = File(targetDir, fileName)
                FileOutputStream(outFile).use { outputStream ->
                    outputStream.write(bytes)
                    outputStream.flush()
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                return@withContext true
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

private fun insertTavernTextChunk(pngBytes: ByteArray, tavernJson: String): ByteArray {
    if (pngBytes.size < 8) throw IllegalArgumentException("invalid png")

    val keyword = "chara"
    val base64 = Base64.encodeToString(tavernJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val data = (keyword + '\u0000' + base64).toByteArray(Charsets.ISO_8859_1)

    val type = byteArrayOf('t'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte(), 't'.code.toByte())
    val crc = CRC32().apply {
        update(type)
        update(data)
    }.value.toInt()

    val chunk = ByteArrayOutputStream().use { out ->
        val lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        val crcBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array()
        out.write(lenBuf)
        out.write(type)
        out.write(data)
        out.write(crcBuf)
        out.toByteArray()
    }

    var offset = 8
    while (offset + 8 <= pngBytes.size) {
        if (offset + 8 > pngBytes.size) break
        val len = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
        val chunkType = String(pngBytes, offset + 4, 4, Charsets.ISO_8859_1)
        val next = offset + 8 + len + 4
        if (chunkType == "IEND") {
            val out = ByteArrayOutputStream()
            out.write(pngBytes, 0, offset)
            out.write(chunk)
            out.write(pngBytes, offset, pngBytes.size - offset)
            return out.toByteArray()
        }
        offset = next
    }

    throw IllegalArgumentException("invalid png")
}

private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
    val minDim = minOf(src.width, src.height)
    val left = (src.width - minDim) / 2
    val top = (src.height - minDim) / 2
    val cropped = Bitmap.createBitmap(src, left, top, minDim, minDim)
    return if (cropped.width == size && cropped.height == size) {
        cropped
    } else {
        Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}

// 旧配置查看对话框已废弃
