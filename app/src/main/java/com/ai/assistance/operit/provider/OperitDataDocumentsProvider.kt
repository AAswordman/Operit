package com.ai.assistance.operit.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.data.persistence.StorageProcessLock
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileNotFoundException

class OperitDataDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val TAG = "OperitDataDocumentsProvider"
        private const val ROOT_ID = "operit_data_root"
        private const val DOC_ID_ROOT = "/"

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

    private lateinit var dataRoot: File
    private lateinit var dataRootCanonical: File
    private lateinit var dataRootCanonicalPath: String
    private lateinit var packageName: String

    override fun onCreate(): Boolean {
        return try {
            val providerContext = context ?: return false
            packageName = providerContext.packageName
            dataRoot = File(providerContext.applicationInfo.dataDir)
            dataRootCanonical = dataRoot.canonicalFile
            dataRootCanonicalPath = dataRootCanonical.path
            if (!dataRootCanonical.isDirectory) {
                throw FileNotFoundException("Operit data directory not found: $dataRootCanonicalPath")
            }
            AppLogger.d(TAG, "Initialized root: $dataRootCanonicalPath")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize provider", e)
            false
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
        )
        row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_manage)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "Operit Data")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, dataRootCanonicalPath)
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOC_ID_ROOT)
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, dataRootCanonical.usableSpace)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, getExistingFile(documentId))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getExistingFile(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("Document is not a directory: $parentDocumentId")
        }

        val files = parent.listFiles()
            ?: throw FileNotFoundException("Unable to list directory: ${parent.path}")
        files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .forEach { file -> includeFile(result, file) }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getExistingFile(documentId)
        if (file.isDirectory) {
            throw FileNotFoundException("Document is a directory: $documentId")
        }
        val parsedMode = ParcelFileDescriptor.parseMode(mode)
        if (mode == "r") {
            return ParcelFileDescriptor.open(file, parsedMode)
        }

        val providerContext = requireNotNull(context).applicationContext
        val lease =
            StorageProcessLock.acquireDescriptorLease(
                providerContext,
                "operit-data-provider:$documentId"
            )
        return try {
            ParcelFileDescriptor.open(
                file,
                parsedMode,
                Handler(Looper.getMainLooper()),
                ParcelFileDescriptor.OnCloseListener { error ->
                    try {
                        if (error != null) {
                            AppLogger.e(TAG, "Write descriptor closed with an error", error)
                        }
                    } finally {
                        try {
                            lease.close()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to release write descriptor lease", e)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            try {
                lease.close()
            } catch (closeError: Exception) {
                AppLogger.e(TAG, "Failed to release rejected write descriptor lease", closeError)
            }
            throw e
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String = withWriteAccess("create:$parentDocumentId") {
        val parent = getExistingFile(parentDocumentId)
        if (!parent.isDirectory) {
            throw IllegalArgumentException("Parent is not a directory: $parentDocumentId")
        }

        val target = resolveChildFile(parent, displayName)
        if (target.exists()) {
            throw IllegalStateException("Document already exists: ${target.path}")
        }

        val created = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            target.mkdir()
        } else {
            target.createNewFile()
        }
        if (!created) {
            throw IllegalStateException("Failed to create document: ${target.path}")
        }
        getDocIdForFile(target)
    }

    override fun deleteDocument(documentId: String) {
        withWriteAccess("delete:$documentId") {
            if (documentId == DOC_ID_ROOT) {
                throw IllegalArgumentException("Cannot delete Operit data root")
            }

            val file = getExistingFile(documentId)
            val deleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            if (!deleted) {
                throw IllegalStateException("Failed to delete document: ${file.path}")
            }
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String =
        withWriteAccess("rename:$documentId") {
            if (documentId == DOC_ID_ROOT) {
                throw IllegalArgumentException("Cannot rename Operit data root")
            }

            val source = getExistingFile(documentId)
            val parent = source.parentFile ?: throw IllegalArgumentException("Document has no parent")
            val target = resolveChildFile(parent, displayName)
            if (target.exists()) {
                throw IllegalStateException("Target document already exists: ${target.path}")
            }
            if (!source.renameTo(target)) {
                throw IllegalStateException("Failed to rename document: ${source.path}")
            }
            getDocIdForFile(target)
        }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String = withWriteAccess("move:$sourceDocumentId") {
        if (sourceDocumentId == DOC_ID_ROOT) {
            throw IllegalArgumentException("Cannot move Operit data root")
        }

        val source = getExistingFile(sourceDocumentId)
        val sourceParent = getExistingFile(sourceParentDocumentId)
        val targetParent = getExistingFile(targetParentDocumentId)

        if (!targetParent.isDirectory) {
            throw IllegalArgumentException("Target parent is not a directory: $targetParentDocumentId")
        }
        if (source.parentFile?.canonicalFile != sourceParent.canonicalFile) {
            throw IllegalArgumentException("Source parent does not contain source document")
        }

        val target = ensureInsideDataRoot(File(targetParent, source.name))
        if (target.exists()) {
            throw IllegalStateException("Target document already exists: ${target.path}")
        }
        if (!source.renameTo(target)) {
            throw IllegalStateException("Failed to move document: ${source.path}")
        }
        getDocIdForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = getExistingFile(parentDocumentId)
        val child = getExistingFile(documentId)
        return isSameOrChild(parent, child)
    }

    private fun includeFile(result: MatrixCursor, file: File) {
        val docId = getDocIdForFile(file)
        val flags = getDocumentFlags(file, docId)
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(file))
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, getDisplayName(file, docId))
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
    }

    private fun getDocumentFlags(file: File, docId: String): Int {
        var flags = 0
        if (file.isDirectory && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        }
        if (docId != DOC_ID_ROOT && file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE
            if (!file.isDirectory) {
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            }
        }
        return flags
    }

    private fun getDisplayName(file: File, docId: String): String {
        return if (docId == DOC_ID_ROOT) {
            packageName
        } else {
            file.name
        }
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        }
        val extension = file.extension.lowercase()
        if (extension.isEmpty()) {
            return "application/octet-stream"
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeType != null) {
            return mimeType
        }
        return "application/octet-stream"
    }

    private fun getExistingFile(documentId: String): File {
        val file = getFileForDocId(documentId)
        if (!file.exists()) {
            throw FileNotFoundException("Document not found: $documentId")
        }
        return file
    }

    private fun getFileForDocId(documentId: String): File {
        if (documentId == DOC_ID_ROOT) {
            return dataRootCanonical
        }
        val relativePath = documentId.trimStart('/')
        if (relativePath.isBlank()) {
            throw FileNotFoundException("Invalid documentId: $documentId")
        }
        return ensureInsideDataRoot(File(dataRootCanonical, relativePath))
    }

    private fun getDocIdForFile(file: File): String {
        val canonical = ensureInsideDataRoot(file)
        val path = canonical.path
        if (path == dataRootCanonicalPath) {
            return DOC_ID_ROOT
        }
        return "/" + path.substring(dataRootCanonicalPath.length + 1)
    }

    private fun resolveChildFile(parent: File, displayName: String): File {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            throw IllegalArgumentException("displayName is blank")
        }
        if (cleanName.contains('/')) {
            throw IllegalArgumentException("displayName contains '/': $displayName")
        }
        if (cleanName.contains('\u0000')) {
            throw IllegalArgumentException("displayName contains null character")
        }
        return ensureInsideDataRoot(File(parent, cleanName))
    }

    private fun ensureInsideDataRoot(file: File): File {
        val canonical = file.canonicalFile
        if (!isSameOrChild(dataRootCanonical, canonical)) {
            throw SecurityException("Path escapes Operit data root: ${canonical.path}")
        }
        return canonical
    }

    private fun isSameOrChild(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalFile.path
        val childPath = child.canonicalFile.path
        return childPath == parentPath || childPath.startsWith(parentPath + File.separator)
    }

    private fun <T> withWriteAccess(owner: String, block: () -> T): T =
        StorageProcessLock.withExclusiveAccess(
            requireNotNull(context).applicationContext,
            "operit-data-provider:$owner",
            block
        )
}
