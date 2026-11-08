package com.ai.assistance.showerclient

import android.content.Context
import android.os.DeadObjectException
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.IShowerVideoSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Lightweight controller to talk to the Shower server running locally on the device.
 *
 * Responsibilities:
 * - Maintain a Binder connection to the Shower service
 * - Send commands for a specific virtual display: ensureDisplay, launchApp, tap, swipe, touch, key, screenshot
 * - Track the virtual display id and video size for this session
 */
class ShowerController {

    companion object {
        private const val TAG = "ShowerController"
        private const val CODEC_SIZE_ALIGNMENT = 16

        @Volatile
        private var binderService: IShowerService? = null

        private fun alignToCodecBlockSize(value: Int): Int =
            ((value + CODEC_SIZE_ALIGNMENT - 1) / CODEC_SIZE_ALIGNMENT) * CODEC_SIZE_ALIGNMENT

        private suspend fun getBinder(context: Context? = null): IShowerService? = withContext(Dispatchers.IO) {
            if (binderService?.asBinder()?.isBinderAlive == true) {
                return@withContext binderService
            }

            fun clearDeadService() {
                binderService = null
                ShowerBinderRegistry.setService(null)
            }

            val maxAttempts = if (context != null) 2 else 1
            var attempt = 0
            while (attempt < maxAttempts) {
                attempt++
                try {
                    val cachedService = ShowerBinderRegistry.getService()
                    val binder = cachedService?.asBinder()
                    val alive = binder?.isBinderAlive == true
                    ShowerLog.d(TAG, "getBinder: attempt=$attempt cachedService=$cachedService binder=$binder alive=$alive")
                    if (cachedService != null && alive) {
                        binderService = cachedService
                        ShowerLog.d(TAG, "Connected to Shower Binder service on attempt=$attempt")
                        return@withContext binderService
                    } else {
                        ShowerLog.w(TAG, "No alive Shower Binder cached in ShowerBinderRegistry on attempt=$attempt")
                        clearDeadService()
                    }
                } catch (e: Exception) {
                    ShowerLog.e(TAG, "Failed to connect to Binder service on attempt=$attempt", e)
                    clearDeadService()
                }

                if (context != null && attempt == 1) {
                    try {
                        val ctx = context.applicationContext
                        ShowerLog.d(TAG, "getBinder: attempting to restart Shower server after connection failure")
                        val ok = ShowerServerManager.ensureServerStarted(ctx)
                        if (!ok) {
                            ShowerLog.e(TAG, "getBinder: failed to restart Shower server")
                            break
                        }
                        // Wait a bit for broadcast to propagate
                        delay(200)
                    } catch (e: Exception) {
                        ShowerLog.e(TAG, "getBinder: exception while restarting Shower server", e)
                        break
                    }
                }
            }
            null
        }
    }

    @Volatile
    private var virtualDisplayId: Int? = null

    fun getDisplayId(): Int? = virtualDisplayId

    @Volatile
    private var videoWidth: Int = 0

    @Volatile
    private var videoHeight: Int = 0

    fun getVideoSize(): Pair<Int, Int>? =
        if (videoWidth > 0 && videoHeight > 0) Pair(videoWidth, videoHeight) else null

    private val binaryLock = Any()
    private val earlyBinaryFrames = ArrayDeque<ByteArray>()

