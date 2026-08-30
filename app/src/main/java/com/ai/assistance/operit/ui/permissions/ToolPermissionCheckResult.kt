package com.ai.assistance.operit.ui.permissions

/** Result of the permission gate before a tool starts executing. */
enum class ToolPermissionCheckResult(
    val isGranted: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    GRANTED(isGranted = true),
    DENIED(
        isGranted = false,
        errorCode = "permission_denied",
        errorMessage = "Tool execution permission was denied."
    ),
    OVERLAY_PERMISSION_REQUIRED(
        isGranted = false,
        errorCode = "overlay_permission_required",
        errorMessage = "Display over other apps permission is required to confirm tool execution."
    ),
    CONFIRMATION_TIMEOUT(
        isGranted = false,
        errorCode = "permission_confirmation_timeout",
        errorMessage = "Timed out waiting for tool execution confirmation."
    )
}
