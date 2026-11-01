package com.ai.assistance.operit.core.tools.builtin.media

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.core.tools.MusicPlaybackResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.logging.AppLogger
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class StandardMusicPlaybackTools(private val context: Context) {

    private val manager: MusicPlaybackManager by lazy {
        MusicPlaybackManager.getInstance(context)
    }

    suspend fun play(tool: AITool): ToolResult {
        val source = tool.parameters.find { it.name == "source" }?.value?.trim()
        if (source.isNullOrBlank()) {
            return error(tool, "Must provide source parameter")
        }

        val sourceType = tool.parameters.find { it.name == "source_type" }?.value?.trim()
        if (sourceType.isNullOrBlank()) {
            return error(tool, "Must provide source_type parameter: path, url, or uri")
        }

        val title = tool.parameters.find { it.name == "title" }?.value?.trim()
        val artist = tool.parameters.find { it.name == "artist" }?.value?.trim()
        val loop = parseOptionalBoolean(tool, "loop")
        if (loop.error != null) {
            return error(tool, loop.error)
        }
        val volume = parseOptionalFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        val startPositionMs = parseOptionalLong(tool, "start_position_ms")
        if (startPositionMs.error != null) {
            return error(tool, startPositionMs.error)
        }

        return try {
            val result =
                manager.play(
                    source = source,
                    sourceType = sourceType,
                    title = title,
                    artist = artist,
                    loop = loop.value,
                    volume = volume.value,
                    startPositionMs = startPositionMs.value
                )
            ToolResult(toolName = tool.name, success = true, result = result)
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music play failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music play failed: ${e.message}"
            )
        }
    }

    suspend fun playQueue(tool: AITool): ToolResult {
        val items = parseQueueItems(tool)
        if (items.error != null) {
            return error(tool, items.error)
        }

        val loop = parseOptionalBoolean(tool, "loop")
        if (loop.error != null) {
            return error(tool, loop.error)
        }
        val volume = parseOptionalFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        val startIndex = parseOptionalInt(tool, "start_index")
        if (startIndex.error != null) {
            return error(tool, startIndex.error)
        }
        val startPositionMs = parseOptionalLong(tool, "start_position_ms")
        if (startPositionMs.error != null) {
            return error(tool, startPositionMs.error)
        }

        return try {
            val result =
                manager.playQueue(
                    items = items.value,
                    loop = loop.value,
                    volume = volume.value,
                    startIndex = startIndex.value,
                    startPositionMs = startPositionMs.value
                )
            ToolResult(toolName = tool.name, success = true, result = result)
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music queue play failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music queue play failed: ${e.message}"
            )
        }
    }

    suspend fun pause(tool: AITool): ToolResult = execute(tool) { manager.pause() }

    suspend fun resume(tool: AITool): ToolResult = execute(tool) { manager.resume() }

    suspend fun stop(tool: AITool): ToolResult = execute(tool) { manager.stop() }

    suspend fun status(tool: AITool): ToolResult = execute(tool) { manager.status() }

    suspend fun seek(tool: AITool): ToolResult {
        val position = parseRequiredLong(tool, "position_ms")
        if (position.error != null) {
            return error(tool, position.error)
        }
        return execute(tool) { manager.seek(position.value) }
    }

    suspend fun setVolume(tool: AITool): ToolResult {
        val volume = parseRequiredFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        return execute(tool) { manager.setVolume(volume.value) }
    }

    private suspend fun execute(
        tool: AITool,
        action: suspend () -> MusicPlaybackResultData
    ): ToolResult {
        return try {
            ToolResult(toolName = tool.name, success = true, result = action())
        } catch (e: IllegalStateException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music operation failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music operation failed: ${e.message}"
            )
        }
    }

    private fun error(tool: AITool, message: String): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message
        )
    }

    private fun parseOptionalBoolean(tool: AITool, name: String): ParsedBoolean {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedBoolean(null, null)
        val value = raw.toBooleanStrictOrNull()
        return if (value == null) {
            ParsedBoolean(null, "$name must be true or false")
        } else {
            ParsedBoolean(value, null)
        }
    }

    private fun parseOptionalFloat(tool: AITool, name: String): ParsedFloat {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedFloat(null, null)
        val value = raw.toFloatOrNull()
        return if (value == null) {
            ParsedFloat(null, "$name must be a number")
        } else {
            ParsedFloat(value, null)
        }
    }

    private fun parseRequiredFloat(tool: AITool, name: String): ParsedRequiredFloat {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw.isNullOrBlank()) return ParsedRequiredFloat(0f, "Must provide $name parameter")
        val value = raw.toFloatOrNull()
        return if (value == null) {
            ParsedRequiredFloat(0f, "$name must be a number")
        } else {
            ParsedRequiredFloat(value, null)
        }
    }

    private fun parseOptionalLong(tool: AITool, name: String): ParsedLong {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedLong(null, null)
        val value = raw.toLongOrNull()
        return if (value == null) {
            ParsedLong(null, "$name must be an integer")
        } else {
            ParsedLong(value, null)
        }
    }

    private fun parseOptionalInt(tool: AITool, name: String): ParsedInt {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedInt(null, null)
        val value = raw.toIntOrNull()
        return if (value == null) {
            ParsedInt(null, "$name must be an integer")
        } else {
            ParsedInt(value, null)
        }
    }

    private fun parseQueueItems(tool: AITool): ParsedQueueItems {
        val raw = tool.parameters.find { it.name == "items" }?.value?.trim()
        if (raw.isNullOrBlank()) {
            return ParsedQueueItems(emptyList(), "Must provide items parameter")
        }

        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) {
                return ParsedQueueItems(emptyList(), "items must contain at least one track")
            }

            val items = mutableListOf<MusicQueueItem>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: return ParsedQueueItems(emptyList(), "items[$index] must be an object")
                val source = item.optString("source").trim()
                if (source.isBlank()) {
                    return ParsedQueueItems(emptyList(), "items[$index].source is required")
                }
                val sourceType = item.optString("source_type").trim()
                if (sourceType.isBlank()) {
                    return ParsedQueueItems(
                        emptyList(),
                        "items[$index].source_type is required: path, url, or uri"
                    )
                }
                items.add(
                    MusicQueueItem(
                        source = source,
                        sourceType = sourceType,
                        title = item.optString("title").trim().takeIf { it.isNotBlank() },
                        artist = item.optString("artist").trim().takeIf { it.isNotBlank() }
                    )
                )
            }
            ParsedQueueItems(items, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse music queue items", e)
            ParsedQueueItems(emptyList(), "items must be a JSON array")
        }
    }

    private fun parseRequiredLong(tool: AITool, name: String): ParsedRequiredLong {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw.isNullOrBlank()) return ParsedRequiredLong(0L, "Must provide $name parameter")
        val value = raw.toLongOrNull()
        return if (value == null) {
            ParsedRequiredLong(0L, "$name must be an integer")
        } else {
            ParsedRequiredLong(value, null)
        }
    }

    private data class ParsedBoolean(val value: Boolean?, val error: String?)
    private data class ParsedFloat(val value: Float?, val error: String?)
    private data class ParsedRequiredFloat(val value: Float, val error: String?)
    private data class ParsedInt(val value: Int?, val error: String?)
    private data class ParsedLong(val value: Long?, val error: String?)
    private data class ParsedRequiredLong(val value: Long, val error: String?)
    private data class ParsedQueueItems(val value: List<MusicQueueItem>, val error: String?)

    companion object {
        private const val TAG = "MusicPlaybackTools"
    }
}

