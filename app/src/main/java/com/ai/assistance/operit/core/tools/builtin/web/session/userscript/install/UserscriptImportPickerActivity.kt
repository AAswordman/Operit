package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.install

import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class UserscriptImportPickerActivity : ComponentActivity() {
    private var requestId: String = ""
    private var completed = false

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                finishWithCancel()
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { readUserscript(uri) }
                completed = true
                UserscriptImportCoordinator.complete(requestId, result)
                finish()
                overridePendingTransition(0, 0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.alpha = 0f

        requestId = intent.getStringExtra(UserscriptImportCoordinator.requestIdExtra()).orEmpty()
        if (requestId.isBlank()) {
            finishWithCancel()
            return
        }

        if (savedInstanceState == null) {
            picker.launch(
                arrayOf(
                    "text/*",
                    "application/javascript",
                    "application/x-javascript",
                    "application/octet-stream"
                )
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !completed && requestId.isNotBlank()) {
            UserscriptImportCoordinator.cancel(requestId)
        }
        super.onDestroy()
    }

    private fun finishWithCancel() {
        if (requestId.isNotBlank()) {
            UserscriptImportCoordinator.cancel(requestId)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun readUserscript(uri: Uri): UserscriptImportResult? {
        val text =
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return null
        return UserscriptImportResult(
            rawSource = text,
            displayName = queryDisplayName(uri),
            sourceUri = uri.toString()
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor =
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use {
            return if (it.moveToFirst()) {
                it.getString(0)
            } else {
                null
            }
        }
    }
}
