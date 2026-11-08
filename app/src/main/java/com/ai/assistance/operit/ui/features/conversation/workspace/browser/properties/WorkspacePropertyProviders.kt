package com.ai.assistance.operit.ui.features.conversation.workspace.browser.properties

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.ui.features.conversation.workspace.browser.directoryModifiedTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/** 文档提供方不保证支持更新时间；读取真实元数据，不用未知大小冒充零字节。 */
internal class SafWorkspacePropertySource private constructor(
    private val context: Context,
    override val root: String,
) : WorkspacePropertySource {
    override val canModifyTime = false
    private val resolver = context.contentResolver

    companion object {
        suspend fun open(context: Context, target: WorkspacePropertiesTarget): SafWorkspacePropertySource {
            val bookmarkName = checkNotNull(target.environment).substringAfter(':')
            val bookmark = ApiPreferences.getInstance(context).safBookmarksFlow.first()
                .firstOrNull { it.name.equals(bookmarkName, ignoreCase = true) }
            checkNotNull(bookmark) { context.getString(R.string.workspace_properties_source_missing) }
            val tree = Uri.parse(bookmark.uri)
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val source = SafWorkspacePropertySource(context, root.toString())
            var current = root.toString()
            for (segment in target.path.trim('/').split('/').filter(String::isNotEmpty)) {
                currentCoroutineContext().ensureActive()
                current = checkNotNull(source.entries(current).firstOrNull { it.first == segment }) {
                    context.getString(R.string.workspace_properties_entry_missing)
                }.second.id
            }
            return SafWorkspacePropertySource(context, current)
        }
    }

    private val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private suspend fun readRows(uri: Uri, tree: Uri): List<Pair<String, PropertyNode>> {
        val cursor = checkNotNull(resolver.query(uri, columns, null, null, null)) {
            context.getString(R.string.workspace_properties_read_failed)
        }
        return cursor.use {
            val result = mutableListOf<Pair<String, PropertyNode>>()
            val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val timeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val directory = it.getString(typeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) it.getLong(sizeColumn).takeIf { value -> value >= 0 } else null
                val modified = if (timeColumn >= 0 && !it.isNull(timeColumn)) it.getLong(timeColumn).takeIf { value -> value > 0 } else null
                val id = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(idColumn)).toString()
                result += it.getString(nameColumn) to PropertyNode(id, directory, false, size, modified)
            }
            result
        }
    }

    private suspend fun entries(id: String): List<Pair<String, PropertyNode>> {
        val parent = Uri.parse(id)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        return readRows(children, parent)
    }

    override suspend fun stat(id: String): PropertyNode {
        currentCoroutineContext().ensureActive()
        val uri = Uri.parse(id)
        return checkNotNull(readRows(uri, uri).singleOrNull()) {
            context.getString(R.string.workspace_properties_entry_missing)
        }.second
    }

    override suspend fun children(node: PropertyNode): List<PropertyNode> {
        currentCoroutineContext().ensureActive()
        return entries(node.id).map { it.second }
    }

    override suspend fun setModified(node: PropertyNode, millis: Long): Unit =
        throw UnsupportedOperationException(context.getString(R.string.workspace_properties_time_unsupported))
}

/** 沿用已连接的工作区文件提供方，远端时间写入能力不由面板自行假定。 */
internal class RemoteWorkspacePropertySource(
    private val fileSystem: FileSystemProvider,
    override val root: String,
) : WorkspacePropertySource {
    override val canModifyTime = false

    override suspend fun stat(id: String): PropertyNode {
        val parent = id.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
        val name = id.trimEnd('/').substringAfterLast('/')
        val entries = checkNotNull(fileSystem.listDirectory(parent)) { "Cannot read directory: $parent" }
        return toNode(id, checkNotNull(entries.firstOrNull { it.name == name }) { "Entry not found: $id" })
    }

    private fun toNode(id: String, entry: FileSystemProvider.FileInfo): PropertyNode = PropertyNode(
        id, entry.isDirectory, entry.permissions.startsWith("l"), entry.size.takeIf { it >= 0 }, directoryModifiedTime(entry.lastModified),
    )

    override suspend fun children(node: PropertyNode): List<PropertyNode> {
        currentCoroutineContext().ensureActive()
        return checkNotNull(fileSystem.listDirectory(node.id)) { "Cannot read directory: ${node.id}" }
            .filter { it.name != "." && it.name != ".." }
            .map { toNode(node.id.trimEnd('/') + "/" + it.name, it) }
    }

    override suspend fun setModified(node: PropertyNode, millis: Long): Unit =
        throw UnsupportedOperationException("This file provider does not expose modification time updates")
}