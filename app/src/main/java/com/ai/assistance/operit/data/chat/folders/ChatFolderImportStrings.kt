package com.ai.assistance.operit.data.chat.folders

import android.content.Context
import com.ai.assistance.operit.R

fun ChatFolderImportIssue.describe(context: Context): String {
    val reason = when (kind) {
        ChatFolderImportErrorKind.EMPTY_ID -> context.getString(R.string.folder_import_empty_id)
        ChatFolderImportErrorKind.DUPLICATE_ID -> context.getString(R.string.folder_import_duplicate_id)
        ChatFolderImportErrorKind.EMPTY_NAME -> context.getString(R.string.folder_import_empty_name)
        ChatFolderImportErrorKind.MISSING_PARENT -> context.getString(R.string.folder_import_missing_parent, requireNotNull(relatedId))
        ChatFolderImportErrorKind.CYCLE -> context.getString(R.string.folder_import_cycle, requireNotNull(relatedId))
    }
    return context.getString(R.string.folder_import_record_error, recordNumber, folderId, reason)
}
