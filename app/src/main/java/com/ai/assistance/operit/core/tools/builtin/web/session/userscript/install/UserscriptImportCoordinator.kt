package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.install

import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class UserscriptImportResult(
    val rawSource: String,
    val displayName: String?,
    val sourceUri: String?
)

internal object UserscriptImportCoordinator {
    private const val EXTRA_REQUEST_ID = "userscript_import_request_id"
    private const val REQUEST_TIMEOUT_MS = 60_000L

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<UserscriptImportResult?>>()

    suspend fun requestImport(context: Context): UserscriptImportResult? {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<UserscriptImportResult?>()
        pendingRequests[requestId] = deferred

        val launched =
            withContext(Dispatchers.Main) {
                launchPickerActivity(context, requestId)
            }
        if (!launched) {
            pendingRequests.remove(requestId)
            return null
        }

        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            deferred.await()
        }.also {
            pendingRequests.remove(requestId)
        }
    }

    private fun launchPickerActivity(
        context: Context,
        requestId: String
    ): Boolean {
        return runCatching {
            val intent =
                Intent(context, UserscriptImportPickerActivity::class.java).apply {
                    putExtra(EXTRA_REQUEST_ID, requestId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val activity = ActivityLifecycleManager.getCurrentActivity()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.startActivity(intent.apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrDefault(false)
    }

    internal fun complete(
        requestId: String,
        result: UserscriptImportResult?
    ) {
        pendingRequests.remove(requestId)?.complete(result)
    }

    internal fun cancel(requestId: String) {
        pendingRequests.remove(requestId)?.complete(null)
    }

    internal fun requestIdExtra(): String = EXTRA_REQUEST_ID
}
