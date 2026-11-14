package com.ai.assistance.operit.core.tools.agent

import android.view.Surface

/**
 * Simple H.264 decoder that renders the Shower video stream onto a Surface.
 *
 * This decoder assumes that the first two binary frames received from the Shower server
 * are the codec configuration buffers (csd-0 and csd-1), followed by regular access units.
 *
 * This app-level object is a thin facade over the shared implementation in the
 * `:showerclient` module.
 */
object ShowerVideoRenderer {

    private val core = com.ai.assistance.showerclient.ShowerVideoRenderer()

    fun attach(surface: Surface, videoWidth: Int, videoHeight: Int) =
        core.attach(surface, videoWidth, videoHeight)

    fun detach() = core.detach()

    fun onFrame(data: ByteArray) = core.onFrame(data)
}
