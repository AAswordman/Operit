package com.ai.assistance.operit.ui.theme.scene.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ai.assistance.operit.ui.theme.scene.ThemeSceneAssetIdV1
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Normalized path commands resolved from one path asset, in the unit square [0,1]x[0,1]. */
internal data class ThemeScenePathDataV1(
    val commands: List<Command>,
) {
    sealed interface Command {
        data class MoveTo(val x: Float, val y: Float) : Command

        data class LineTo(val x: Float, val y: Float) : Command

        data class QuadTo(
            val controlX: Float,
            val controlY: Float,
            val endX: Float,
            val endY: Float,
        ) : Command

        data class CubicTo(
            val control1X: Float,
            val control1Y: Float,
            val control2X: Float,
            val control2Y: Float,
            val endX: Float,
            val endY: Float,
        ) : Command

        data object Close : Command
    }
}

internal class ThemeScenePathParseException(message: String) : IllegalArgumentException(message)

/**
 * Decodes theme package assets from immutable installed files. Bitmaps are bounds-checked on
 * decode; oversized assets are rejected instead of resampled, so a package can never change
 * its declared footprint after validation.
 */
internal class ThemeSceneAssetRepositoryV1(
    private val assets: Map<String, File>,
) {
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()
    private val fontCache = ConcurrentHashMap<String, FontFamily>()
    private val pathCache = ConcurrentHashMap<String, ThemeScenePathDataV1>()

    fun has(assetId: ThemeSceneAssetIdV1): Boolean = assets.containsKey(assetId.value)

    fun bitmap(assetId: ThemeSceneAssetIdV1): ImageBitmap =
        bitmapCache.getOrPut(assetId.value) { decodeBitmap(assetId) }

    fun fontFamily(assetId: ThemeSceneAssetIdV1): FontFamily =
        fontCache.getOrPut(assetId.value) {
            val file = fileFor(assetId)
            try {
                FontFamily(Font(file))
            } catch (error: Throwable) {
                AppLogger.e(TAG, "Failed to load theme font asset: ${file.absolutePath}", error)
                error("Theme font asset cannot be loaded: ${assetId.value}")
            }
        }

    fun pathData(assetId: ThemeSceneAssetIdV1): ThemeScenePathDataV1 =
        pathCache.getOrPut(assetId.value) {
            ThemeScenePathDataV1(parseThemeScenePathCommands(fileFor(assetId).readText(Charsets.UTF_8)))
        }

    fun composePath(assetId: ThemeSceneAssetIdV1, widthPx: Float, heightPx: Float): Path {
        val data = pathData(assetId)
        val path = Path()
        data.commands.forEach { command ->
            when (command) {
                is ThemeScenePathDataV1.Command.MoveTo ->
                    path.moveTo(command.x * widthPx, command.y * heightPx)

                is ThemeScenePathDataV1.Command.LineTo ->
                    path.lineTo(command.x * widthPx, command.y * heightPx)

                is ThemeScenePathDataV1.Command.QuadTo ->
                    path.quadraticBezierTo(
                        command.controlX * widthPx,
                        command.controlY * heightPx,
                        command.endX * widthPx,
                        command.endY * heightPx,
                    )

                is ThemeScenePathDataV1.Command.CubicTo ->
                    path.cubicTo(
                        command.control1X * widthPx,
                        command.control1Y * heightPx,
                        command.control2X * widthPx,
                        command.control2Y * heightPx,
                        command.endX * widthPx,
                        command.endY * heightPx,
                    )

                ThemeScenePathDataV1.Command.Close -> path.close()
            }
        }
        return path
    }

    private fun decodeBitmap(assetId: ThemeSceneAssetIdV1): ImageBitmap {
        val file = fileFor(assetId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(
            bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth * bounds.outHeight <= MAX_BITMAP_PIXELS,
        ) {
            "Theme bitmap asset exceeds the ${MAX_BITMAP_PIXELS} pixel limit: ${assetId.value} " +
                "(${bounds.outWidth}x${bounds.outHeight})"
        }
        val bitmap =
            BitmapFactory.decodeFile(file.absolutePath)
                ?: error("Theme bitmap asset cannot be decoded: ${assetId.value}")
        return bitmap.asImageBitmap()
    }

    private fun fileFor(assetId: ThemeSceneAssetIdV1): File =
        assets[assetId.value]
            ?: error("Theme asset is not present in the package: ${assetId.value}")

    companion object {
        private const val TAG = "ThemeSceneAssets"

        /** 4096x4096 equivalent; the installer enforces the same ceiling before publishing. */
        const val MAX_BITMAP_PIXELS = 16_777_216
    }
}

/** Parses whitespace/comma separated normalized path commands: M/L/Q/C with float pairs, Z. */
internal fun parseThemeScenePathCommands(text: String): List<ThemeScenePathDataV1.Command> {
    val commands = mutableListOf<ThemeScenePathDataV1.Command>()
    val tokens = PATH_TOKEN_REGEX.findAll(text).map { it.value }.toList()
    var index = 0

    fun nextFloat(command: String): Float =
        if (index < tokens.size) {
            val raw = tokens[index++].toFloatOrNull()
                ?: throw ThemeScenePathParseException(
                    "Path command $command expected a number but found: ${tokens[index - 1]}",
                )
            if (raw < 0f || raw > 1f) {
                throw ThemeScenePathParseException(
                    "Path coordinate outside the unit square: $raw",
                )
            }
            raw
        } else {
            throw ThemeScenePathParseException("Path command $command is missing coordinates.")
        }

    while (index < tokens.size) {
        when (tokens[index++]) {
            "M" -> commands += ThemeScenePathDataV1.Command.MoveTo(nextFloat("M"), nextFloat("M"))
            "L" -> commands += ThemeScenePathDataV1.Command.LineTo(nextFloat("L"), nextFloat("L"))
            "Q" -> commands +=
                ThemeScenePathDataV1.Command.QuadTo(
                    nextFloat("Q"), nextFloat("Q"), nextFloat("Q"), nextFloat("Q"),
                )

            "C" -> commands +=
                ThemeScenePathDataV1.Command.CubicTo(
                    nextFloat("C"), nextFloat("C"), nextFloat("C"),
                    nextFloat("C"), nextFloat("C"), nextFloat("C"),
                )

            "Z" -> commands += ThemeScenePathDataV1.Command.Close

            other ->
                throw ThemeScenePathParseException(
                    "Unsupported path command token: $other (only M, L, Q, C, Z are allowed)",
                )
        }
    }
    if (commands.isEmpty()) {
        throw ThemeScenePathParseException("Path asset declares no commands.")
    }
    return commands
}

private val PATH_TOKEN_REGEX = Regex("[A-Za-z]|[-+]?[0-9]*\\.?[0-9]+")
