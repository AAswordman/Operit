package com.ai.assistance.operit.util.media

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.PerspectiveTransform
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import com.google.zxing.qrcode.detector.Detector
import com.google.zxing.qrcode.encoder.Encoder
import java.nio.charset.Charset
import java.util.EnumMap
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.roundToInt

object ColorQrCodeUtil {
    data class DecodeResult(
        val payload: ByteArray,
        val colorCount: Int,
        val qrVersion: Int,
        val crc32: Int,
    )

    class ColorQrException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private const val PROTOCOL_VERSION: Byte = 1
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'Q'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
    private const val HEADER_LEN = 14

    fun generate(
        payload: ByteArray,
        colorCount: Int,
        moduleSizePx: Int = 12,
        marginModules: Int = 4,
    ): Bitmap {
        if (moduleSizePx <= 0) throw ColorQrException("moduleSizePx must be > 0")
        if (marginModules < 0) throw ColorQrException("marginModules must be >= 0")

        val bitsPerSymbol = bitsPerSymbol(colorCount)
        val palette = paletteFor(colorCount)

        val header = buildHeader(payload, colorCount)
        val all = ByteArray(header.size + payload.size)
        System.arraycopy(header, 0, all, 0, header.size)
        System.arraycopy(payload, 0, all, header.size, payload.size)

        val version = chooseVersion(all.size, bitsPerSymbol)
        val qr = buildSkeletonQr(version)
        val dim = qr.matrix.width

        val functionPattern = buildFunctionPattern(version)
        val availableSymbols = dim * dim - countSetBits(functionPattern)
        val requiredSymbols = ceil(all.size * 8.0 / bitsPerSymbol).toInt()
        if (requiredSymbols > availableSymbols) {
            throw ColorQrException("Payload too large: require=$requiredSymbols available=$availableSymbols")
        }

        val sizeModules = dim + marginModules * 2
        val sizePx = sizeModules * moduleSizePx
        val pixels = IntArray(sizePx * sizePx) { Color.WHITE }

        val bitReader = BitReader(all)

        for (y in 0 until dim) {
            for (x in 0 until dim) {
                val isFunction = functionPattern.get(x, y)
                val color = if (isFunction) {
                    if (qr.matrix.get(x, y).toInt() == 1) Color.BLACK else Color.WHITE
                } else {
                    val symbol = bitReader.readBits(bitsPerSymbol)
                    palette[symbol]
                }
                fillModule(pixels, sizePx, x + marginModules, y + marginModules, moduleSizePx, color)
            }
        }

        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    fun generate(
        text: String,
        colorCount: Int,
        charset: Charset = Charsets.UTF_8,
        moduleSizePx: Int = 12,
        marginModules: Int = 4,
    ): Bitmap {
        val raw = text.toByteArray(charset)
        val payload = gzipIfBeneficial(raw)
        return generate(payload, colorCount, moduleSizePx, marginModules)
    }

    fun decode(bitmap: Bitmap, expectedColorCount: Int? = null): DecodeResult {
        val detected = detectQr(bitmap)
        val dim = detected.dimension
        val versionNumber = (dim - 17) / 4
        val version = Version.getVersionForNumber(versionNumber)
        val functionPattern = buildFunctionPattern(version)
        val transform = createTransformFromDetectorPoints(detected.points, dim)

        val candidates = expectedColorCount?.let { listOf(it) } ?: listOf(16, 8, 4, 2)

        var lastError: Throwable? = null
        for (colorCount in candidates) {
            try {
                val bitsPerSymbol = bitsPerSymbol(colorCount)
                val paletteCandidates = listOf(paletteFor(colorCount), paletteForLegacy(colorCount))
                val hueStep = 360f / (1 shl bitsPerSymbol)
                val hueOffsets = if (colorCount == 16) {
                    listOf(
                        0f,
                        hueStep / 8f,
                        -hueStep / 8f,
                        hueStep / 4f,
                        -hueStep / 4f,
                        3f * hueStep / 8f,
                        -3f * hueStep / 8f,
                        hueStep / 2f,
                        -hueStep / 2f,
                    )
                } else {
                    listOf(0f)
                }

                for (palette in paletteCandidates) {
                    for (hueOffset in hueOffsets) {
                        try {
                            val headerSymbolsCount = requiredSymbolCount(HEADER_LEN, bitsPerSymbol)
                            val headerSymbols = readSymbols(
                                bitmap = bitmap,
                                transform = transform,
                                functionPattern = functionPattern,
                                palette = palette,
                                hueOffsetDegrees = hueOffset,
                                maxSymbols = headerSymbolsCount,
                            )
                            val headerBytes = decodeBytesFromSymbols(headerSymbols, bitsPerSymbol, HEADER_LEN)
                            if (!headerBytes.copyOfRange(0, 4).contentEquals(MAGIC)) {
                                throw ColorQrException("Magic mismatch")
                            }
                            if (headerBytes[4] != PROTOCOL_VERSION) {
                                throw ColorQrException("Protocol version mismatch")
                            }
                            val declaredColors = headerBytes[5].toInt() and 0xFF
                            if (declaredColors != colorCount) {
                                throw ColorQrException("Color count mismatch")
                            }

                            val payloadLen = readIntBE(headerBytes, 6)
                            val expectedCrc = readIntBE(headerBytes, 10)
                            if (payloadLen < 0) throw ColorQrException("Invalid payload length")

                            val totalBytes = HEADER_LEN + payloadLen
                            val totalSymbolsCount = requiredSymbolCount(totalBytes, bitsPerSymbol)
                            val symbols = readSymbols(
                                bitmap = bitmap,
                                transform = transform,
                                functionPattern = functionPattern,
                                palette = palette,
                                hueOffsetDegrees = hueOffset,
                                maxSymbols = totalSymbolsCount,
                            )
                            val allBytes = decodeBytesFromSymbols(symbols, bitsPerSymbol, totalBytes)
                            val payload = allBytes.copyOfRange(HEADER_LEN, totalBytes)

                            val actualCrc = crc32(payload)
                            if (actualCrc != expectedCrc) {
                                throw ColorQrException("CRC mismatch")
                            }

                            return DecodeResult(
                                payload = payload,
                                colorCount = colorCount,
                                qrVersion = versionNumber,
                                crc32 = expectedCrc,
                            )
                        } catch (t: Throwable) {
                            lastError = t
                        }
                    }
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw ColorQrException("Failed to decode color QR", lastError)
    }

    fun decodeToString(
        bitmap: Bitmap,
        expectedColorCount: Int? = null,
        charset: Charset = Charsets.UTF_8,
    ): String {
        val payload = decode(bitmap, expectedColorCount).payload
        val bytes = if (isGzip(payload)) {
            gunzip(payload)
        } else {
            payload
        }
        return bytes.toString(charset)
    }

    private fun isGzip(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0x1F && (bytes[1].toInt() and 0xFF) == 0x8B
    }

    private fun gzipIfBeneficial(bytes: ByteArray): ByteArray {
        return try {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(bytes) }
            val gz = out.toByteArray()
            if (gz.size < bytes.size) gz else bytes
        } catch (_: Throwable) {
            bytes
        }
    }

    private fun gunzip(bytes: ByteArray): ByteArray {
        val input = ByteArrayInputStream(bytes)
        GZIPInputStream(input).use { gis ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = gis.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    fun cropToQrRegion(bitmap: Bitmap, paddingRatio: Float = 0.25f): Bitmap? {
        return try {
            val detected = detectQr(bitmap)
            val pts = detected.points
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (p in pts) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }

            val w = maxX - minX
            val h = maxY - minY
            val pad = max(w, h) * paddingRatio + 8f

            val left = floor(minX - pad).toInt().coerceIn(0, bitmap.width - 1)
            val top = floor(minY - pad).toInt().coerceIn(0, bitmap.height - 1)
            val right = floor(maxX + pad).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = floor(maxY + pad).toInt().coerceIn(top + 1, bitmap.height)

            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (_: Throwable) {
            null
        }
    }

    private data class DetectedQr(val dimension: Int, val points: Array<com.google.zxing.ResultPoint>)

    private fun detectQr(bitmap: Bitmap): DetectedQr {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val blackMatrix = try {
            binaryBitmap.blackMatrix
        } catch (e: NotFoundException) {
            throw ColorQrException("Unable to binarize image", e)
        }

        val detector = Detector(blackMatrix)
        val detectorResult = try {
            detector.detect()
        } catch (e: NotFoundException) {
            throw ColorQrException("QR detector failed", e)
        }

        return DetectedQr(detectorResult.bits.width, detectorResult.points)
    }

    private fun createTransformFromDetectorPoints(
        points: Array<com.google.zxing.ResultPoint>,
        dimension: Int,
    ): PerspectiveTransform {
        val bottomLeft = points[0]
        val topLeft = points[1]
        val topRight = points[2]
        val alignment = if (points.size >= 4) points[3] else null

        val dimMinusThree = dimension - 3.5f

        val bottomRightX: Float
        val bottomRightY: Float
        val sourceBottomRightX: Float
        val sourceBottomRightY: Float

        if (alignment != null) {
            bottomRightX = alignment.x
            bottomRightY = alignment.y
            sourceBottomRightX = dimMinusThree - 3.0f
            sourceBottomRightY = sourceBottomRightX
        } else {
            bottomRightX = (topRight.x - topLeft.x) + bottomLeft.x
            bottomRightY = (topRight.y - topLeft.y) + bottomLeft.y
            sourceBottomRightX = dimMinusThree
            sourceBottomRightY = dimMinusThree
        }

        return PerspectiveTransform.quadrilateralToQuadrilateral(
            3.5f,
            3.5f,
            dimMinusThree,
            3.5f,
            sourceBottomRightX,
            sourceBottomRightY,
            3.5f,
            dimMinusThree,
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRightX,
            bottomRightY,
            bottomLeft.x,
            bottomLeft.y,
        )
    }

    private fun readSymbols(
        bitmap: Bitmap,
        transform: PerspectiveTransform,
        functionPattern: com.google.zxing.common.BitMatrix,
        palette: IntArray,
        hueOffsetDegrees: Float = 0f,
        maxSymbols: Int? = null,
    ): IntArray {
        val dim = functionPattern.width
        val symbols = ArrayList<Int>(dim * dim)

        val points = FloatArray(2)
        val samplePoints = floatArrayOf(
            0.5f, 0.5f,
            0.35f, 0.35f,
            0.65f, 0.35f,
            0.35f, 0.65f,
            0.65f, 0.65f,
        )
        val counts = IntArray(palette.size)
        for (y in 0 until dim) {
            for (x in 0 until dim) {
                if (functionPattern.get(x, y)) continue
                if (maxSymbols != null && symbols.size >= maxSymbols) break

                java.util.Arrays.fill(counts, 0)
                var centerIndex = 0
                for (i in 0 until 5) {
                    val ox = samplePoints[i * 2]
                    val oy = samplePoints[i * 2 + 1]
                    points[0] = x + ox
                    points[1] = y + oy
                    transform.transformPoints(points)
                    val px = points[0].roundToInt().coerceIn(0, bitmap.width - 1)
                    val py = points[1].roundToInt().coerceIn(0, bitmap.height - 1)
                    val c = sampleColor(bitmap, px, py)
                    val idx = nearestPaletteIndex(c, palette, hueOffsetDegrees)
                    if (i == 0) centerIndex = idx
                    counts[idx]++
                }

                var chosen = centerIndex
                var bestCount = counts[chosen]
                for (i in counts.indices) {
                    val c = counts[i]
                    if (c > bestCount) {
                        bestCount = c
                        chosen = i
                    }
                }

                symbols.add(chosen)
            }
            if (maxSymbols != null && symbols.size >= maxSymbols) break
        }

        return symbols.toIntArray()
    }

    private fun requiredSymbolCount(byteCount: Int, bitsPerSymbol: Int): Int {
        val requiredBits = byteCount * 8
        return (requiredBits + bitsPerSymbol - 1) / bitsPerSymbol
    }

    private fun sampleColor(bitmap: Bitmap, x: Int, y: Int): Int {
        val hsv = FloatArray(3)
        var best = bitmap.getPixel(x, y)
        var bestSat = -1f
        for (dy in -1..1) {
            val py = (y + dy).coerceIn(0, bitmap.height - 1)
            for (dx in -1..1) {
                val px = (x + dx).coerceIn(0, bitmap.width - 1)
                val c = bitmap.getPixel(px, py)
                Color.colorToHSV(c, hsv)
                val sat = hsv[1]
                if (sat > bestSat) {
                    bestSat = sat
                    best = c
                }
            }
        }
        return best
    }

    private fun decodeBytesFromSymbols(symbols: IntArray, bitsPerSymbol: Int, byteCount: Int): ByteArray {
        val requiredBits = byteCount * 8
        val availableBits = symbols.size * bitsPerSymbol
        if (requiredBits > availableBits) {
            throw ColorQrException("Not enough symbols: requiredBits=$requiredBits availableBits=$availableBits")
        }
        val out = ByteArray(byteCount)

        var bitPos = 0
        var symIndex = 0
        var symBitsLeft = 0
        var symValue = 0

        fun readBit(): Int {
            if (symBitsLeft == 0) {
                if (symIndex >= symbols.size) {
                    throw ColorQrException("Not enough symbols")
                }
                symValue = symbols[symIndex++]
                symBitsLeft = bitsPerSymbol
            }
            symBitsLeft--
            return (symValue shr symBitsLeft) and 1
        }

        while (bitPos < requiredBits) {
            val byteIndex = bitPos ushr 3
            val bitInByte = 7 - (bitPos and 7)
            val b = readBit()
            out[byteIndex] = (out[byteIndex].toInt() or (b shl bitInByte)).toByte()
            bitPos++
        }

        return out
    }

    private fun buildHeader(payload: ByteArray, colorCount: Int): ByteArray {
        val header = ByteArray(HEADER_LEN)
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.size)
        header[4] = PROTOCOL_VERSION
        header[5] = colorCount.toByte()
        writeIntBE(header, 6, payload.size)
        writeIntBE(header, 10, crc32(payload))
        return header
    }

    private fun chooseVersion(totalBytes: Int, bitsPerSymbol: Int): Version {
        val requiredBits = totalBytes * 8
        for (v in 1..40) {
            val ver = Version.getVersionForNumber(v)
            val dim = ver.dimensionForVersion
            val functionPattern = buildFunctionPattern(ver)
            val reserved = countSetBits(functionPattern)
            val available = dim * dim - reserved
            if (available * bitsPerSymbol >= requiredBits) return ver
        }
        throw ColorQrException("Payload too large for max QR version")
    }

    private fun buildFunctionPattern(version: Version): com.google.zxing.common.BitMatrix {
        val dimension = version.dimensionForVersion
        val bitMatrix = com.google.zxing.common.BitMatrix(dimension)

        // Finder patterns + separators
        bitMatrix.setRegion(0, 0, 9, 9)
        bitMatrix.setRegion(dimension - 8, 0, 8, 9)
        bitMatrix.setRegion(0, dimension - 8, 9, 8)

        // Alignment patterns
        val centers = version.alignmentPatternCenters
        val max = centers.size
        for (x in 0 until max) {
            val cx = centers[x]
            for (y in 0 until max) {
                val cy = centers[y]
                if ((x == 0 && (y == 0 || y == max - 1)) || (x == max - 1 && y == 0)) {
                    continue
                }
                bitMatrix.setRegion(cx - 2, cy - 2, 5, 5)
            }
        }

        // Timing patterns
        bitMatrix.setRegion(6, 9, 1, dimension - 17)
        bitMatrix.setRegion(9, 6, dimension - 17, 1)

        // Format information
        bitMatrix.setRegion(0, 8, 9, 1)
        bitMatrix.setRegion(8, 0, 1, 9)
        bitMatrix.setRegion(dimension - 8, 8, 8, 1)
        bitMatrix.setRegion(8, dimension - 7, 1, 7)

        // Version information (v7+)
        if (version.versionNumber > 6) {
            bitMatrix.setRegion(dimension - 11, 0, 3, 6)
            bitMatrix.setRegion(0, dimension - 11, 6, 3)
        }

        return bitMatrix
    }

    private fun countSetBits(matrix: com.google.zxing.common.BitMatrix): Int {
        var c = 0
        val w = matrix.width
        val h = matrix.height
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (matrix.get(x, y)) c++
            }
        }
        return c
    }

    private fun buildSkeletonQr(version: Version): com.google.zxing.qrcode.encoder.QRCode {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.QR_VERSION] = version.versionNumber
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        return Encoder.encode("A", ErrorCorrectionLevel.M, hints)
    }

    private fun bitsPerSymbol(colorCount: Int): Int {
        return when (colorCount) {
            2 -> 1
            4 -> 2
            8 -> 3
            16 -> 4
            else -> throw ColorQrException("Unsupported colorCount=$colorCount")
        }
    }

    private fun paletteFor(colorCount: Int): IntArray {
        val bits = bitsPerSymbol(colorCount)
        val n = 1 shl bits
        val palette = IntArray(n)
        val hsv = floatArrayOf(0f, 1.0f, 0.80f)
        for (i in 0 until n) {
            hsv[0] = (360f * i) / n
            palette[i] = Color.HSVToColor(255, hsv)
        }
        return palette
    }

    private fun paletteForLegacy(colorCount: Int): IntArray {
        val bits = bitsPerSymbol(colorCount)
        val n = 1 shl bits
        val palette = IntArray(n)
        val hsv = floatArrayOf(0f, 0.25f, 1.0f)
        for (i in 0 until n) {
            hsv[0] = (360f * i) / n
            palette[i] = Color.HSVToColor(255, hsv)
        }
        return palette
    }

    private fun nearestPaletteIndex(color: Int, palette: IntArray, hueOffsetDegrees: Float = 0f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = ((hsv[0] + hueOffsetDegrees) % 360f + 360f) % 360f
        val sat = hsv[1]
        val value = hsv[2]

        if (palette.size >= 16) {
            val step = 360f / palette.size
            val idx = (((hue + step / 2f) / step).toInt() % palette.size + palette.size) % palette.size
            return idx
        }

        var best = 0
        var bestScore = Float.MAX_VALUE
        val phsv = FloatArray(3)

        for (i in palette.indices) {
            Color.colorToHSV(palette[i], phsv)
            val dhRaw = abs(hue - phsv[0])
            val dh = min(dhRaw, 360f - dhRaw) / 180f
            val ds = sat - phsv[1]
            val dv = value - phsv[2]

            val hueWeight = if (sat < 0.20f) 0.5f else 4.0f
            val score = hueWeight * dh * dh + 0.5f * ds * ds + 0.25f * dv * dv
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    private fun fillModule(pixels: IntArray, imageWidth: Int, moduleX: Int, moduleY: Int, moduleSize: Int, color: Int) {
        val startX = moduleX * moduleSize
        val startY = moduleY * moduleSize
        for (dy in 0 until moduleSize) {
            val row = (startY + dy) * imageWidth
            for (dx in 0 until moduleSize) {
                pixels[row + startX + dx] = color
            }
        }
    }

    private fun crc32(bytes: ByteArray): Int {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }

    private fun writeIntBE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 24).toByte()
        buf[offset + 1] = (value ushr 16).toByte()
        buf[offset + 2] = (value ushr 8).toByte()
        buf[offset + 3] = value.toByte()
    }

    private fun readIntBE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF shl 24) or
            (buf[offset + 1].toInt() and 0xFF shl 16) or
            (buf[offset + 2].toInt() and 0xFF shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }

    private class BitReader(private val data: ByteArray) {
        private var byteIndex = 0
        private var bitInByte = 0

        fun readBits(n: Int): Int {
            var v = 0
            repeat(n) {
                v = (v shl 1) or readBit()
            }
            return v
        }

        private fun readBit(): Int {
            if (byteIndex >= data.size) return 0
            val b = data[byteIndex].toInt() and 0xFF
            val bit = (b shr (7 - bitInByte)) and 1
            bitInByte++
            if (bitInByte == 8) {
                bitInByte = 0
                byteIndex++
            }
            return bit
        }
    }
}