private data class MusicQueueItem(
    val source: String,
    val sourceType: String,
    val title: String?,
    val artist: String?
)

private class MusicPlaybackManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var metadata: CurrentMusic? = null
    private var queueMetadata: List<CurrentMusic> = emptyList()
    private var playbackState: String = "idle"
    private var lastError: String? = null
    private var currentVolume: Float = 1f
    private var currentLoop: Boolean = false

    suspend fun play(
        source: String,
        sourceType: String,
        title: String?,
        artist: String?,
        loop: Boolean?,
        volume: Float?,
        startPositionMs: Long?
    ): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val itemUri = resolveUri(source, sourceType)
            val musicTitle = title?.takeIf { it.isNotBlank() }
            val musicArtist = artist?.takeIf { it.isNotBlank() }
            val musicLoop = loop ?: false
            val musicVolume = volume ?: 1f
            validateVolume(musicVolume)
            if (startPositionMs != null && startPositionMs < 0L) {
                throw IllegalArgumentException("start_position_ms must be zero or greater")
            }

            val mediaItem =
                buildMediaItem(
                    uri = itemUri,
                    title = musicTitle,
                    artist = musicArtist
                )

            val activePlayer = ensurePlayer()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setMediaItem(mediaItem)
            activePlayer.repeatMode =
                if (musicLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            activePlayer.volume = musicVolume
            activePlayer.prepare()
            if (startPositionMs != null) {
                activePlayer.seekTo(startPositionMs)
            }
            activePlayer.play()

            metadata =
                CurrentMusic(
                    source = source,
                    sourceType = sourceType,
                    title = musicTitle,
                    artist = musicArtist
                )
            queueMetadata = listOf(metadata!!)
            currentLoop = musicLoop
            currentVolume = musicVolume
            lastError = null
            playbackState = "playing"
            buildResult("Playback started")
        }

    suspend fun playQueue(
        items: List<MusicQueueItem>,
        loop: Boolean?,
        volume: Float?,
        startIndex: Int?,
        startPositionMs: Long?
    ): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            if (items.isEmpty()) {
                throw IllegalArgumentException("items must contain at least one track")
            }
            val musicLoop = loop ?: false
            val musicVolume = volume ?: 1f
            val firstIndex = startIndex ?: 0
            validateVolume(musicVolume)
            if (firstIndex < 0 || firstIndex >= items.size) {
                throw IllegalArgumentException("start_index must be between 0 and ${items.lastIndex}")
            }
            if (startPositionMs != null && startPositionMs < 0L) {
                throw IllegalArgumentException("start_position_ms must be zero or greater")
            }

            val mediaItems = items.map { item ->
                buildMediaItem(
                    uri = resolveUri(item.source, item.sourceType),
                    title = item.title,
                    artist = item.artist
                )
            }
            val queue = items.map { item ->
                CurrentMusic(
                    source = item.source,
                    sourceType = item.sourceType,
                    title = item.title,
                    artist = item.artist
                )
            }

            val activePlayer = ensurePlayer()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setMediaItems(mediaItems, firstIndex, startPositionMs ?: C.TIME_UNSET)
            activePlayer.repeatMode =
                if (musicLoop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            activePlayer.volume = musicVolume
            activePlayer.prepare()
            activePlayer.play()

            queueMetadata = queue
            metadata = queue[firstIndex]
            currentLoop = musicLoop
            currentVolume = musicVolume
            lastError = null
            playbackState = "playing"
            buildResult("Queue playback started")
        }

    suspend fun pause(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.pause()
            playbackState = "paused"
            buildResult("Playback paused")
        }

    suspend fun resume(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.play()
            playbackState = "playing"
            buildResult("Playback resumed")
        }

    suspend fun stop(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            metadata = null
            queueMetadata = emptyList()
            currentLoop = false
            playbackState = "stopped"
            buildResult("Playback stopped")
        }

    suspend fun seek(positionMs: Long): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            if (positionMs < 0L) {
                throw IllegalArgumentException("position_ms must be zero or greater")
            }
            val activePlayer = requirePlayer()
            activePlayer.seekTo(positionMs)
            buildResult("Playback position updated")
        }

    suspend fun setVolume(volume: Float): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            validateVolume(volume)
            val activePlayer = requirePlayer()
            activePlayer.volume = volume
            currentVolume = volume
            buildResult("Playback volume updated")
        }

    suspend fun status(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            buildResult(lastError ?: "Playback status")
        }

    private fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing

        val created =
            ExoPlayer.Builder(appContext).build().apply {
                val audioAttributes =
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                setAudioAttributes(audioAttributes, true)
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            this@MusicPlaybackManager.playbackState =
                                when (state) {
                                    Player.STATE_BUFFERING -> "preparing"
                                    Player.STATE_READY -> if (this@apply.isPlaying) "playing" else "paused"
                                    Player.STATE_ENDED -> "ended"
                                    Player.STATE_IDLE -> this@MusicPlaybackManager.playbackState
                                    else -> this@MusicPlaybackManager.playbackState
                                }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (
                                this@MusicPlaybackManager.playbackState != "stopped" &&
                                    this@MusicPlaybackManager.playbackState != "ended"
                            ) {
                                this@MusicPlaybackManager.playbackState =
                                    if (isPlaying) "playing" else "paused"
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            this@MusicPlaybackManager.playbackState = "error"
                            lastError = error.message
                        }
                    }
                )
            }
        player = created
        return created
    }

    private fun requirePlayer(): ExoPlayer {
        return player ?: throw IllegalStateException("No active music playback session")
    }

    private fun resolveUri(source: String, sourceType: String): Uri {
        return when (sourceType) {
            "path" -> {
                val file = File(source)
                if (!file.exists() || !file.isFile) {
                    throw IllegalArgumentException("Music file does not exist: $source")
                }
                Uri.fromFile(file)
            }
            "url" -> {
                val uri = Uri.parse(source)
                val scheme = uri.scheme
                if (scheme != "http" && scheme != "https") {
                    throw IllegalArgumentException("url source_type requires http or https source")
                }
                uri
            }
            "uri" -> {
                val uri = Uri.parse(source)
                if (uri.scheme.isNullOrBlank()) {
                    throw IllegalArgumentException("uri source_type requires a URI scheme")
                }
                uri
            }
            else -> throw IllegalArgumentException("source_type must be path, url, or uri")
        }
    }

    private fun buildMediaItem(uri: Uri, title: String?, artist: String?): MediaItem {
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()
    }

    private fun validateVolume(volume: Float) {
        if (volume < 0f || volume > 1f) {
            throw IllegalArgumentException("volume must be between 0 and 1")
        }
    }

    private fun buildResult(message: String): MusicPlaybackResultData {
        val activePlayer = player
        val duration = activePlayer?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L }
        val currentIndex = activePlayer?.currentMediaItemIndex ?: 0
        val currentMetadata = queueMetadata.getOrNull(currentIndex) ?: metadata
        val queueSize = queueMetadata.size.takeIf { it > 1 }
        return MusicPlaybackResultData(
            state = playbackState,
            source = currentMetadata?.source,
            sourceType = currentMetadata?.sourceType,
            title = currentMetadata?.title,
            artist = currentMetadata?.artist,
            durationMs = duration,
            positionMs = activePlayer?.currentPosition ?: 0L,
            bufferedPositionMs = activePlayer?.bufferedPosition ?: 0L,
            volume = currentVolume,
            loop = currentLoop,
            queueIndex = queueSize?.let { currentIndex },
            queueSize = queueSize,
            message = message
        )
    }

    private data class CurrentMusic(
        val source: String,
        val sourceType: String,
        val title: String?,
        val artist: String?
    )

    companion object {
        @Volatile private var INSTANCE: MusicPlaybackManager? = null

        fun getInstance(context: Context): MusicPlaybackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlaybackManager(context).also { INSTANCE = it }
            }
        }
    }
}
