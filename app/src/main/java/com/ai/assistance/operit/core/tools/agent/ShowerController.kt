package com.ai.assistance.operit.core.tools.agent

import android.content.Context
import com.ai.assistance.showerclient.ShowerController as ClientShowerController
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight controller to talk to the Shower server running locally on the device.
 * Maintains multiple instances keyed by agentId.
 *
 * Responsibilities:
 * - Manage Binder connections to the Shower service (shared via ClientShowerController's companion)
 * - Support multiple virtual screens for concurrent PhoneAgent tasks
 */
object ShowerController {

    private val instances = ConcurrentHashMap<String, ClientShowerController>()

    /** Gets or creates a controller instance for the given agentId. */
    fun getInstance(agentId: String): ClientShowerController {
        return instances.getOrPut(agentId) { ClientShowerController() }
    }

    /** Checks if a controller instance exists for the given agentId. */
    fun hasInstance(agentId: String): Boolean = instances.containsKey(agentId)

    /** Gets the display ID for a specific agent. */
    fun getDisplayId(agentId: String): Int? = instances[agentId]?.getDisplayId()

    fun getDisplayId(): Int? = getDisplayId("default")

    /** Gets the video size for a specific agent. */
    fun getVideoSize(agentId: String): Pair<Int, Int>? = instances[agentId]?.getVideoSize()

    fun getVideoSize(): Pair<Int, Int>? = getVideoSize("default")

    /** Sets the binary video frame handler for a specific agent. */
    fun setBinaryHandler(agentId: String, handler: ((ByteArray) -> Unit)?) =
        instances[agentId]?.setBinaryHandler(handler)

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) = setBinaryHandler("default", handler)

    suspend fun requestScreenshot(agentId: String, timeoutMs: Long = 3000L): ByteArray? =
        getInstance(agentId).requestScreenshot(timeoutMs)

    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? =
        requestScreenshot("default", timeoutMs)

    /**
     * Prepares Shower input for the physical main display (displayId=0) without creating
     * a virtual display.
     */
    suspend fun prepareMainDisplay(agentId: String, context: Context): Boolean =
        getInstance(agentId).prepareMainDisplay(context)

    suspend fun prepareMainDisplay(context: Context): Boolean =
        prepareMainDisplay("default", context)

    suspend fun acquireInputClient(agentId: String): AutoCloseable =
        getInstance(agentId).acquireInputClient()

    /** Ensures a virtual display is created and ready for the specified agent. */
    suspend fun ensureDisplay(
        agentId: String,
        context: Context,
        width: Int,
        height: Int,
        dpi: Int,
        bitrateKbps: Int? = null,
    ): Boolean = getInstance(agentId).ensureDisplay(context, width, height, dpi, bitrateKbps)

    suspend fun ensureDisplay(
        context: Context,
        width: Int,
        height: Int,
        dpi: Int,
        bitrateKbps: Int? = null,
    ): Boolean = ensureDisplay("default", context, width, height, dpi, bitrateKbps)

    /** Launches an app on the virtual display associated with the specified agent. */
    suspend fun launchApp(agentId: String, packageName: String): Boolean =
        getInstance(agentId).launchApp(packageName)

    suspend fun launchApp(packageName: String): Boolean =
        launchApp("default", packageName)

    /** Performs a tap action on the virtual display associated with the specified agent. */
    suspend fun tap(agentId: String, x: Int, y: Int): Boolean =
        getInstance(agentId).tap(x, y)

    suspend fun tap(x: Int, y: Int): Boolean = tap("default", x, y)

    /** Performs a swipe action on the virtual display associated with the specified agent. */
    suspend fun swipe(
        agentId: String,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean = getInstance(agentId).swipe(startX, startY, endX, endY, durationMs)

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean = swipe("default", startX, startY, endX, endY, durationMs)

    /** Performs a touch down action on the virtual display associated with the specified agent. */
    suspend fun touchDown(agentId: String, x: Int, y: Int): Boolean =
        getInstance(agentId).touchDown(x, y)

    suspend fun touchDown(x: Int, y: Int): Boolean = touchDown("default", x, y)

    /** Performs a touch move action on the virtual display associated with the specified agent. */
    suspend fun touchMove(agentId: String, x: Int, y: Int): Boolean =
        getInstance(agentId).touchMove(x, y)

    suspend fun touchMove(x: Int, y: Int): Boolean = touchMove("default", x, y)

    /** Performs a touch up action on the virtual display associated with the specified agent. */
    suspend fun touchUp(agentId: String, x: Int, y: Int): Boolean =
        getInstance(agentId).touchUp(x, y)

    suspend fun touchUp(x: Int, y: Int): Boolean = touchUp("default", x, y)

    suspend fun injectTouchEvent(
        agentId: String,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
        pressure: Float,
        size: Float,
        metaState: Int,
        xPrecision: Float,
        yPrecision: Float,
        deviceId: Int,
        edgeFlags: Int,
    ): Boolean = getInstance(agentId).injectTouchEvent(
        action,
        x,
        y,
        downTime,
        eventTime,
        pressure,
        size,
        metaState,
        xPrecision,
        yPrecision,
        deviceId,
        edgeFlags
    )

    suspend fun injectTouchEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
        pressure: Float,
        size: Float,
        metaState: Int,
        xPrecision: Float,
        yPrecision: Float,
        deviceId: Int,
        edgeFlags: Int,
    ): Boolean = injectTouchEvent(
        "default",
        action,
        x,
        y,
        downTime,
        eventTime,
        pressure,
        size,
        metaState,
        xPrecision,
        yPrecision,
        deviceId,
        edgeFlags
    )

    /** Shuts down the virtual display and connection for the specified agent. */
    fun shutdown(agentId: String) {
        instances.remove(agentId)?.shutdown()
    }

    fun shutdown() = shutdown("default")

    /** Injects a key event into the virtual display associated with the specified agent. */
    suspend fun key(agentId: String, keyCode: Int): Boolean =
        getInstance(agentId).key(keyCode)

    suspend fun keyWithMeta(agentId: String, keyCode: Int, metaState: Int): Boolean =
        getInstance(agentId).keyWithMeta(keyCode, metaState)

    suspend fun key(keyCode: Int): Boolean = key("default", keyCode)

    suspend fun keyWithMeta(keyCode: Int, metaState: Int): Boolean = keyWithMeta("default", keyCode, metaState)
}
