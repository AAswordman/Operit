package com.ai.assistance.operit.core.tools.builtin.web.session.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object WebSessionPermissionRequestCoordinator {
    private const val EXTRA_REQUEST_ID = "web_session_permission_request_id"
    private const val EXTRA_PERMISSIONS = "web_session_permission_list"
    private const val REQUEST_TIMEOUT_MS = 30_000L

    private data class PendingRequest(
        val permissions: List<String>,
        val deferred: CompletableDeferred<Map<String, Boolean>>
    )

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()

    suspend fun requestPermissions(
        context: Context,
        permissions: Collection<String>
    ): Map<String, Boolean> {
        val requested =
            permissions
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (requested.isEmpty()) {
            return emptyMap()
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pendingRequests[requestId] = PendingRequest(requested, deferred)

        val launchSucceeded =
            withContext(Dispatchers.Main) {
                launchPermissionActivity(context, requestId, requested)
            }
        if (!launchSucceeded) {
            return requested.associateWith { false }.also {
                pendingRequests.remove(requestId)
            }
        }

        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            deferred.await()
        } ?: requested.associateWith { false }.also {
            pendingRequests.remove(requestId)
        }
    }

    private fun launchPermissionActivity(
        context: Context,
        requestId: String,
        permissions: List<String>
    ): Boolean {
        return runCatching {
            val currentActivity = ActivityLifecycleManager.getCurrentActivity()
            if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
                currentActivity.startActivity(
                    Intent(currentActivity, WebSessionPermissionRequestActivity::class.java).apply {
                        putExtra(EXTRA_REQUEST_ID, requestId)
                        putStringArrayListExtra(EXTRA_PERMISSIONS, ArrayList(permissions))
                    }
                )
            } else {
                context.startActivity(
                    Intent(context, WebSessionPermissionRequestActivity::class.java).apply {
                        putExtra(EXTRA_REQUEST_ID, requestId)
                        putStringArrayListExtra(EXTRA_PERMISSIONS, ArrayList(permissions))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            true
        }.getOrDefault(false)
    }

    internal fun completeRequest(
        requestId: String,
        permissions: Map<String, Boolean>
    ) {
        val pending = pendingRequests.remove(requestId) ?: return
        val normalized =
            pending.permissions.associateWith { permission ->
                permissions[permission] == true
            }
        pending.deferred.complete(normalized)
    }

    internal fun cancelRequest(requestId: String) {
        val pending = pendingRequests.remove(requestId) ?: return
        pending.deferred.complete(pending.permissions.associateWith { false })
    }

    internal fun requestIdExtra(): String = EXTRA_REQUEST_ID

    internal fun permissionsExtra(): String = EXTRA_PERMISSIONS
}

class WebSessionPermissionRequestActivity : ComponentActivity() {
    private var requestId: String = ""
    private var resultDelivered = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            resultDelivered = true
            WebSessionPermissionRequestCoordinator.completeRequest(requestId, permissions)
            finish()
            overridePendingTransition(0, 0)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.alpha = 0f

        requestId = intent.getStringExtra(WebSessionPermissionRequestCoordinator.requestIdExtra()).orEmpty()
        val permissions =
            intent.getStringArrayListExtra(WebSessionPermissionRequestCoordinator.permissionsExtra())
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()

        if (requestId.isBlank() || permissions.isEmpty()) {
            finishWithCancel()
            return
        }

        if (savedInstanceState == null) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !resultDelivered && requestId.isNotBlank()) {
            WebSessionPermissionRequestCoordinator.cancelRequest(requestId)
        }
        super.onDestroy()
    }

    private fun finishWithCancel() {
        if (requestId.isNotBlank()) {
            WebSessionPermissionRequestCoordinator.cancelRequest(requestId)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
