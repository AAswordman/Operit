package com.ai.assistance.operit.api.speech.audio

import com.ai.assistance.operit.util.logging.AppLogger

data class WakePhraseSnapshot(
    val phrase: String,
    val regexEnabled: Boolean,
)

object SpeechPrerollStore {
    private const val TAG = "SpeechPrerollStore"

    private const val SAMPLE_RATE = 16000
    private const val CAPACITY_MS = 2500

    private val lock = Any()

    private val capacitySamples: Int = (SAMPLE_RATE * CAPACITY_MS) / 1000
    private val ring: ShortArray = ShortArray(capacitySamples)

    private var writePos: Int = 0
    private var filled: Int = 0

    private var pending: ShortArray? = null
    private var pendingCapturedAtMs: Long = 0L
    private var pendingArmed: Boolean = false

    private var pendingWakePhrase: String? = null
    private var pendingWakePhraseRegexEnabled: Boolean = false
    private var pendingWakePhraseAtMs: Long = 0L

    fun appendPcm(pcm: ShortArray, length: Int) {
        if (length <= 0) return
        val n = minOf(length, pcm.size)
        synchronized(lock) {
            var idx = 0
            while (idx < n) {
                val toCopy = minOf(n - idx, capacitySamples - writePos)
                java.lang.System.arraycopy(pcm, idx, ring, writePos, toCopy)
                writePos += toCopy
                if (writePos >= capacitySamples) writePos = 0
                filled = minOf(capacitySamples, filled + toCopy)
                idx += toCopy
            }
        }
    }

    fun capturePending(windowMs: Int = 1600) {
        val now = System.currentTimeMillis()
        val requested = ((SAMPLE_RATE * windowMs) / 1000).coerceAtLeast(0)
        val snapshot: ShortArray?
        synchronized(lock) {
            val available = filled
            if (available <= 0 || requested <= 0) {
                snapshot = null
            } else {
                val take = minOf(available, requested)
                val out = ShortArray(take)
                val start = ((writePos - take) % capacitySamples + capacitySamples) % capacitySamples
                val firstLen = minOf(take, capacitySamples - start)
                java.lang.System.arraycopy(ring, start, out, 0, firstLen)
                val remain = take - firstLen
                if (remain > 0) {
                    java.lang.System.arraycopy(ring, 0, out, firstLen, remain)
                }
                snapshot = out
            }

            pending = snapshot
            pendingCapturedAtMs = now
            pendingArmed = false
        }

        if (snapshot != null) {
            AppLogger.d(TAG, "Captured pending preroll: samples=${snapshot.size}, ms=${snapshot.size * 1000 / SAMPLE_RATE}")
        } else {
            AppLogger.d(TAG, "Captured pending preroll: empty")
        }
    }

    fun consumePending(maxAgeMs: Long = 10_000L): ShortArray? {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!pendingArmed) return null
            val p = pending ?: return null
            if (now - pendingCapturedAtMs > maxAgeMs) {
                pending = null
                pendingCapturedAtMs = 0L
                pendingArmed = false
                return null
            }
            pending = null
            pendingCapturedAtMs = 0L
            pendingArmed = false
            return p
        }
    }

    fun armPending() {
        synchronized(lock) {
            pendingArmed = pending != null
        }
    }

    fun setPendingWakePhrase(phrase: String, regexEnabled: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pendingWakePhrase = phrase
            pendingWakePhraseRegexEnabled = regexEnabled
            pendingWakePhraseAtMs = now
        }
    }

    fun consumePendingWakePhrase(maxAgeMs: Long = 10_000L): WakePhraseSnapshot? {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val phrase = pendingWakePhrase ?: return null
            if (now - pendingWakePhraseAtMs > maxAgeMs) {
                pendingWakePhrase = null
                pendingWakePhraseAtMs = 0L
                pendingWakePhraseRegexEnabled = false
                return null
            }
            pendingWakePhrase = null
            pendingWakePhraseAtMs = 0L
            val regexEnabled = pendingWakePhraseRegexEnabled
            pendingWakePhraseRegexEnabled = false
            return WakePhraseSnapshot(phrase = phrase, regexEnabled = regexEnabled)
        }
    }

    fun clearPendingWakePhrase() {
        synchronized(lock) {
            pendingWakePhrase = null
            pendingWakePhraseRegexEnabled = false
            pendingWakePhraseAtMs = 0L
        }
    }

    fun clear() {
        synchronized(lock) {
            writePos = 0
            filled = 0
            pending = null
            pendingCapturedAtMs = 0L
            pendingArmed = false
            pendingWakePhrase = null
            pendingWakePhraseRegexEnabled = false
            pendingWakePhraseAtMs = 0L
        }
    }
}
