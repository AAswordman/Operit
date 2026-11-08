package com.ai.assistance.operit.api.speech.wake

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object PersonalWakeFeatureExtractor {
    private const val EPS = 1e-10f

    data class Config(
        val sampleRate: Int = 16000,
        val frameSize: Int = 400,
        val hopSize: Int = 160,
        val fftSize: Int = 512,
        val melBins: Int = 40,
        val numMfcc: Int = 13,
        val maxFrames: Int = 64,
        val fMin: Float = 20f,
        val fMax: Float = 4000f,
    )

    private data class MelFilterBank(
        val weights: Array<FloatArray>,
    )

    fun extractFeatures(
        pcm: ShortArray,
        size: Int,
        config: Config = Config(),
    ): FloatArray {
        val normalized = normalizePcm(pcm, size)
        val waveform = trimToMax(normalized, maxSamples = config.sampleRate * 2)

        val frames = frameSignal(waveform, config.frameSize, config.hopSize)
        if (frames.isEmpty()) return FloatArray(0)

        val mel = buildMelFilterBank(config)

        val logMels = ArrayList<FloatArray>(frames.size)
        for (frame in frames) {
            val pre = preEmphasis(frame, 0.97f)
            val windowed = applyHann(pre)
            val mag2 = fftMagSquared(windowed, config.fftSize)

            val feats = FloatArray(config.melBins)
            for (i in 0 until config.melBins) {
                var sum = 0f
                val w = mel.weights[i]
                val n = min(w.size, mag2.size)
                for (k in 0 until n) {
                    sum += w[k] * mag2[k]
                }
                feats[i] = kotlin.math.ln(max(EPS, sum))
            }

            logMels.add(feats)
        }

        val limited = limitFrames(logMels, config.maxFrames)
        val mfcc = computeMfcc(limited, config)
        val delta = computeDelta(mfcc)
        val delta2 = computeDelta(delta)

        val featureDim = config.numMfcc * 3
        val featSeq = Array(mfcc.size) { FloatArray(featureDim) }
        for (t in mfcc.indices) {
            val base1 = 0
            val base2 = config.numMfcc
            val base3 = config.numMfcc * 2
            for (i in 0 until config.numMfcc) {
                featSeq[t][base1 + i] = mfcc[t][i]
                featSeq[t][base2 + i] = delta[t][i]
                featSeq[t][base3 + i] = delta2[t][i]
            }
        }

        cmvnInPlace(featSeq)

        val out = FloatArray(featSeq.size * featureDim)
        var idx = 0
        for (t in featSeq.indices) {
            for (i in 0 until featureDim) {
                out[idx++] = featSeq[t][i]
            }
        }
        return out
    }

    private fun limitFrames(frames: List<FloatArray>, maxFrames: Int): List<FloatArray> {
        if (frames.size <= maxFrames) return frames
        val out = ArrayList<FloatArray>(maxFrames)
        val n = frames.size
        val dim = frames[0].size
        for (t in 0 until maxFrames) {
            val start = (t * n) / maxFrames
            val end = ((t + 1) * n) / maxFrames
            val count = max(1, end - start)
            val pooled = FloatArray(dim)
            for (k in start until end) {
                val src = frames[k]
                for (i in 0 until dim) {
                    pooled[i] += src[i]
                }
            }
            val inv = 1f / count.toFloat()
            for (i in 0 until dim) {
                pooled[i] = pooled[i] * inv
            }
            out.add(pooled)
        }
        return out
    }

    private fun computeMfcc(logMels: List<FloatArray>, config: Config): Array<FloatArray> {
        val t = logMels.size
        val out = Array(t) { FloatArray(config.numMfcc) }
        val cosTable = dctCosTable(config.numMfcc, config.melBins)
        for (frame in 0 until t) {
            val x = logMels[frame]
            for (k in 0 until config.numMfcc) {
                var sum = 0f
                val row = cosTable[k]
                for (i in 0 until config.melBins) {
                    sum += x[i] * row[i]
                }
                out[frame][k] = sum
            }
        }
        return out
    }

    private fun dctCosTable(numMfcc: Int, melBins: Int): Array<FloatArray> {
        val table = Array(numMfcc) { FloatArray(melBins) }
        val m = melBins.toFloat()
        for (k in 0 until numMfcc) {
            val kk = k.toFloat()
            for (i in 0 until melBins) {
                val ii = i.toFloat()
                val angle = (Math.PI * kk * (ii + 0.5f) / m).toDouble()
                table[k][i] = kotlin.math.cos(angle).toFloat()
            }
        }
        return table
    }

    private fun computeDelta(x: Array<FloatArray>): Array<FloatArray> {
        val t = x.size
        if (t == 0) return emptyArray()
        val d = x[0].size
        val out = Array(t) { FloatArray(d) }
        for (i in 0 until t) {
            val prev = x[if (i > 0) i - 1 else 0]
            val next = x[if (i + 1 < t) i + 1 else t - 1]
            for (k in 0 until d) {
                out[i][k] = (next[k] - prev[k]) * 0.5f
            }
        }
        return out
    }

    private fun cmvnInPlace(x: Array<FloatArray>) {
        if (x.isEmpty()) return
        val t = x.size
        val d = x[0].size
        for (k in 0 until d) {
            var mean = 0f
            for (i in 0 until t) {
                mean += x[i][k]
            }
            mean /= t
            var varSum = 0f
            for (i in 0 until t) {
                val diff = x[i][k] - mean
                varSum += diff * diff
            }
            val std = sqrt(max(EPS, varSum / t))
            for (i in 0 until t) {
                x[i][k] = (x[i][k] - mean) / std
            }
        }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
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
        val denom = sqrt(max(EPS, na)) * sqrt(max(EPS, nb))
        return dot / denom
    }

    private fun normalizePcm(pcm: ShortArray, size: Int): FloatArray {
        val n = size.coerceIn(0, pcm.size)
        if (n == 0) return FloatArray(0)

        var mean = 0f
        for (i in 0 until n) {
            mean += pcm[i] / 32768.0f
        }
        mean /= n

        val out = FloatArray(n)
        for (i in 0 until n) {
            val v = (pcm[i] / 32768.0f) - mean
            out[i] = v
        }
        return out
    }

    private fun trimToMax(waveform: FloatArray, maxSamples: Int): FloatArray {
        if (waveform.size <= maxSamples) return waveform
        val start = (waveform.size - maxSamples) / 2
        return waveform.copyOfRange(start, start + maxSamples)
    }

    private fun frameSignal(signal: FloatArray, frameSize: Int, hopSize: Int): List<FloatArray> {
        if (signal.size < frameSize || frameSize <= 0 || hopSize <= 0) return emptyList()
        val frames = ArrayList<FloatArray>()
        var pos = 0
        while (pos + frameSize <= signal.size) {
            val f = FloatArray(frameSize)
            for (i in 0 until frameSize) {
                f[i] = signal[pos + i]
            }
            frames.add(f)
            pos += hopSize
        }
        return frames
    }

    private fun preEmphasis(frame: FloatArray, coeff: Float): FloatArray {
        val out = FloatArray(frame.size)
        var prev = 0f
        for (i in frame.indices) {
            val x = frame[i]
            out[i] = x - coeff * prev
            prev = x
        }
        return out
    }

    private fun applyHann(frame: FloatArray): FloatArray {
        val out = FloatArray(frame.size)
        val n = frame.size
        val denom = max(1, n - 1)
        for (i in 0 until n) {
            val w = 0.5f * (1f - cos((2.0 * Math.PI * i / denom).toFloat()))
            out[i] = frame[i] * w
        }
        return out
    }

    private fun fftMagSquared(frame: FloatArray, fftSize: Int): FloatArray {
        val n = fftSize
        val re = FloatArray(n)
        val im = FloatArray(n)
        val m = min(frame.size, n)
        for (i in 0 until m) {
            re[i] = frame[i]
        }

        fftInPlace(re, im)

        val bins = n / 2 + 1
        val mag2 = FloatArray(bins)
        for (i in 0 until bins) {
            mag2[i] = re[i] * re[i] + im[i] * im[i]
        }
        return mag2
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = (-2.0 * Math.PI / len).toFloat()
            val wlenRe = cos(ang)
            val wlenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm

                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    val nextWIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                    wIm = nextWIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun buildMelFilterBank(config: Config): MelFilterBank {
        val bins = config.fftSize / 2 + 1
        val weights = Array(config.melBins) { FloatArray(bins) }

        val fMax = if (config.fMax <= 0f) config.sampleRate / 2f else config.fMax
        val melMin = hzToMel(config.fMin)
        val melMax = hzToMel(fMax)

        val melPoints = FloatArray(config.melBins + 2)
        for (i in melPoints.indices) {
            melPoints[i] = melMin + (melMax - melMin) * i / (config.melBins + 1)
        }

        val hzPoints = FloatArray(melPoints.size) { i -> melToHz(melPoints[i]) }
        val binPoints = IntArray(hzPoints.size) { i ->
            val freq = hzPoints[i]
            val b = ((config.fftSize + 1) * freq / config.sampleRate).toInt()
            b.coerceIn(0, bins - 1)
        }

        for (m in 1..config.melBins) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]

            if (center == left || right == center) continue

            for (k in left until center) {
                weights[m - 1][k] = (k - left).toFloat() / (center - left).toFloat()
            }
            for (k in center until right) {
                weights[m - 1][k] = (right - k).toFloat() / (right - center).toFloat()
            }
        }

        return MelFilterBank(weights = weights)
    }

    private fun hzToMel(hz: Float): Float {
        return (2595.0 * Math.log10(1.0 + hz / 700.0)).toFloat()
    }

    private fun melToHz(mel: Float): Float {
        return (700.0 * (Math.pow(10.0, (mel / 2595.0).toDouble()) - 1.0)).toFloat()
    }

    private fun l2NormalizeInPlace(x: FloatArray) {
        var norm = 0f
        for (v in x) {
            norm += v * v
        }
        norm = sqrt(max(EPS, norm))
        for (i in x.indices) {
            x[i] /= norm
        }
    }
}
