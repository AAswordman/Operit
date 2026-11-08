package com.ai.assistance.operit.api.speech.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ai.assistance.operit.util.logging.AppLogger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.ai.assistance.operit.api.speech.audio.OnnxSileroVad
import com.ai.assistance.operit.api.speech.audio.SpeechPrerollStore

class PersonalWakeListener(
    private val context: Context,
    private val templatesProvider: () -> List<FloatArray>,
    private val onTriggered: (similarity: Float) -> Unit,
) {
    companion object {
        private const val TAG = "PersonalWakeListener"
    }

    data class Config(
        val sampleRate: Int = 16000,
        val frameSize: Int = 512,
        val maxSegmentMs: Long = 1600L,
        val minSegmentMs: Long = 250L,
        val endSilenceMs: Long = 350L,
        val similarityThreshold: Float = 0.865f,
        val dynamicThresholdMargin: Float = 0.02f,
        val minDynamicThresholdFloor: Float = 0.84f,
        val maxBestSecondGap: Float = 0.04f,
        val minIntraSimilarity: Float = 0.80f,
        val dtwBand: Int = 4,
        val requiredTemplateMatches: Int = 1,
        val minDurationRatio: Float = 0.75f,
        val maxDurationRatio: Float = 1.25f,
        val minRms: Float = 0.003f,
        val rmsNoiseMargin: Float = 0.001f,
        val noiseRmsEmaAlpha: Float = 0.05f,
        val vadMode: OnnxSileroVad.Mode = OnnxSileroVad.Mode.NORMAL,
    )

    @Volatile
    private var running = false

    @Volatile
    private var lastDebugAtMs: Long = 0L

    private val debugIntervalMs: Long = 1500L

    @SuppressLint("MissingPermission")
    suspend fun runLoop(config: Config = Config()) {
        if (running) return
        running = true

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            (minBufferSize.coerceAtLeast(config.frameSize) * 2)
        )

        val vad = OnnxSileroVad(
            context = context.applicationContext,
            sampleRate = config.sampleRate,
            frameSize = config.frameSize,
            mode = config.vadMode,
            speechDurationMs = 30,
            silenceDurationMs = 300,
        )

        val buffer = ShortArray(config.frameSize)
        val segment = ArrayList<Short>()

        var seenSpeech = false
        var speechMs = 0L
        var silenceMs = 0L

        var noiseRmsEma = 0f

        try {
            audioRecord.startRecording()
            AppLogger.d(TAG, "Started personal wake loop")

            while (running) {
                if (!currentCoroutineContext().isActive) break
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                SpeechPrerollStore.appendPcm(buffer, read)

                val isSpeechFrame = read == config.frameSize && vad.isSpeech(buffer)
                val chunkMs = (read * 1000L) / config.sampleRate

                if (!isSpeechFrame && !seenSpeech) {
                    val frameRms = computeRms(buffer, read)
                    noiseRmsEma =
                        if (noiseRmsEma <= 0f) {
                            frameRms
                        } else {
                            val a = config.noiseRmsEmaAlpha.coerceIn(0.001f, 0.5f)
                            (1f - a) * noiseRmsEma + a * frameRms
                        }
                }

                if (isSpeechFrame) {
                    seenSpeech = true
                    silenceMs = 0L
                    speechMs += chunkMs
                    for (i in 0 until read) {
                        segment.add(buffer[i])
                    }

                    if (speechMs >= config.maxSegmentMs) {
                        flushSegmentIfNeeded(config, segment, speechMs, noiseRmsEma)
                        resetSegment(segment)
                        seenSpeech = false
                        speechMs = 0L
                        silenceMs = 0L
                    }
                } else {
                    if (seenSpeech) {
                        silenceMs += chunkMs
                        if (silenceMs >= config.endSilenceMs) {
                            flushSegmentIfNeeded(config, segment, speechMs, noiseRmsEma)
                            resetSegment(segment)
                            seenSpeech = false
                            speechMs = 0L
                            silenceMs = 0L
                        }
                    }
                }
            }
        } finally {
            AppLogger.d(TAG, "Stopping personal wake loop")
            try {
                audioRecord.stop()
            } catch (_: Exception) {
            }
            try {
                audioRecord.release()
            } catch (_: Exception) {
            }
            try {
                vad.close()
            } catch (_: Exception) {
            }
            running = false
        }
    }

    fun stop() {
        running = false
    }

    private fun flushSegmentIfNeeded(
        config: Config,
        segment: ArrayList<Short>,
        speechMs: Long,
        noiseRms: Float,
    ) {
        if (segment.isEmpty()) return
        if (speechMs < config.minSegmentMs) return

        val templates = templatesProvider()
        if (templates.isEmpty()) {
            maybeDebug("drop: no templates")
            return
        }

        val pcm = ShortArray(segment.size)
        for (i in pcm.indices) {
            pcm[i] = segment[i]
        }

        val rms = computeRms(pcm)
        val rmsGate = max(config.minRms, noiseRms + config.rmsNoiseMargin)
        if (rms < rmsGate) {
            maybeDebug(
                "drop: rms too low (rms=$rms < gate=$rmsGate min=${config.minRms} noise=$noiseRms margin=${config.rmsNoiseMargin}) speechMs=$speechMs samples=${pcm.size}"
            )
            return
        }

        val feat = PersonalWakeFeatureExtractor.extractFeatures(pcm, pcm.size)
        if (feat.isEmpty()) {
            maybeDebug("drop: empty features")
            return
        }

        val cfg = PersonalWakeFeatureExtractor.Config()
        val featureDim = cfg.numMfcc * 3
        if (featureDim <= 0) {
            maybeDebug("drop: invalid featureDim=$featureDim")
            return
        }
        if (feat.size % featureDim != 0) {
            maybeDebug("drop: invalid feature size=${feat.size} (not divisible by featureDim=$featureDim)")
            return
        }

        val validTemplates =
            templates.filter { t ->
                t.isNotEmpty() && (t.size % featureDim == 0)
            }
        if (validTemplates.isEmpty()) {
            val sizes = templates.map { it.size }.distinct()
            maybeDebug(
                "drop: template size mismatch featureDim=$featureDim templateSizes=$sizes (need re-enroll)"
            )
            return
        }

        val featSeq = reshapeAndNormalizePerFrame(feat, featureDim)
        val tmplSeqs = validTemplates.map { t -> reshapeAndNormalizePerFrame(t, featureDim) }

        val intraSims = ArrayList<Float>(3)
        if (tmplSeqs.size >= 2) {
            for (i in 0 until tmplSeqs.size) {
                for (j in i + 1 until tmplSeqs.size) {
                    intraSims.add(dtwSimilarity(tmplSeqs[i], tmplSeqs[j], config.dtwBand))
                }
            }
        }
        val (intraMean, intraStd) = meanStd(intraSims)
        val intraMin = intraSims.minOrNull() ?: 1f
        if (intraSims.isNotEmpty() && intraMin < config.minIntraSimilarity) {
            maybeDebug(
                "warn: enrollment inconsistent intraMin=$intraMin intraMean=$intraMean intraStd=$intraStd intra=$intraSims"
            )
        }

        val meanLen = tmplSeqs.map { it.size }.average().toFloat().coerceAtLeast(1f)
        val ratio = featSeq.size.toFloat() / meanLen
        if (ratio < config.minDurationRatio || ratio > config.maxDurationRatio) {
            maybeDebug(
                "drop: duration ratio out of range ratio=$ratio featFrames=${featSeq.size} templateMeanFrames=$meanLen"
            )
            return
        }
        val dynThreshold =
            max(
                config.minDynamicThresholdFloor,
                min(
                    config.similarityThreshold,
                    (intraMin - config.dynamicThresholdMargin).coerceIn(0f, 1f)
                )
            )

        var best = -1f
        var secondBest = -1f
        var hits = 0
        val sims = ArrayList<Float>(tmplSeqs.size)
        for (seq in tmplSeqs) {
            val sim = dtwSimilarity(featSeq, seq, config.dtwBand)
            sims.add(sim)
            if (sim > best) {
                secondBest = best
                best = sim
            } else if (sim > secondBest) {
                secondBest = sim
            }
            if (sim >= dynThreshold) hits++
        }

        val requiredHits = min(config.requiredTemplateMatches, tmplSeqs.size)
        val bestSecondGap = if (secondBest >= 0f) best - secondBest else 0f
        val gapOk = tmplSeqs.size < 2 || hits >= 2 || bestSecondGap <= config.maxBestSecondGap

        if (hits >= requiredHits && gapOk) {
            AppLogger.d(
                TAG,
                "Personal wake triggered: best=$best secondBest=$secondBest gap=$bestSecondGap hits=$hits/$requiredHits rms=$rms speechMs=$speechMs threshold=$dynThreshold intraMin=$intraMin intraMean=$intraMean intraStd=$intraStd intra=$intraSims sims=$sims"
            )
            onTriggered(best)
        } else {
            maybeDebug(
                "drop: not enough matches best=$best secondBest=$secondBest gap=$bestSecondGap gapOk=$gapOk hits=$hits/$requiredHits rms=$rms speechMs=$speechMs threshold=$dynThreshold intraMin=$intraMin intraMean=$intraMean intraStd=$intraStd intra=$intraSims sims=$sims"
            )
        }
    }

    private fun meanStd(x: List<Float>): Pair<Float, Float> {
        if (x.isEmpty()) return 1f to 0f
        var mean = 0f
        for (v in x) {
            mean += v
        }
        mean /= x.size
        var varSum = 0f
        for (v in x) {
            val d = v - mean
            varSum += d * d
        }
        val std = kotlin.math.sqrt(max(1e-10f, varSum / x.size))
        return mean to std
    }

    private fun resetSegment(segment: ArrayList<Short>) {
        segment.clear()
    }

    private fun computeRms(pcm: ShortArray): Float {
        return computeRms(pcm, pcm.size)
    }

    private fun computeRms(pcm: ShortArray, len: Int): Float {
        if (pcm.isEmpty() || len <= 0) return 0f
        var sum = 0.0
        val n = min(len, pcm.size)
        for (i in 0 until n) {
            val v = pcm[i].toDouble() / 32768.0
            sum += v * v
        }
        val mean = sum / n.toDouble()
        return kotlin.math.sqrt(mean).toFloat()
    }

    private fun reshapeAndNormalizePerFrame(
        flat: FloatArray,
        featureDim: Int,
    ): Array<FloatArray> {
        val frames = flat.size / featureDim
        val out = Array(frames) { FloatArray(featureDim) }
        var idx = 0
        for (t in 0 until frames) {
            for (i in 0 until featureDim) {
                out[t][i] = flat[idx++]
            }
            l2NormalizeInPlace(out[t])
        }
        return out
    }

    private fun dtwSimilarity(a: Array<FloatArray>, b: Array<FloatArray>, band: Int): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val n = a.size
        val m = b.size
        val bandW = max(band, abs(n - m))
        val dp = Array(n + 1) { FloatArray(m + 1) { Float.POSITIVE_INFINITY } }
        dp[0][0] = 0f

        for (i in 1..n) {
            val jStart = max(1, i - bandW)
            val jEnd = min(m, i + bandW)
            for (j in jStart..jEnd) {
                val cost = cosineDistance(a[i - 1], b[j - 1])
                val bestPrev = min(dp[i - 1][j], min(dp[i][j - 1], dp[i - 1][j - 1]))
                dp[i][j] = cost + bestPrev
            }
        }

        val norm = max(1f, (n + m).toFloat())
        val avgCost = dp[n][m] / norm
        val sim = 1f - (avgCost / 2f)
        return sim.coerceIn(0f, 1f)
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in 0 until n) {
            val va = a[i]
            val vb = b[i]
            dot += va * vb
            na += va * va
            nb += vb * vb
        }
        val denom = kotlin.math.sqrt(max(1e-10f, na)) * kotlin.math.sqrt(max(1e-10f, nb))
        val cos = (dot / denom).coerceIn(-1f, 1f)
        return 1f - cos
    }

    private fun l2NormalizeInPlace(x: FloatArray) {
        var norm = 0f
        for (v in x) {
            norm += v * v
        }
        norm = kotlin.math.sqrt(max(1e-10f, norm))
        for (i in x.indices) {
            x[i] /= norm
        }
    }

    private fun maybeDebug(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastDebugAtMs < debugIntervalMs) return
        lastDebugAtMs = now
        AppLogger.d(TAG, message)
    }
}
