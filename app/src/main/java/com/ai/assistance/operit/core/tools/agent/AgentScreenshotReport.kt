package com.ai.assistance.operit.core.tools.agent

/**
 * Screenshot handed to the vision model for one agent step.
 *
 * The two fields are mutually exclusive: either [link] points at the captured target display,
 * or [diagnostic] explains why that display produced no image.
 */
data class AgentScreenshot(
    val link: String?,
    val diagnostic: String? = null
)

/**
 * Describes a virtual-display capture failure to the vision model.
 *
 * The message names the display that was actually attempted and states that the main display was
 * not used, so the model treats the missing image as a capture failure instead of reasoning about
 * whatever happens to be on the physical screen.
 */
internal fun virtualDisplayScreenshotDiagnostic(
    agentId: String,
    displayId: Int?,
    reason: String
): String {
    val target = displayId?.let { "displayId=$it" } ?: "displayId=unbound"
    return "Virtual display capture failed (agentId=$agentId, $target, reason=$reason). " +
        "The main display was NOT captured because it does not show this agent's screen, " +
        "so no image is attached for this step and any earlier screenshot is stale."
}

/** Builds the screen section of the agent prompt for one step. */
internal fun buildScreenInfo(screenshot: AgentScreenshot): String =
    buildString {
        if (screenshot.link != null) {
            appendLine("[SCREENSHOT] Below is the latest screen image:")
            appendLine(screenshot.link)
        } else if (screenshot.diagnostic != null) {
            appendLine("[SCREENSHOT UNAVAILABLE]")
            appendLine(screenshot.diagnostic)
        } else {
            appendLine("No screenshot available for this step.")
        }
    }.trim()
