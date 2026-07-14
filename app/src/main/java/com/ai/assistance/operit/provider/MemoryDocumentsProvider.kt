package com.ai.assistance.operit.provider

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Base64
import android.util.Log
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemorySpace
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MemoryDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val TAG = "MemoryDocumentsProvider"
        // Authority在AndroidManifest.xml中使用${applicationId}.documents.memory声明
        private const val ROOT_ID = "memory_root"

        private const val DOC_ID_ROOT = "root"
        private const val DOC_ID_PROFILE_PREFIX = "profile:"
        private const val DOC_ID_DIR_PREFIX = "dir:"
        private const val DOC_ID_MEM_PREFIX = "mem:"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    private val repositoryCache: MutableMap<String, MemoryRepository> = mutableMapOf()
    private val writeBackExecutor = Executors.newSingleThreadExecutor()

    private fun requireProviderContext(): Context {
        return context ?: throw IllegalStateException("Context is null")
    }

    override fun attachInfo(context: Context?, info: ProviderInfo?) {
        Log.d(
            TAG,
            "attachInfo context=${context?.packageName} authority=${info?.authority} exported=${info?.exported}"
        )
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean {
        return try {
            val context = context ?: return false
            AppLogger.bindContext(context)
            Log.d(TAG, "onCreate package=${context.packageName}")
            AppLogger.d(TAG, "MemoryDocumentsProvider initialized for ${context.packageName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            AppLogger.e(TAG, "Failed to initialize provider", e)
            false
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String? {
        Log.d(
            TAG,
            "moveDocument source=$sourceDocumentId sourceParent=$sourceParentDocumentId targetParent=$targetParentDocumentId"
        )
        AppLogger.d(
            TAG,
            "moveDocument source=$sourceDocumentId sourceParent=$sourceParentDocumentId targetParent=$targetParentDocumentId"
        )

        return try {
            val source = parseDocumentId(sourceDocumentId)
            val targetParent = parseDocumentId(targetParentDocumentId)

            if (targetParent is DocRef.Root) {
                throw IllegalArgumentException("Cannot move into root")
            }
            if (targetParent is DocRef.Memory) {
                throw IllegalArgumentException("Target parent is not a directory: $targetParentDocumentId")
            }

            try {
                when (parseDocumentId(sourceParentDocumentId)) {
                    is DocRef.Memory -> throw IllegalArgumentException("Source parent is not a directory: $sourceParentDocumentId")
                    else -> {
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "moveDocument: failed to parse sourceParentDocumentId=$sourceParentDocumentId", e)
            }

            when (source) {
                is DocRef.Root -> throw IllegalArgumentException("Cannot move root")
                is DocRef.Profile -> throw IllegalArgumentException("Cannot move profile")

                is DocRef.Memory -> {
                    val sourceProfileId = source.profileId
                    if (targetParent.profileId != sourceProfileId) {
                        throw IllegalArgumentException("Cross-profile move is not supported")
                    }

                    val repo = getRepository(sourceProfileId)
                    if (targetParent is DocRef.Directory) {
                        requireDirectoryExists(repo, targetParent.path)
                    }
                    val memory = runBlocking { repo.findMemoryByUuid(source.uuid) }
                        ?: throw FileNotFoundException("Memory not found: ${source.uuid}")

                    val newFolderPath: String? = when (targetParent) {
                        is DocRef.Profile -> null
                        is DocRef.Directory -> targetParent.path
                        is DocRef.Memory -> throw IllegalArgumentException("Target parent is not a directory")
                        is DocRef.Root -> throw IllegalArgumentException("Cannot move into root")
                    }

                    runBlocking {
                        repo.updateMemory(
                            memory = memory,
                            newTitle = memory.title,
                            newContent = memory.content,
                            newContentType = memory.contentType,
                            newSource = memory.source,
                            newCredibility = memory.credibility,
                            newImportance = memory.importance,
                            newFolderPath = newFolderPath,
                            newTags = null
                        )
                    }

                    sourceDocumentId
                }

                is DocRef.Directory -> {
                    val sourceProfileId = source.profileId
                    if (targetParent.profileId != sourceProfileId) {
                        throw IllegalArgumentException("Cross-profile move is not supported")
                    }

                    if (targetParent is DocRef.Directory) {
                        if (targetParent.path == source.path || targetParent.path.startsWith(source.path + "/")) {
                            throw IllegalArgumentException("Cannot move a directory into itself")
                        }
                    }

                    val leafName = source.path.substringAfterLast('/')
                    val newPath = when (targetParent) {
                        is DocRef.Profile -> leafName
                        is DocRef.Directory -> targetParent.path + "/" + leafName
                        is DocRef.Memory -> throw IllegalArgumentException("Target parent is not a directory")
                        is DocRef.Root -> throw IllegalArgumentException("Cannot move into root")
                    }

                    if (newPath == source.path) {
                        return sourceDocumentId
                    }

                    val repo = getRepository(sourceProfileId)
                    requireDirectoryExists(repo, source.path)
                    if (targetParent is DocRef.Directory) {
                        requireDirectoryExists(repo, targetParent.path)
                    }
                    val existingPaths = loadRealFolderPaths(repo)

                    val conflict = existingPaths.any { p ->
                        (p == newPath || p.startsWith(newPath + "/")) &&
                            !(p == source.path || p.startsWith(source.path + "/"))
                    }
                    if (conflict) {
                        throw IllegalStateException("Target folder already exists: $newPath")
                    }

                    val ok = runBlocking { repo.renameFolder(source.path, newPath) }
                    if (!ok) {
                        throw IllegalStateException("Failed to move folder: ${source.path} -> $newPath")
                    }
                    buildDirectoryDocumentId(sourceProfileId, newPath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "moveDocument failed", e)
            AppLogger.e(TAG, "moveDocument failed", e)
            throw e
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots projection=${projection?.joinToString()}")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "application/json\ntext/plain\n*/*")
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
        )
        row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_info_details)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "Operit Memory Library")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Access memory items")
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, 0L)

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryDocument documentId=$documentId projection=${projection?.joinToString()}")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeDocument(result, documentId)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(
            TAG,
            "queryChildDocuments parent=$parentDocumentId projection=${projection?.joinToString()} sortOrder=$sortOrder"
        )
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val ctx = requireProviderContext()
        val prefs = UserPreferencesManager.getInstance(ctx)

        when (val parent = parseDocumentId(parentDocumentId)) {
            is DocRef.Root -> {
                val profileIds = runBlocking { prefs.memorySpaceListFlow.first() }
                profileIds.forEach { profileId ->
                    val profile = runBlocking { prefs.getMemorySpaceFlow(profileId).first() }
                    includeProfile(result, profile)
                }
            }

            is DocRef.Profile -> {
                requireProfileExists(parent.profileId)
                val repo = getRepository(parent.profileId)
                val allMemories = loadAllMemories(repo)
                val realFolderPaths = collectRealFolderPaths(allMemories)

                val normalizedFolders = normalizeFolders(realFolderPaths)
                val topLevel = normalizedFolders
                    .mapNotNull { it.split('/').firstOrNull()?.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                topLevel.forEach { name ->
                    includeDirectory(result, parent.profileId, name, name)
                }

                includeMemoriesForFolder(
                    result = result,
                    profileId = parent.profileId,
                    memories = allMemories,
                    folderPath = null
                )
            }

            is DocRef.Directory -> {
                requireProfileExists(parent.profileId)
                val repo = getRepository(parent.profileId)
                requireDirectoryExists(repo, parent.path)
                val allMemories = loadAllMemories(repo)
                val realFolderPaths = collectRealFolderPaths(allMemories)

                val normalizedFolders = normalizeFolders(realFolderPaths)
                val directSubfolders = normalizedFolders
                    .filter { it.startsWith(parent.path + "/") }
                    .mapNotNull { it.removePrefix(parent.path + "/").split('/').firstOrNull() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                directSubfolders.forEach { childName ->
                    val childPath = parent.path + "/" + childName
                    includeDirectory(result, parent.profileId, childPath, childName)
                }

                includeMemoriesForFolder(
                    result = result,
                    profileId = parent.profileId,
                    memories = allMemories,
                    folderPath = parent.path
                )
            }

            is DocRef.Memory -> return result
        }

        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        Log.d(TAG, "openDocument documentId=$documentId mode=$mode")
        AppLogger.d(TAG, "openDocument documentId=$documentId mode=$mode")
        val ref = parseDocumentId(documentId)
        if (ref !is DocRef.Memory) {
            throw FileNotFoundException("Document is not a file: $documentId")
        }

        val repo = getRepository(ref.profileId)
        val context = context ?: throw IllegalStateException("Context is null")
        val handler = Handler(Looper.getMainLooper())

        val wantsWrite = mode.contains('w') || mode.contains('W')
        return if (!wantsWrite) {
            val jsonString = runBlocking {
                val memory = repo.findMemoryByUuid(ref.uuid)
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                buildMemoryJson(memory)
            }

            val tempFile = File(context.cacheDir, "memory_doc_read_${ref.uuid}_${System.currentTimeMillis()}.json")
            tempFile.writeText(jsonString, StandardCharsets.UTF_8)
            AppLogger.d(TAG, "openDocument(read) tempFile=${tempFile.absolutePath}")

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY, handler) {
                try {
                    tempFile.delete()
                } catch (_: Exception) {
                }
            }
        } else {
            val jsonString = runBlocking {
                val memory = repo.findMemoryByUuid(ref.uuid)
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                buildMemoryJson(memory)
            }

            val tempFile = File(context.cacheDir, "memory_doc_${ref.uuid}_${System.currentTimeMillis()}.json")
            tempFile.writeText(jsonString, StandardCharsets.UTF_8)
            AppLogger.d(TAG, "openDocument(write) tempFile=${tempFile.absolutePath}")

            val accessMode = ParcelFileDescriptor.parseMode(mode)

            ParcelFileDescriptor.open(tempFile, accessMode, handler) {
                val tempPath = tempFile.absolutePath
                AppLogger.d(TAG, "openDocument(write) onClose uuid=${ref.uuid} tempPath=$tempPath")
                writeBackExecutor.execute {
                    try {
                        AppLogger.d(TAG, "writeBack start uuid=${ref.uuid}")
                        val writtenText = File(tempPath).readText(StandardCharsets.UTF_8)
                        applyWrittenContentToMemory(repo, ref.uuid, writtenText)
                        AppLogger.d(TAG, "writeBack done uuid=${ref.uuid} chars=${writtenText.length}")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to apply written content for ${ref.uuid}", e)
                    } finally {
                        try {
                            File(tempPath).delete()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? {
        AppLogger.d(TAG, "createDocument parent=$parentDocumentId mimeType=$mimeType displayName=$displayName")
        val parent = parseDocumentId(parentDocumentId)
        if (parent is DocRef.Memory) {
            throw IllegalArgumentException("Parent is not a directory: $parentDocumentId")
        }
        if (parent is DocRef.Root) {
            throw IllegalArgumentException("Cannot create profiles from provider")
        }

        if (parent is DocRef.Profile) {
            requireProfileExists(parent.profileId)
        }
        val repo = getRepository(parent.profileId)
        when (parent) {
            is DocRef.Directory -> requireDirectoryExists(repo, parent.path)
            else -> {}
        }

        val cleanName = displayName.trim().trim('/').trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("displayName is blank")
        }
        if (cleanName.contains('/')) {
            throw IllegalArgumentException("displayName contains '/': $displayName")
        }
        if (cleanName.contains(':')) {
            throw IllegalArgumentException("displayName contains ':': $displayName")
        }

        return if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            val newFolderPath = when (parent) {
                is DocRef.Profile -> cleanName
                is DocRef.Directory -> parent.path + "/" + cleanName
                is DocRef.Memory -> throw IllegalArgumentException("Parent is not a directory")
                is DocRef.Root -> throw IllegalArgumentException("Cannot create profiles from provider")
            }

            val ok = runBlocking { repo.createFolder(newFolderPath) }
            if (!ok) {
                throw IllegalStateException("Failed to create folder: $newFolderPath")
            }
            buildDirectoryDocumentId(parent.profileId, newFolderPath)
        } else {
            val title = decodeMemoryTitleFromDisplayName(stripJsonExtension(cleanName))

            val folderPathForMemory: String? = when (parent) {
                is DocRef.Profile -> null
                is DocRef.Directory -> parent.path
                is DocRef.Memory -> null
                is DocRef.Root -> throw IllegalArgumentException("Cannot create profiles from provider")
            }

            val memory = Memory(
                title = title,
                content = "",
                contentType = "text/plain",
                source = "documents_provider",
                folderPath = folderPathForMemory
            )

            runBlocking {
                repo.saveMemory(memory)
            }

            buildMemoryDocumentId(parent.profileId, memory.uuid)
        }
    }

    override fun deleteDocument(documentId: String) {
        AppLogger.d(TAG, "deleteDocument documentId=$documentId")
        when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> throw IllegalArgumentException("Cannot delete root")
            is DocRef.Profile -> throw IllegalArgumentException("Cannot delete profile from provider")
            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")
                val ok = runBlocking { repo.deleteMemory(memory.id) }
                if (!ok) {
                    throw IllegalStateException("Failed to delete memory: ${ref.uuid}")
                }
            }
            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                requireDirectoryExists(repo, ref.path)
                val memories = loadMemoriesInFolderTree(repo, ref.path)
                val uuids = memories.map { it.uuid }.toSet()
                val ok = runBlocking { repo.deleteMemoriesByUuids(uuids) }
                if (!ok) {
                    throw IllegalStateException("Failed to delete folder and its contents: ${ref.path}")
                }
            }
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        AppLogger.d(TAG, "renameDocument documentId=$documentId displayName=$displayName")
        val prefs = UserPreferencesManager.getInstance(context ?: throw IllegalStateException("Context is null"))
        val cleanName = displayName.trim().trim('/').trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("displayName is blank")
        }
        if (cleanName.contains('/')) {
            throw IllegalArgumentException("displayName contains '/': $displayName")
        }
        if (cleanName.contains(':')) {
            throw IllegalArgumentException("displayName contains ':': $displayName")
        }

        return when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> throw IllegalArgumentException("Cannot rename root")

            is DocRef.Profile -> {
                requireProfileExists(ref.profileId)
                val profile = runBlocking { prefs.getMemorySpaceFlow(ref.profileId).first() }
                runBlocking {
                    prefs.updateMemorySpace(profile.copy(name = cleanName))
                }
                buildProfileDocumentId(ref.profileId)
            }

            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                requireDirectoryExists(repo, ref.path)
                val parentPrefix = ref.path.substringBeforeLast('/', "")
                val newPath = if (parentPrefix.isBlank()) cleanName else parentPrefix + "/" + cleanName
                val existingPaths = loadRealFolderPaths(repo)
                val conflict = existingPaths.any { existing ->
                    (existing == newPath || existing.startsWith(newPath + "/")) &&
                        !(existing == ref.path || existing.startsWith(ref.path + "/"))
                }
                if (conflict) {
                    throw IllegalStateException("Target folder already exists: $newPath")
                }
                val ok = runBlocking { repo.renameFolder(ref.path, newPath) }
                if (!ok) {
                    throw IllegalStateException("Failed to rename folder: ${ref.path} -> $newPath")
                }
                buildDirectoryDocumentId(ref.profileId, newPath)
            }

            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")

                val newTitle = decodeRequestedTitle(memory, cleanName)

                runBlocking {
                    repo.updateMemory(
                        memory = memory,
                        newTitle = newTitle,
                        newContent = memory.content,
                        newContentType = memory.contentType,
                        newSource = memory.source,
                        newCredibility = memory.credibility,
                        newImportance = memory.importance,
                        newFolderPath = memory.folderPath,
                        newTags = null
                    )
                }
                buildMemoryDocumentId(ref.profileId, ref.uuid)
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        Log.d(TAG, "isChildDocument parent=$parentDocumentId doc=$documentId")
        val parent = try {
            parseDocumentId(parentDocumentId)
        } catch (_: Exception) {
            return false
        }
        val child = try {
            parseDocumentId(documentId)
        } catch (_: Exception) {
            return false
        }

        if (!documentExists(child)) {
            return false
        }

        return when (parent) {
            is DocRef.Root -> {
                when (child) {
                    is DocRef.Profile,
                    is DocRef.Directory,
                    is DocRef.Memory -> true

                    is DocRef.Root -> false
                }
            }

            is DocRef.Profile -> {
                if (!profileExists(parent.profileId)) return false
                when (child) {
                    is DocRef.Directory -> child.profileId == parent.profileId
                    is DocRef.Memory -> child.profileId == parent.profileId
                    else -> false
                }
            }

            is DocRef.Directory -> {
                val repo = getRepository(parent.profileId)
                if (!directoryExists(repo, parent.path)) return false
                when (child) {
                    is DocRef.Directory -> {
                        child.profileId == parent.profileId &&
                            (child.path == parent.path || child.path.startsWith(parent.path + "/"))
                    }

                    is DocRef.Memory -> {
                        if (child.profileId != parent.profileId) return false
                        val memory = runBlocking { repo.findMemoryByUuid(child.uuid) } ?: return false
                        val fp = MemoryRepository.normalizeFolderPath(memory.folderPath)
                        fp == parent.path || (fp != null && fp.startsWith(parent.path + "/"))
                    }

                    else -> false
                }
            }

            is DocRef.Memory -> false
        }
    }

    private fun includeDocument(result: MatrixCursor, documentId: String) {
        when (val ref = parseDocumentId(documentId)) {
            is DocRef.Root -> {
                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Operit Memory")
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Profile -> {
                requireProfileExists(ref.profileId)
                val prefs = UserPreferencesManager.getInstance(context ?: throw IllegalStateException("Context is null"))
                val profile = runBlocking { prefs.getMemorySpaceFlow(ref.profileId).first() }
                val displayName = getProfileDisplayName(profile)

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildProfileDocumentId(ref.profileId))
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Directory -> {
                requireProfileExists(ref.profileId)
                val repo = getRepository(ref.profileId)
                requireDirectoryExists(repo, ref.path)
                var flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildDirectoryDocumentId(ref.profileId, ref.path))
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ref.displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
                row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
                row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }

            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                val memory = runBlocking { repo.findMemoryByUuid(ref.uuid) }
                    ?: throw FileNotFoundException("Memory not found: ${ref.uuid}")

                val displayName = buildMemoryDisplayName(memory.title, memory.uuid)
                val lastModified = memory.updatedAt.time
                val size = buildMemoryJson(memory).toByteArray(StandardCharsets.UTF_8).size.toLong()

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildMemoryDocumentId(ref.profileId, ref.uuid))
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/json")
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, size)
            }
        }
    }

    private fun includeProfile(result: MatrixCursor, profile: MemorySpace) {
        val displayName = getProfileDisplayName(profile)
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildProfileDocumentId(profile.id))
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
        row.add(
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        )
        row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
    }

    private fun includeDirectory(result: MatrixCursor, profileId: String, path: String, displayName: String) {
        var flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE

        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildDirectoryDocumentId(profileId, path))
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0L)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
    }

    private fun includeMemoriesForFolder(
        result: MatrixCursor,
        profileId: String,
        memories: List<Memory>,
        folderPath: String?
    ) {
        val directMemories = filterDirectMemories(memories, folderPath)

        directMemories
            .sortedBy { it.title.lowercase() }
            .forEach { memory ->
                val displayName = buildMemoryDisplayName(memory.title, memory.uuid)
                val lastModified = memory.updatedAt.time
                val size = buildMemoryJson(memory).toByteArray(StandardCharsets.UTF_8).size.toLong()

                val row = result.newRow()
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildMemoryDocumentId(profileId, memory.uuid))
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/json")
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, lastModified)
                row.add(
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                        DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        DocumentsContract.Document.FLAG_SUPPORTS_RENAME or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                )
                row.add(DocumentsContract.Document.COLUMN_SIZE, size)
            }
    }

    private fun getProfileDisplayName(profile: MemorySpace): String {
        return profile.name
            .ifBlank { profile.id }
    }

    private fun applyWrittenContentToMemory(repo: MemoryRepository, uuid: String, writtenText: String) {
        runBlocking {
            val memory = repo.findMemoryByUuid(uuid) ?: return@runBlocking
            try {
                val json = JSONObject(writtenText)
                val newTitle = json.optString("title", memory.title)
                val requestedContent = json.optString("content", memory.content)
                val newContentType = json.optString("contentType", memory.contentType)
                val newSource = json.optString("source", memory.source)
                val newCredibility = json.optDouble("credibility", memory.credibility.toDouble()).toFloat()
                val newImportance = json.optDouble("importance", memory.importance.toDouble()).toFloat()

                repo.updateMemory(
                    memory = memory,
                    newTitle = newTitle,
                    newContent = if (memory.isDocumentNode) memory.content else requestedContent,
                    newContentType = newContentType,
                    newSource = newSource,
                    newCredibility = newCredibility,
                    newImportance = newImportance,
                    // File content writes must not implicitly relocate the document.
                    newFolderPath = memory.folderPath,
                    newTags = null
                )
            } catch (e: Exception) {
                if (memory.isDocumentNode) {
                    AppLogger.w(TAG, "Ignoring non-JSON write for document memory uuid=$uuid", e)
                    return@runBlocking
                }
                repo.updateMemory(
                    memory = memory,
                    newTitle = memory.title,
                    newContent = writtenText,
                    newContentType = "text/plain",
                    newSource = memory.source,
                    newCredibility = memory.credibility,
                    newImportance = memory.importance,
                    newFolderPath = memory.folderPath,
                    newTags = null
                )
            }
        }
    }

    private fun normalizeFolders(folderPaths: Collection<String>): List<String> {
        return folderPaths
            .filter { it.isNotBlank() }
            .flatMap { full ->
                val parts = full.split('/').filter { it.isNotBlank() }
                val prefixes = mutableListOf<String>()
                var current = ""
                parts.forEach { part ->
                    current = if (current.isEmpty()) part else "$current/$part"
                    prefixes.add(current)
                }
                prefixes
            }
            .distinct()
            .sorted()
    }

    private fun buildMemoryDisplayName(title: String, uuid: String): String {
        val displayTitle = buildVisibleMemoryName(title)
        return "$displayTitle.json"
    }

    private fun decodeRequestedTitle(memory: Memory, displayName: String): String {
        val stem = stripJsonExtension(displayName)
        val currentDisplayStem = stripJsonExtension(buildMemoryDisplayName(memory.title, memory.uuid))
        return if (stem == currentDisplayStem) {
            memory.title
        } else {
            decodeMemoryTitleFromDisplayName(stem)
        }
    }

    private fun buildMemoryJson(memory: com.ai.assistance.operit.data.model.Memory): String {
        val obj = JSONObject()
        obj.put("uuid", memory.uuid)
        obj.put("title", memory.title)
        obj.put("content", memory.content)
        obj.put("contentType", memory.contentType)
        obj.put("source", memory.source)
        obj.put("credibility", memory.credibility)
        obj.put("importance", memory.importance)
        obj.put("folderPath", memory.folderPath)
        obj.put("isDocumentNode", memory.isDocumentNode)
        obj.put("documentPath", memory.documentPath)
        obj.put("createdAt", memory.createdAt.time)
        obj.put("updatedAt", memory.updatedAt.time)
        obj.put("lastAccessedAt", memory.lastAccessedAt.time)
        return obj.toString(2)
    }

    private fun parseDocumentId(documentId: String): DocRef {
        return try {
            parseDirectDocumentId(documentId)
        } catch (directError: FileNotFoundException) {
            if (!documentId.contains('/')) {
                throw directError
            }
            parseSyntheticTreeDocumentId(documentId)
        }
    }

    private fun parseDirectDocumentId(documentId: String): DocRef {
        return when {
            documentId == DOC_ID_ROOT -> DocRef.Root
            documentId.startsWith(DOC_ID_PROFILE_PREFIX) -> {
                val profileId = documentId.removePrefix(DOC_ID_PROFILE_PREFIX)
                if (profileId.isBlank() || profileId.contains('/')) {
                    throw FileNotFoundException("Invalid profile documentId: $documentId")
                }
                DocRef.Profile(profileId = profileId)
            }
            documentId.startsWith(DOC_ID_DIR_PREFIX) -> {
                val encoded = documentId.removePrefix(DOC_ID_DIR_PREFIX)
                val parts = encoded.split(":", limit = 2)
                if (parts.size != 2) throw FileNotFoundException("Invalid directory documentId: $documentId")
                val profileId = parts[0]
                val encodedPath = parts[1]
                if (profileId.isBlank() || profileId.contains('/')) {
                    throw FileNotFoundException("Invalid directory documentId: $documentId")
                }
                if (encodedPath.isBlank() || encodedPath.contains('/')) {
                    throw FileNotFoundException("Invalid directory documentId: $documentId")
                }
                val path = decodeFileComponent(encodedPath)
                val normalizedPath = MemoryRepository.normalizeFolderPath(path)
                    ?: throw FileNotFoundException("Invalid directory documentId: $documentId")
                if (normalizedPath != path) {
                    throw FileNotFoundException("Non-normalized directory documentId: $documentId")
                }
                val displayName = path.split('/').lastOrNull().orEmpty().ifBlank { path }
                DocRef.Directory(profileId = profileId, path = path, displayName = displayName)
            }
            documentId.startsWith(DOC_ID_MEM_PREFIX) -> {
                val encoded = documentId.removePrefix(DOC_ID_MEM_PREFIX)
                val parts = encoded.split(":", limit = 2)
                if (parts.size != 2) throw FileNotFoundException("Invalid memory documentId: $documentId")
                val profileId = parts[0]
                val uuid = parts[1]
                if (profileId.isBlank() || profileId.contains('/') || uuid.isBlank() || uuid.contains('/')) {
                    throw FileNotFoundException("Invalid memory documentId: $documentId")
                }
                DocRef.Memory(profileId = profileId, uuid = uuid)
            }
            else -> throw FileNotFoundException("Unknown documentId: $documentId")
        }
    }

    private fun parseSyntheticTreeDocumentId(documentId: String): DocRef {
        val parts = documentId.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) {
            throw FileNotFoundException("Unknown documentId: $documentId")
        }

        var current = parseDirectDocumentId(parts.first())
        parts.drop(1).forEach { displayName ->
            current = resolveSyntheticChild(current, displayName, documentId)
        }
        return current
    }

    private fun resolveSyntheticChild(parent: DocRef, displayName: String, originalDocumentId: String): DocRef {
        return when (parent) {
            is DocRef.Root -> {
                val prefs = UserPreferencesManager.getInstance(requireProviderContext())
                val profileIds = runBlocking { prefs.memorySpaceListFlow.first() }
                val profile = profileIds
                    .asSequence()
                    .map { profileId -> runBlocking { prefs.getMemorySpaceFlow(profileId).first() } }
                    .firstOrNull { getProfileDisplayName(it) == displayName }
                    ?: throw FileNotFoundException("Synthetic child not found: $originalDocumentId")
                DocRef.Profile(profile.id)
            }

            is DocRef.Profile -> {
                requireProfileExists(parent.profileId)
                val repo = getRepository(parent.profileId)
                resolveSyntheticChildInContainer(
                    profileId = parent.profileId,
                    parentFolderPath = null,
                    displayName = displayName,
                    repo = repo,
                    originalDocumentId = originalDocumentId
                )
            }

            is DocRef.Directory -> {
                val repo = getRepository(parent.profileId)
                requireDirectoryExists(repo, parent.path)
                resolveSyntheticChildInContainer(
                    profileId = parent.profileId,
                    parentFolderPath = parent.path,
                    displayName = displayName,
                    repo = repo,
                    originalDocumentId = originalDocumentId
                )
            }

            is DocRef.Memory -> throw FileNotFoundException("Memory has no child: $originalDocumentId")
        }
    }

    private fun resolveSyntheticChildInContainer(
        profileId: String,
        parentFolderPath: String?,
        displayName: String,
        repo: MemoryRepository,
        originalDocumentId: String
    ): DocRef {
        val allMemories = loadAllMemories(repo)
        val childDirectoryPath = findDirectChildDirectoryPath(
            parentFolderPath = parentFolderPath,
            childDisplayName = displayName,
            folderPaths = collectRealFolderPaths(allMemories)
        )
        if (childDirectoryPath != null) {
            return DocRef.Directory(
                profileId = profileId,
                path = childDirectoryPath,
                displayName = childDisplayName(childDirectoryPath)
            )
        }

        val childMemory = filterDirectMemories(allMemories, parentFolderPath)
            .firstOrNull { memory -> buildMemoryDisplayName(memory.title, memory.uuid) == displayName }
            ?: throw FileNotFoundException("Synthetic child not found: $originalDocumentId")

        return DocRef.Memory(profileId = profileId, uuid = childMemory.uuid)
    }

    private fun findDirectChildDirectoryPath(
        parentFolderPath: String?,
        childDisplayName: String,
        folderPaths: Collection<String>
    ): String? {
        val normalizedFolders = normalizeFolders(folderPaths)
        return if (parentFolderPath == null) {
            normalizedFolders
                .mapNotNull { path -> path.split('/').firstOrNull()?.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstOrNull { it == childDisplayName }
        } else {
            normalizedFolders
                .asSequence()
                .filter { path -> path.startsWith(parentFolderPath + "/") }
                .mapNotNull { path ->
                    path.removePrefix(parentFolderPath + "/").split('/').firstOrNull()?.takeIf { it.isNotBlank() }
                }
                .distinct()
                .firstOrNull { it == childDisplayName }
                ?.let { childName -> parentFolderPath + "/" + childName }
        }
    }

    private fun childDisplayName(path: String): String {
        return path.split('/').lastOrNull().orEmpty().ifBlank { path }
    }

    private fun getRepository(profileId: String): MemoryRepository {
        val ctx = requireProviderContext()
        return synchronized(repositoryCache) {
            repositoryCache.getOrPut(profileId) { MemoryRepository(ctx, profileId) }
        }
    }

    private fun documentExists(ref: DocRef): Boolean {
        return when (ref) {
            is DocRef.Root -> true
            is DocRef.Profile -> profileExists(ref.profileId)
            is DocRef.Directory -> {
                val repo = getRepository(ref.profileId)
                directoryExists(repo, ref.path)
            }
            is DocRef.Memory -> {
                val repo = getRepository(ref.profileId)
                runBlocking { repo.findMemoryByUuid(ref.uuid) } != null
            }
        }
    }

    private fun profileExists(profileId: String): Boolean {
        val prefs = UserPreferencesManager.getInstance(requireProviderContext())
        return runBlocking { prefs.memorySpaceListFlow.first() }.contains(profileId)
    }

    private fun requireProfileExists(profileId: String) {
        if (!profileExists(profileId)) {
            throw FileNotFoundException("Profile not found: $profileId")
        }
    }

    private fun loadAllMemories(repo: MemoryRepository): List<Memory> {
        return runBlocking {
            repo.searchMemories(query = "*", folderPath = null)
        }
    }

    private fun loadRealFolderPaths(repo: MemoryRepository): Set<String> {
        return collectRealFolderPaths(loadAllMemories(repo))
    }

    private fun loadDirectoryNodes(repo: MemoryRepository): Set<String> {
        return normalizeFolders(loadRealFolderPaths(repo)).toSet()
    }

    private fun directoryExists(repo: MemoryRepository, path: String): Boolean {
        val normalizedPath = MemoryRepository.normalizeFolderPath(path) ?: return false
        return loadDirectoryNodes(repo).contains(normalizedPath)
    }

    private fun requireDirectoryExists(repo: MemoryRepository, path: String) {
        if (!directoryExists(repo, path)) {
            throw FileNotFoundException("Directory not found: $path")
        }
    }

    private fun loadMemoriesInFolderTree(repo: MemoryRepository, folderPath: String): List<Memory> {
        val normalizedTarget = MemoryRepository.normalizeFolderPath(folderPath) ?: return emptyList()
        return loadAllMemories(repo).filter { memory ->
            val path = MemoryRepository.normalizeFolderPath(memory.folderPath) ?: return@filter false
            path == normalizedTarget || path.startsWith("$normalizedTarget/")
        }
    }

    private fun collectRealFolderPaths(memories: List<Memory>): Set<String> {
        return memories
            .mapNotNull { MemoryRepository.normalizeFolderPath(it.folderPath) }
            .toSortedSet()
    }

    private fun filterDirectMemories(memories: List<Memory>, folderPath: String?): List<Memory> {
        val ctx = requireProviderContext()
        return memories
            .filter { it.title != ".folder_placeholder" && it.title != ctx.getString(R.string.memory_repository_folder_description_title) }
            .filter { memory ->
                val normalizedMemoryFolder = MemoryRepository.normalizeFolderPath(memory.folderPath)
                if (folderPath == null) {
                    normalizedMemoryFolder == null
                } else {
                    normalizedMemoryFolder == folderPath
                }
            }
    }

    private fun buildProfileDocumentId(profileId: String): String {
        return DOC_ID_PROFILE_PREFIX + profileId
    }

    private fun buildDirectoryDocumentId(profileId: String, path: String): String {
        val normalizedPath = MemoryRepository.normalizeFolderPath(path)
            ?: throw IllegalArgumentException("Invalid directory path: $path")
        return DOC_ID_DIR_PREFIX + profileId + ":" + encodeFileComponent(normalizedPath)
    }

    private fun buildMemoryDocumentId(profileId: String, uuid: String): String {
        return DOC_ID_MEM_PREFIX + profileId + ":" + uuid
    }

    private fun stripJsonExtension(name: String): String {
        return if (name.endsWith(".json", ignoreCase = true)) {
            name.dropLast(5)
        } else {
            name
        }
    }

    private fun decodeMemoryTitleFromDisplayName(stem: String): String {
        return stem.trim().ifBlank { "untitled" }
    }

    private fun buildVisibleMemoryName(title: String): String {
        val raw = title.ifBlank { "untitled" }
        return raw
            .replace("/", "／")
            .replace("\\", "＼")
            .trim()
            .ifBlank { "untitled" }
    }

    private fun encodeFileComponent(value: String): String {
        val raw = MemoryRepository.normalizeFolderPath(value)
            ?: throw IllegalArgumentException("Cannot encode blank path")
        return Base64.encodeToString(
            raw.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun decodeFileComponent(value: String): String {
        if (value.isBlank() || value.contains('/')) {
            throw FileNotFoundException("Invalid encoded directory path")
        }
        if (!value.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            throw FileNotFoundException("Invalid encoded directory path")
        }
        return try {
            val paddedValue = when (value.length % 4) {
                0 -> value
                2 -> "$value=="
                3 -> "$value="
                else -> throw FileNotFoundException("Invalid encoded directory path: $value")
            }
            val decodedBytes = Base64.decode(paddedValue, Base64.URL_SAFE or Base64.NO_WRAP)
            String(decodedBytes, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw FileNotFoundException("Invalid encoded directory path: $value")
        }
    }

    private sealed class DocRef {
        abstract val profileId: String

        object Root : DocRef() {
            override val profileId: String
                get() = throw IllegalStateException("Root has no profileId")
        }

        data class Profile(override val profileId: String) : DocRef()

        data class Directory(
            override val profileId: String,
            val path: String,
            val displayName: String
        ) : DocRef()

        data class Memory(
            override val profileId: String,
            val uuid: String
        ) : DocRef()
    }
}