    @Volatile
    private var binaryHandler: ((ByteArray) -> Unit)? = null

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {
        val framesToReplay: List<ByteArray>
        synchronized(binaryLock) {
            binaryHandler = handler
            ShowerLog.d(TAG, "setBinaryHandler: id=${virtualDisplayId} handlerSet=${handler != null}, bufferedFrames=${earlyBinaryFrames.size}")
            framesToReplay = if (handler != null && earlyBinaryFrames.isNotEmpty()) {
                val list = earlyBinaryFrames.toList()
                earlyBinaryFrames.clear()
                list
            } else {
                emptyList()
            }
        }
        if (handler != null && framesToReplay.isNotEmpty()) {
            ShowerLog.d(TAG, "setBinaryHandler: replaying ${framesToReplay.size} buffered frames")
            framesToReplay.forEach { frame ->
                try {
                    handler(frame)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val videoSink = object : IShowerVideoSink.Stub() {
        override fun onVideoFrame(data: ByteArray) {
            val handler: ((ByteArray) -> Unit)?
            synchronized(binaryLock) {
                handler = binaryHandler
                if (handler == null) {
                    if (earlyBinaryFrames.size >= 120) {
                        earlyBinaryFrames.removeFirst()
                    }
                    earlyBinaryFrames.addLast(data)
                }
            }
            handler?.invoke(data)
        }
    }

    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? =
        withContext(Dispatchers.IO) {
            val service = getBinder() ?: return@withContext null
            val id = virtualDisplayId ?: return@withContext null
            try {
                withTimeout(timeoutMs) {
                    service.requestScreenshot(id)
                }
            } catch (e: Exception) {
                ShowerLog.e(TAG, "requestScreenshot failed for $id", e)
                null
            }
        }

    /** A live client token, not a periodic input event, owns the service during model planning. */
    suspend fun acquireInputClient(): AutoCloseable {
        var lease: AutoCloseable? = null
        try {
            return withContext(Dispatchers.IO) {
                val service = getBinder() ?: throw IllegalStateException("Shower input service is unavailable")
                val token = Binder()
                val acquired = AutoCloseable { service.unregisterInputClient(token) }
                lease = acquired
                service.registerInputClient(token)
                acquired
            }
        } catch (e: Exception) {
            // withContext can discard a successful registration when cancellation wins its return.
            withContext(NonCancellable + Dispatchers.IO) {
                try { lease?.close() } catch (cleanup: Exception) { e.addSuppressed(cleanup) }
            }
            throw e
        }
    }

    /**
     * Prepares this controller to inject input on the physical main display (displayId=0)
     * without creating a virtual display.
     */
    suspend fun prepareMainDisplay(context: Context): Boolean = withContext(Dispatchers.IO) {
        fun clearCachedBinder() {
            binderService = null
            ShowerBinderRegistry.setService(null)
        }

        fun resetLocalDisplayState() {
            virtualDisplayId = null
            videoWidth = 0
            videoHeight = 0
        }

        fun isBinderDied(e: Throwable): Boolean {
            return e is DeadObjectException || e is RemoteException || e.cause is DeadObjectException
        }

        fun doPrepare(service: IShowerService): Boolean {
            // If this controller was previously bound to a virtual display, release it first.
            val oldId = virtualDisplayId
            if (oldId != null && oldId > 0) {
                try {
                    service.setVideoSink(oldId, null)
                } catch (_: Exception) {
                }
                try {
                    service.destroyDisplay(oldId)
                } catch (_: Exception) {
                }
            }

            virtualDisplayId = 0
            videoWidth = 0
            videoHeight = 0
            // Probe the input path once. keyCode=0 (KEYCODE_UNKNOWN) should be harmless.
            service.injectKey(0, 0)
            ShowerLog.d(TAG, "prepareMainDisplay complete, displayId=0")
            return true
        }

        val service = getBinder(context) ?: return@withContext false
        try {
            doPrepare(service)
        } catch (e: Exception) {
            ShowerLog.e(TAG, "prepareMainDisplay failed", e)
            resetLocalDisplayState()
            if (!isBinderDied(e)) {
                return@withContext false
            }

            clearCachedBinder()
            try {
                ShowerLog.d(TAG, "prepareMainDisplay: binder died, restarting Shower server and retrying")
                val ok = ShowerServerManager.ensureServerStarted(context.applicationContext)
                if (!ok) {
                    ShowerLog.e(TAG, "prepareMainDisplay: failed to restart Shower server")
                    return@withContext false
                }
                delay(200)
                val retryService = getBinder(context) ?: return@withContext false
                doPrepare(retryService)
            } catch (retryError: Exception) {
                ShowerLog.e(TAG, "prepareMainDisplay retry failed", retryError)
                resetLocalDisplayState()
                clearCachedBinder()
                false
            }
        }
    }

    suspend fun ensureDisplay(
        context: Context,
        width: Int,
        height: Int,
        dpi: Int,
        bitrateKbps: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        fun clearCachedBinder() {
            binderService = null
            ShowerBinderRegistry.setService(null)
        }

        fun resetLocalDisplayState() {
            virtualDisplayId = null
            videoWidth = 0
            videoHeight = 0
        }

        fun isBinderDied(e: Throwable): Boolean {
            return e is DeadObjectException || e is RemoteException || e.cause is DeadObjectException
        }

        val targetWidth = alignToCodecBlockSize(width)
        val targetHeight = alignToCodecBlockSize(height)
        val bitrate = bitrateKbps ?: 0

        suspend fun doEnsure(service: IShowerService): Boolean {
            val existingId = virtualDisplayId
            if (existingId != null && videoWidth == targetWidth && videoHeight == targetHeight) {
                service.setVideoSink(existingId, videoSink.asBinder())
                ShowerLog.d(TAG, "ensureDisplay reuse existing displayId=$existingId, size=${videoWidth}x${videoHeight}")
                return true
            }

            if (existingId != null) {
                try {
                    service.destroyDisplay(existingId)
                    ShowerLog.d(TAG, "ensureDisplay: destroyed previous displayId=$existingId before recreate")
                } catch (e: Exception) {
                    ShowerLog.w(TAG, "ensureDisplay: failed to destroy previous displayId=$existingId before recreate", e)
                }
                resetLocalDisplayState()
            }

            // Changed: ensureDisplay now returns the ID and doesn't destroy existing ones.
            val id = service.ensureDisplay(targetWidth, targetHeight, dpi, bitrate)
            if (id < 0) {
                resetLocalDisplayState()
                ShowerLog.e(TAG, "ensureDisplay: server reported invalid displayId=$id")
                return false
            }

            virtualDisplayId = id
            videoWidth = targetWidth
            videoHeight = targetHeight

            // Link local sink to this display on the server
            service.setVideoSink(id, videoSink.asBinder())

            ShowerLog.d(TAG, "ensureDisplay complete, new displayId=$virtualDisplayId, size=${videoWidth}x${videoHeight}")
            return true
        }

        val service = getBinder(context) ?: return@withContext false
        try {
            doEnsure(service)
        } catch (e: Exception) {
            ShowerLog.e(TAG, "ensureDisplay failed", e)
            resetLocalDisplayState()
            if (!isBinderDied(e)) {
                return@withContext false
            }

            clearCachedBinder()
            try {
                ShowerLog.d(TAG, "ensureDisplay: binder died, restarting Shower server and retrying")
                val ok = ShowerServerManager.ensureServerStarted(context.applicationContext)
                if (!ok) {
                    ShowerLog.e(TAG, "ensureDisplay: failed to restart Shower server")
                    return@withContext false
                }
                delay(200)
                val retryService = getBinder(context) ?: return@withContext false
                doEnsure(retryService)
            } catch (retryError: Exception) {
                ShowerLog.e(TAG, "ensureDisplay retry failed", retryError)
                resetLocalDisplayState()
                clearCachedBinder()
                false
            }
        }
    }

    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        if (packageName.isBlank()) return@withContext false
        try {
            service.launchApp(packageName, id)
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "launchApp failed for $packageName on $id", e)
            false
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.tap(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "tap($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.swipe(id, startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMs)
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "swipe failed on $id", e)
            false
        }
    }

    suspend fun touchDown(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchDown(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "touchDown($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun touchMove(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchMove(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "touchMove($x, $y) failed on $id", e)
            false
        }
    }

    suspend fun touchUp(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.touchUp(id, x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "touchUp($x, $y) failed on $id", e)
            false
        }
    }

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
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            service.injectTouchEvent(
                id,
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
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "injectTouchEvent(action=$action, x=$x, y=$y) failed on $id", e)
            false
        }
    }

    fun shutdown() {
        ShowerLog.d(TAG, "shutdown requested for display $virtualDisplayId")
        val service = binderService
        val id = virtualDisplayId
        virtualDisplayId = null
        videoWidth = 0
        videoHeight = 0
        synchronized(binaryLock) {
            binaryHandler = null
            earlyBinaryFrames.clear()
        }
        if (id != null && id > 0 && service?.asBinder()?.isBinderAlive == true) {
            try {
                service.setVideoSink(id, null)
                service.destroyDisplay(id)
            } catch (e: Exception) {
                ShowerLog.e(TAG, "shutdown: destroyDisplay failed for $id", e)
            }
        }
    }

    suspend fun key(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        keyWithMeta(keyCode, 0)
    }

    suspend fun keyWithMeta(keyCode: Int, metaState: Int): Boolean = withContext(Dispatchers.IO) {
        val service = getBinder() ?: return@withContext false
        val id = virtualDisplayId ?: return@withContext false
        try {
            if (metaState == 0) {
                service.injectKey(id, keyCode)
            } else {
                service.injectKeyWithMeta(id, keyCode, metaState)
            }
            true
        } catch (e: Exception) {
            ShowerLog.e(TAG, "keyWithMeta(keyCode=$keyCode, metaState=$metaState) failed on $id", e)
            false
        }
    }
}
