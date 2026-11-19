package com.ai.assistance.showerclient.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerLog
import com.ai.assistance.showerclient.ShowerVideoRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SurfaceView used inside a virtual display overlay to render the Shower video stream.
 */
open class ShowerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "ShowerSurfaceView"
    }

    private var attachJob: Job? = null
    private val renderer = ShowerVideoRenderer()
    private var controller: ShowerController? = null

    init {
        holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
        holder.addCallback(this)
    }

    /**
     * Bind a specific ShowerController to this view.
     * This allows rendering a specific virtual display stream.
     */
    fun bindController(controller: ShowerController) {
        this.controller = controller
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ShowerLog.d(TAG, "surfaceCreated")
        val ctrl = controller ?: run {
            ShowerLog.w(TAG, "surfaceCreated: no ShowerController bound")
            return
        }

        // Cancel any previous job
        attachJob?.cancel()
        attachJob = CoroutineScope(Dispatchers.Main).launch {
            ShowerLog.d(TAG, "surfaceCreated: waiting for video size from ShowerController")
            var size: Pair<Int, Int>? = null
            // Retry for a short period to wait for the video size to be set by the controller,
            // resolving a potential race condition.
            for (i in 0 until 50) { // Max wait: 5 seconds
                size = ctrl.getVideoSize()
                if (size != null) break
                delay(100)
            }

            if (size != null) {
                val (w, h) = size
                ShowerLog.d(TAG, "Attaching renderer with size: ${w}x${h}")
                try {
                    holder.setFixedSize(w, h)
                } catch (_: Exception) {
                }
                renderer.attach(holder.surface, w, h)
                // Route binary video frames to the renderer only after the surface and size are ready,
                // so that any buffered SPS/PPS frames can be consumed correctly by the decoder.
                ShowerLog.d(TAG, "surfaceCreated: setting ShowerController binary handler")
                ctrl.setBinaryHandler { data ->
                    renderer.onFrame(data)
                }
            } else {
                ShowerLog.e(TAG, "Failed to get video size after multiple retries.")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No-op for now; scaling is handled by SurfaceView layout.
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ShowerLog.d(TAG, "surfaceDestroyed")
        attachJob?.cancel()
        attachJob = null
        val ctrl = controller
        if (ctrl != null) {
            ShowerLog.d(TAG, "surfaceDestroyed: clearing binary handler")
            ctrl.setBinaryHandler(null)
        }
        renderer.detach()
    }
}
