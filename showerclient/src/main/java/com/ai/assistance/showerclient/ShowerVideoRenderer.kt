package com.ai.assistance.showerclient

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * H.264 decoder that renders the Shower video stream onto a Surface.
 * Each instance handles one video stream for a specific virtual display.
 */
class ShowerVideoRenderer {

    companion object {
        private const val TAG = "ShowerVideoRenderer"
    }

    private val lock = Any()

    @Volatile
    private var decoder: MediaCodec? = null

    @Volatile
    private var surface: Surface? = null

    @Volatile
    private var csd0: ByteArray? = null

    @Volatile
    private var csd1: ByteArray? = null

    private val pendingFrames = mutableListOf<ByteArray>()

    @Volatile
    private var width: Int = 0

    @Volatile
    private var height: Int = 0

    @Volatile
    private var warnedNoSurface: Boolean = false

    fun attach(surface: Surface, videoWidth: Int, videoHeight: Int) {
        synchronized(lock) {
            this.surface = surface
            this.width = videoWidth
            this.height = videoHeight
            warnedNoSurface = false
            releaseDecoderLocked()
            pendingFrames.clear()
        }
    }

    fun detach() {
        synchronized(lock) {
            releaseDecoderLocked()
            surface = null
            pendingFrames.clear()
            warnedNoSurface = false
        }
    }

    private fun releaseDecoderLocked() {
        val dec = decoder
        decoder = null
        if (dec != null) {
            try {
                dec.stop()
            } catch (_: Exception) {
            }
            try {
                dec.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Called for each H.264 packet. */
    fun onFrame(data: ByteArray) {
        synchronized(lock) {
            if (surface == null || width <= 0 || height <= 0) {
                if (!warnedNoSurface) {
                    ShowerLog.w(TAG, "onFrame: no surface or invalid size; dropping frames")
                    warnedNoSurface = true
                }
                return
            }

            val packet = maybeAvccToAnnexb(data)

            if (decoder == null) {
                val nalType = findNalUnitType(packet)
                if (nalType == 7) { // SPS
                    if (csd0 == null) {
                        csd0 = packet
                    }
                } else if (nalType == 8) { // PPS
                    if (csd1 == null) {
                        csd1 = packet
                    }
                } else {
                    pendingFrames.add(packet)
                }

                if (csd0 != null && csd1 != null) {
                    initDecoderLocked()
                    val framesToProcess = pendingFrames.toList()
                    pendingFrames.clear()
                    framesToProcess.forEach { frame -> queueFrameToDecoder(frame) }
                }
                return
            }

            queueFrameToDecoder(packet)
        }
    }

    private fun findNalUnitType(packet: ByteArray): Int {
        var offset = -1
        for (i in 0 until packet.size - 3) {
            if (packet[i] == 0.toByte() && packet[i + 1] == 0.toByte()) {
                if (packet[i + 2] == 1.toByte()) {
                    offset = i + 3
                    break
                } else if (i + 3 < packet.size && packet[i + 2] == 0.toByte() && packet[i + 3] == 1.toByte()) {
                    offset = i + 4
                    break
                }
            }
        }
        if (offset != -1 && offset < packet.size) {
            return (packet[offset].toInt() and 0x1F)
        }
        return -1
    }

    private fun queueFrameToDecoder(packet: ByteArray) {
        synchronized(lock) {
            val dec = decoder ?: return
            try {
                val inIndex = dec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inputBuffer: ByteBuffer? = dec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(packet)
                        dec.queueInputBuffer(inIndex, 0, packet.size, System.nanoTime() / 1000, 0)
                    }
                }

                val bufferInfo = BufferInfo()
                var outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                while (outIndex >= 0) {
                    dec.releaseOutputBuffer(outIndex, true)
                    outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                }
            } catch (e: Exception) {
                ShowerLog.e(TAG, "Decoder error on frame", e)
                releaseDecoderLocked()
                pendingFrames.clear()
            }
        }
    }

    private fun maybeAvccToAnnexb(packet: ByteArray): ByteArray {
        if (packet.size >= 4) {
            val b0 = packet[0].toInt() and 0xFF
            val b1 = packet[1].toInt() and 0xFF
            val b2 = packet[2].toInt() and 0xFF
            val b3 = packet[3].toInt() and 0xFF
            if (b0 == 0 && b1 == 0 && ((b2 == 0 && b3 == 1) || b2 == 1)) {
                return packet
            }
        }
        val out = ByteArrayOutputStream()
        var i = 0
        val n = packet.size
        while (i + 4 <= n) {
            val nalLen =
                ((packet[i].toInt() and 0xFF) shl 24) or
                    ((packet[i + 1].toInt() and 0xFF) shl 16) or
                    ((packet[i + 2].toInt() and 0xFF) shl 8) or
                    (packet[i + 3].toInt() and 0xFF)
            i += 4
            if (nalLen <= 0 || i + nalLen > n) return packet
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(packet, i, nalLen)
            i += nalLen
        }
        val result = out.toByteArray()
        return if (result.isNotEmpty()) result else packet
    }

    private fun initDecoderLocked() {
        val s = surface ?: return
        val localCsd0 = csd0 ?: return
        val localCsd1 = csd1 ?: return
        if (width <= 0 || height <= 0) return

        try {
            val csd0Annexb = maybeAvccToAnnexb(localCsd0)
            val csd1Annexb = maybeAvccToAnnexb(localCsd1)

            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0Annexb))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1Annexb))

            val dec = MediaCodec.createDecoderByType("video/avc")
            dec.configure(format, s, null, 0)
            dec.start()
            decoder = dec
            ShowerLog.d(TAG, "MediaCodec decoder initialized for ${width}x${height}")
        } catch (e: Exception) {
            ShowerLog.e(TAG, "Failed to init decoder", e)
            releaseDecoderLocked()
        }
    }
}
