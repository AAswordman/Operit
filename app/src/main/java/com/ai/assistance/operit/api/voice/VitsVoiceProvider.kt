package com.ai.assistance.operit.api.voice

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VitsVoiceProvider(
    private val context: Context,
    private val config: SpeechServicesPreferences.VitsTtsPackageConfig,
    private val defaultSpeechRate: Float,
) : VoiceService {

    private companion object {
        private const val TAG = "VitsVoiceProvider"
        private const val SPEECH_PREVIEW_MAX = 48
        private const val DEFAULT_CHUNK_FRAMES = 2048
        private const val PACKAGE_MANIFEST = "operit-vits-tts.json"
    }

    private data class RuntimeConfig(
        val sampleRate: Int,
        val tokenMap: Map<String, List<Long>>,
        val lexicon: PackageLexicon?,
        val frontend: String,
        val addBlank: Boolean,
        val blankId: Long?,
        val bosIds: List<Long>,
        val eosIds: List<Long>,
        val noiseScale: Float?,
        val lengthScale: Float?,
        val noiseW: Float?,
        val speakerCount: Int?
    )

    private data class PackageFiles(
        val root: File,
        val modelFile: File,
        val configFile: File,
        val lexiconFile: File?
    )

    private data class InputBindings(
        val idsInputName: String,
        val lengthInputName: String?,
        val scalesInputName: String?,
        val sidInputName: String?
    )

    private data class SpeakRequest(
        val text: String,
        val rate: Float?,
        val pitch: Float?,
        val extraParams: Map<String, String>,
        val generation: Long,
        val completion: CompletableDeferred<Boolean>
    )

    private data class PreparedSpeech(
        val request: SpeakRequest,
        val pcm: ShortArray,
        val sampleRate: Int
    )

    private class PackageLexicon(
        private val entries: Map<String, LongArray>,
        private val tokenMap: Map<String, List<Long>>
    ) {
        private val maxEntryChars = entries.keys.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1

        fun tokenize(text: String): LongArray {
            val ids = ArrayList<Long>()
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                val symbol = String(Character.toChars(codePoint))

                if (Character.isWhitespace(codePoint)) {
                    tokenMap[" "]?.let { ids.addAll(it) }
                    index += charCount
                    continue
                }

                if (isAsciiWordChar(codePoint)) {
                    val start = index
                    index += charCount
                    while (index < text.length) {
                        val next = text.codePointAt(index)
                        if (!isAsciiWordChar(next)) break
                        index += Character.charCount(next)
                    }
                    val word = text.substring(start, index).lowercase(Locale.ROOT)
                    val wordIds = entries[word]
                        ?: throw IllegalArgumentException("unknown word: $word")
                    ids.addAll(wordIds.toList())
                    continue
                }

                val lexiconMatch = longestEntryAt(text, index)
                if (lexiconMatch != null) {
                    ids.addAll(lexiconMatch.second.toList())
                    index += lexiconMatch.first.length
                    continue
                }

                val mapped = tokenMap[symbol]
                    ?: throw IllegalArgumentException("unknown symbol: $symbol")
                ids.addAll(mapped)
                index += charCount
            }
            return ids.toLongArray()
        }

        private fun longestEntryAt(text: String, start: Int): Pair<String, LongArray>? {
            var end = start
            var count = 0
            val pieces = ArrayList<String>()
            while (end < text.length && count < maxEntryChars) {
                val cp = text.codePointAt(end)
                val part = String(Character.toChars(cp))
                pieces.add(part)
                end += Character.charCount(cp)
                count++
            }

            for (size in pieces.size downTo 1) {
                val candidate = pieces.take(size).joinToString("").lowercase(Locale.ROOT)
                val ids = entries[candidate]
                if (ids != null) return candidate to ids
            }
            return null
        }

        private fun isAsciiWordChar(codePoint: Int): Boolean {
            return codePoint in 'a'.code..'z'.code ||
                codePoint in 'A'.code..'Z'.code ||
                codePoint in '0'.code..'9'.code ||
                codePoint == '\''.code ||
                codePoint == '-'.code
        }
    }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val initializeMutex = Mutex()
    private val playbackMutex = Mutex()
    private val stateLock = Any()
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakQueue = Channel<SpeakRequest>(Channel.UNLIMITED)
    private val playbackQueue = Channel<PreparedSpeech>(capacity = 1)
    private val stopGeneration = AtomicLong(0)

    private var session: OrtSession? = null
    private var runtimeConfig: RuntimeConfig? = null
    private var inputBindings: InputBindings? = null
    private var currentSpeakerId: String = config.speakerId.trim()

    private var currentAudioTrack: AudioTrack? = null
    private var playbackGeneration: Long = 0L
    private var paused = false

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    init {
        providerScope.launch {
            for (request in speakQueue) {
                try {
                    if (request.generation != stopGeneration.get()) {
                        request.completion.complete(false)
                        continue
                    }

                    val prepared = prepareSpeech(request)
                    if (prepared == null) {
                        request.completion.complete(false)
                    } else {
                        playbackQueue.send(prepared)
                    }
                } catch (e: Exception) {
                    request.completion.completeExceptionally(e)
                }
            }
        }

        providerScope.launch {
            for (prepared in playbackQueue) {
                try {
                    if (prepared.request.generation != stopGeneration.get()) {
                        prepared.request.completion.complete(false)
                        continue
                    }

                    val result = playbackMutex.withLock {
                        val generation = synchronized(stateLock) { playbackGeneration }
                        playPcm16(prepared.pcm, prepared.sampleRate, generation)
                    }
                    prepared.request.completion.complete(result)
                } catch (e: Exception) {
                    prepared.request.completion.completeExceptionally(e)
                }
            }
        }
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        initializeMutex.withLock {
            if (_isInitialized.value && session != null && runtimeConfig != null && inputBindings != null) {
                return@withLock true
            }

            try {
                val packageRoot = resolvePackageRoot(config.packagePath)
                val packageFiles = resolvePackageFiles(packageRoot)
                val parsedConfig = parseRuntimeConfig(packageFiles)
                val opts = OrtSession.SessionOptions().apply {
                    val threadCount = optionalInt("threads") ?: 1
                    setIntraOpNumThreads(threadCount)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val createdSession = env.createSession(packageFiles.modelFile.absolutePath, opts)
                val bindings = try {
                    resolveInputBindings(createdSession)
                } catch (e: Exception) {
                    createdSession.close()
                    throw e
                }

                session?.close()
                session = createdSession
                runtimeConfig = parsedConfig
                inputBindings = bindings
                _isInitialized.value = true

                AppLogger.d(
                    TAG,
                    "Initialized package=${packageFiles.root.absolutePath} model=${packageFiles.modelFile.name} inputs=${createdSession.inputNames} outputs=${createdSession.outputNames} sampleRate=${parsedConfig.sampleRate} frontend=${parsedConfig.frontend}"
                )
                true
            } catch (e: Exception) {
                _isInitialized.value = false
                AppLogger.e(TAG, "VITS TTS initialize failed", e)
                if (e is TtsException) throw e
                throw TtsException(context.getString(R.string.vits_tts_error_init_failed), cause = e)
            }
        }
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        if (interrupt) {
            clearForInterrupt()
        }

        val completion = CompletableDeferred<Boolean>()
        val request = SpeakRequest(
            text = text,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams,
            generation = stopGeneration.get(),
            completion = completion
        )
        speakQueue.send(request)
        completion.await()
    }

    private suspend fun prepareSpeech(request: SpeakRequest): PreparedSpeech? {
        if (!isInitialized) {
            val initOk = initialize()
            if (!initOk) return null
        }

        if (request.generation != stopGeneration.get()) {
            return null
        }

        val activeSession = session
            ?: throw TtsException(context.getString(R.string.vits_tts_error_init_failed))
        val activeConfig = runtimeConfig
            ?: throw TtsException(context.getString(R.string.vits_tts_error_init_failed))
        val bindings = inputBindings
            ?: throw TtsException(context.getString(R.string.vits_tts_error_init_failed))

        try {
            val effectiveRate = request.rate ?: defaultSpeechRate
            val ids = tokenize(request.text, activeConfig, request.extraParams)
            if (ids.isEmpty()) {
                throw TtsException(context.getString(R.string.vits_tts_error_tokenize_failed, "empty token ids"))
            }

            AppLogger.d(
                TAG,
                "speak len=${request.text.length} preview=\"${speechPreview(request.text)}\" ids=${ids.size} rate=$effectiveRate pitch=${request.pitch} speaker=$currentSpeakerId"
            )

            val pcm = runModel(activeSession, activeConfig, bindings, ids, effectiveRate)
            if (pcm.isEmpty()) {
                throw TtsException(context.getString(R.string.vits_tts_error_output_empty))
            }
            if (request.generation != stopGeneration.get()) {
                return null
            }

            return PreparedSpeech(request, pcm, activeConfig.sampleRate)
        } catch (e: Exception) {
            AppLogger.e(TAG, "VITS TTS speak failed", e)
            if (e is TtsException) throw e
            throw TtsException(context.getString(R.string.vits_tts_error_request_failed), cause = e)
        }
    }

    private fun runModel(
        activeSession: OrtSession,
        activeConfig: RuntimeConfig,
        bindings: InputBindings,
        ids: LongArray,
        effectiveRate: Float
    ): ShortArray {
        val toClose = ArrayList<AutoCloseable>()
        val inputs = LinkedHashMap<String, OnnxTensor>()

        try {
            val idsInfo = tensorInfo(activeSession, bindings.idsInputName)
            val idsShape = shapeForValues(idsInfo, ids.size, bindings.idsInputName)
            val idsTensor = createIntegerTensor(bindings.idsInputName, ids, idsShape, idsInfo)
            toClose.add(idsTensor)
            inputs[bindings.idsInputName] = idsTensor

            bindings.lengthInputName?.let { name ->
                val lengthInfo = tensorInfo(activeSession, name)
                val lengthShape = shapeForValues(lengthInfo, 1, name)
                val lengthTensor = createIntegerTensor(
                    name = name,
                    values = longArrayOf(ids.size.toLong()),
                    shape = lengthShape,
                    info = lengthInfo
                )
                toClose.add(lengthTensor)
                inputs[name] = lengthTensor
            }

            bindings.scalesInputName?.let { name ->
                val noiseScale = activeConfig.noiseScale
                    ?: throw TtsException(context.getString(R.string.vits_tts_error_scales_not_set, "noise_scale"))
                val lengthScale = activeConfig.lengthScale
                    ?: throw TtsException(context.getString(R.string.vits_tts_error_scales_not_set, "length_scale"))
                val noiseW = activeConfig.noiseW
                    ?: throw TtsException(context.getString(R.string.vits_tts_error_scales_not_set, "noise_w"))
                val scales = floatArrayOf(noiseScale, lengthScale / effectiveRate.coerceAtLeast(0.01f), noiseW)
                val scalesInfo = tensorInfo(activeSession, name)
                val scalesShape = shapeForValues(scalesInfo, scales.size, name)
                val scalesTensor = createFloatTensor(name, scales, scalesShape, scalesInfo)
                toClose.add(scalesTensor)
                inputs[name] = scalesTensor
            }

            bindings.sidInputName?.let { name ->
                val speaker = currentSpeakerId.toLongOrNull()
                    ?: throw TtsException(context.getString(R.string.vits_tts_error_speaker_required))
                val sidInfo = tensorInfo(activeSession, name)
                val sidShape = shapeForValues(sidInfo, 1, name)
                val sidTensor = createIntegerTensor(name, longArrayOf(speaker), sidShape, sidInfo)
                toClose.add(sidTensor)
                inputs[name] = sidTensor
            }

            activeSession.run(inputs).use { result ->
                val firstOutput = result.get(0).value
                return extractPcm16(firstOutput)
            }
        } finally {
            for (i in toClose.indices.reversed()) {
                try {
                    toClose[i].close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun playPcm16(pcm: ShortArray, sampleRate: Int, generation: Long): Boolean {
        val channelMask = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, audioFormat)
        if (minBufferSize <= 0) {
            throw TtsException(context.getString(R.string.vits_tts_error_playback_failed, minBufferSize))
        }
        val bufferSize = minBufferSize.coerceAtLeast(DEFAULT_CHUNK_FRAMES * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        var shouldReleaseImmediately = false
        synchronized(stateLock) {
            if (playbackGeneration == generation) {
                currentAudioTrack?.release()
                currentAudioTrack = track
                paused = false
            } else {
                shouldReleaseImmediately = true
            }
        }
        if (shouldReleaseImmediately) {
            track.release()
            return false
        }

        try {
            track.play()
            _isSpeaking.value = true

            var offset = 0
            while (offset < pcm.size) {
                if (!isCurrentPlayback(generation)) return false
                while (isPlaybackPaused(generation)) {
                    delay(50)
                }
                val count = minOf(DEFAULT_CHUNK_FRAMES, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) {
                    throw TtsException(context.getString(R.string.vits_tts_error_playback_failed, written))
                }
                if (written == 0) {
                    delay(10)
                    continue
                }
                offset += written
            }

            while (isCurrentPlayback(generation) && track.playbackHeadPosition < pcm.size) {
                if (isPlaybackPaused(generation)) {
                    delay(50)
                } else {
                    delay(20)
                }
            }

            return isCurrentPlayback(generation)
        } finally {
            synchronized(stateLock) {
                if (currentAudioTrack === track) {
                    currentAudioTrack = null
                }
                paused = false
            }
            _isSpeaking.value = false
            try {
                track.stop()
            } catch (_: Exception) {
            }
            try {
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun clearPendingRequests() {
        while (true) {
            val request = speakQueue.tryReceive().getOrNull() ?: break
            request.completion.complete(false)
        }
    }

    private fun clearPendingPlayback() {
        while (true) {
            val prepared = playbackQueue.tryReceive().getOrNull() ?: break
            prepared.request.completion.complete(false)
        }
    }

    private fun clearForInterrupt() {
        stopGeneration.incrementAndGet()
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
    }

    private fun stopPlaybackOnly(): Boolean {
        val track = synchronized(stateLock) {
            playbackGeneration++
            paused = false
            val active = currentAudioTrack
            currentAudioTrack = null
            active
        }

        return try {
            track?.let {
                try {
                    it.pause()
                    it.flush()
                    it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
            _isSpeaking.value = false
            track != null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Stop VITS TTS playback failed", e)
            false
        }
    }

    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        stopGeneration.incrementAndGet()
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
        true
    }

    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val track = synchronized(stateLock) {
            currentAudioTrack?.also { paused = true }
        } ?: return@withContext false

        return@withContext try {
            track.pause()
            _isSpeaking.value = false
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Pause VITS TTS playback failed", e)
            false
        }
    }

    override suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        val track = synchronized(stateLock) {
            currentAudioTrack?.also { paused = false }
        } ?: return@withContext false

        return@withContext try {
            track.play()
            _isSpeaking.value = true
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Resume VITS TTS playback failed", e)
            false
        }
    }

    override fun shutdown() {
        stopGeneration.incrementAndGet()
        providerScope.cancel()
        clearPendingRequests()
        clearPendingPlayback()
        speakQueue.close()
        playbackQueue.close()

        synchronized(stateLock) {
            playbackGeneration++
            paused = false
            currentAudioTrack?.release()
            currentAudioTrack = null
        }
        _isSpeaking.value = false
        _isInitialized.value = false

        try {
            session?.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Close VITS TTS session failed", e)
        } finally {
            session = null
            runtimeConfig = null
            inputBindings = null
        }
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> = withContext(Dispatchers.IO) {
        val speakerCount = runtimeConfig?.speakerCount ?: optionalInt("speaker_count") ?: return@withContext emptyList()
        val locale = optionalString("locale").takeIf { it.isNotBlank() } ?: "und"
        return@withContext (0 until speakerCount).map { id ->
            VoiceService.Voice(id.toString(), "Speaker $id", locale, "NEUTRAL")
        }
    }

    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        currentSpeakerId = voiceId.trim()
        true
    }

    private fun resolvePackageRoot(raw: String): File {
        val packagePath = normalizeLocalPath(raw)
        if (packagePath.isBlank()) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_path_not_set))
        }

        val source = File(packagePath)
        if (!source.exists()) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_file_not_found, packagePath))
        }

        if (source.isDirectory) return source
        if (!source.isFile || !source.extension.equals("zip", ignoreCase = true)) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_path_invalid, packagePath))
        }

        return extractZipPackage(source)
    }

    private fun extractZipPackage(zipFile: File): File {
        val signature = "${zipFile.absolutePath}|${zipFile.length()}|${zipFile.lastModified()}"
        val packageDir = File(context.filesDir, "vits_tts_packages")
        val target = File(packageDir, sha256(signature).take(16))
        val marker = File(target, ".source")
        if (target.isDirectory && marker.isFile && marker.readText(Charsets.UTF_8) == signature) {
            return target
        }

        if (target.exists()) {
            target.deleteRecursively()
        }
        if (!target.mkdirs() && !target.isDirectory) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_extract_failed))
        }

        val canonicalTarget = target.canonicalFile
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                var entry = zip.nextEntry
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (entry != null) {
                    val entryName = entry.name.replace('\\', '/')
                    val outFile = File(target, entryName).canonicalFile
                    if (!outFile.path.startsWith(canonicalTarget.path + File.separator)) {
                        throw TtsException(context.getString(R.string.vits_tts_error_package_zip_entry_unsafe, entry.name))
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: TtsException) {
            throw e
        } catch (e: Exception) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_zip_read_failed), cause = e)
        }

        marker.writeText(signature, Charsets.UTF_8)
        return target
    }

    private fun resolvePackageFiles(root: File): PackageFiles {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        val manifest = files.firstOrNull { it.name == PACKAGE_MANIFEST }?.let {
            JSONObject(it.readText(Charsets.UTF_8))
        }

        val modelFile = manifestPath(root, manifest, "model")
            ?: optionPath(root, "model_path")
            ?: singleCandidate(
                files.filter { it.extension.equals("onnx", ignoreCase = true) },
                R.string.vits_tts_error_package_model_not_found,
                R.string.vits_tts_error_package_model_ambiguous
            )

        val configFile = manifestPath(root, manifest, "config")
            ?: optionPath(root, "config_path")
            ?: File(modelFile.absolutePath + ".json").takeIf { it.isFile }
            ?: singleCandidate(
                files.filter { it.extension.equals("json", ignoreCase = true) && isTtsConfigJson(it) },
                R.string.vits_tts_error_package_config_not_found,
                R.string.vits_tts_error_package_config_ambiguous
            )

        val lexiconFile = manifestPath(root, manifest, "lexicon")
            ?: optionPath(root, "lexicon_path")
            ?: optionalSingleCandidate(
                files.filter { it.name.equals("lexicon.txt", ignoreCase = true) },
                R.string.vits_tts_error_package_lexicon_ambiguous
            )

        return PackageFiles(root, modelFile, configFile, lexiconFile)
    }

    private fun parseRuntimeConfig(packageFiles: PackageFiles): RuntimeConfig {
        val root = try {
            JSONObject(packageFiles.configFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw TtsException(context.getString(R.string.vits_tts_error_config_parse_failed), cause = e)
        }

        val sampleRate = optionalInt("sample_rate")
            ?: root.optJSONObject("audio")?.optionalInt("sample_rate")
            ?: root.optionalInt("sample_rate")
            ?: throw TtsException(context.getString(R.string.vits_tts_error_sample_rate_not_set))

        if (sampleRate <= 0) {
            throw TtsException(context.getString(R.string.vits_tts_error_sample_rate_not_set))
        }

        val tokenSource = firstTokenMapObject(root)
        val tokenSourceObject = tokenSource?.second
        val tokenMap = tokenSourceObject?.let { parseTokenMap(it) }
            ?: firstTokenArray(root)?.let { parseTokenArray(it) }
            ?: emptyMap()

        if (tokenMap.isEmpty()) {
            throw TtsException(context.getString(R.string.vits_tts_error_tokenize_failed, "token map is empty"))
        }

        val inference = root.optJSONObject("inference")
        val blankId = optionalLong("blank_token_id")
            ?: tokenSourceObject?.let { readFirstTokenId(it, "_") }
            ?: tokenSourceObject?.let { readFirstTokenId(it, "") }
            ?: tokenSourceObject?.let { readFirstTokenId(it, "<blank>") }
        val addBlank = optionalBool("add_blank")
            ?: optionalBool("interleave_blank")
            ?: (tokenSource?.first == "phoneme_id_map" && blankId != null)
        val frontend = optionalString("frontend").ifBlank { "lexicon" }
        val lexicon = packageFiles.lexiconFile?.let { loadLexicon(it, tokenMap) }

        return RuntimeConfig(
            sampleRate = sampleRate,
            tokenMap = tokenMap,
            lexicon = lexicon,
            frontend = frontend,
            addBlank = addBlank,
            blankId = blankId,
            bosIds = optionalIds("bos_token_ids")
                ?: optionalLong("bos_token_id")?.let { listOf(it) }
                ?: tokenSourceObject?.let { readTokenIds(it, "^") }.orEmpty(),
            eosIds = optionalIds("eos_token_ids")
                ?: optionalLong("eos_token_id")?.let { listOf(it) }
                ?: tokenSourceObject?.let { readTokenIds(it, "\$") }.orEmpty(),
            noiseScale = optionalFloat("noise_scale") ?: inference?.optionalFloat("noise_scale"),
            lengthScale = optionalFloat("length_scale") ?: inference?.optionalFloat("length_scale"),
            noiseW = optionalFloat("noise_w") ?: inference?.optionalFloat("noise_w"),
            speakerCount = optionalInt("speaker_count") ?: root.optionalInt("num_speakers")
        )
    }

    private fun tokenize(
        text: String,
        activeConfig: RuntimeConfig,
        extraParams: Map<String, String>
    ): LongArray {
        val directIds = listOf("token_ids", "phoneme_ids", "ids")
            .firstNotNullOfOrNull { key -> extraParams[key]?.takeIf { it.isNotBlank() } }
        if (directIds != null) {
            return parseIds(directIds, "extra token ids").toLongArray()
        }

        val textMode = optionalString("text_mode")
        if (textMode.equals("token_ids", ignoreCase = true) || textMode.equals("phoneme_ids", ignoreCase = true)) {
            return parseIds(text, "text token ids").toLongArray()
        }

        val bodyIds = when (activeConfig.frontend.lowercase(Locale.ROOT)) {
            "lexicon" -> {
                val lexicon = activeConfig.lexicon
                    ?: throw TtsException(context.getString(R.string.vits_tts_error_raw_text_requires_lexicon))
                try {
                    lexicon.tokenize(text)
                } catch (e: IllegalArgumentException) {
                    throw TtsException(context.getString(R.string.vits_tts_error_tokenize_failed, e.message.orEmpty()), cause = e)
                }
            }
            "direct_symbols" -> tokenizeSymbolsByMap(text, activeConfig.tokenMap)
            else -> throw TtsException(context.getString(R.string.vits_tts_error_frontend_unsupported, activeConfig.frontend))
        }

        if (bodyIds.isEmpty()) return bodyIds

        val ids = ArrayList<Long>()
        ids.addAll(activeConfig.bosIds)
        ids.addAll(bodyIds.toList())
        ids.addAll(activeConfig.eosIds)

        if (activeConfig.addBlank && ids.isNotEmpty()) {
            val blank = activeConfig.blankId
                ?: throw TtsException(context.getString(R.string.vits_tts_error_blank_token_not_set))
            val withBlank = ArrayList<Long>(ids.size * 2 - 1)
            ids.forEachIndexed { index, id ->
                if (index > 0) {
                    withBlank.add(blank)
                }
                withBlank.add(id)
            }
            return withBlank.toLongArray()
        }

        return ids.toLongArray()
    }

    private fun loadLexicon(file: File, tokenMap: Map<String, List<Long>>): PackageLexicon {
        val entries = LinkedHashMap<String, LongArray>()
        try {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachIndexed
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size < 2) {
                    throw TtsException(context.getString(R.string.vits_tts_error_lexicon_parse_failed, index + 1))
                }
                val word = parts.first().lowercase(Locale.ROOT)
                if (entries.containsKey(word)) {
                    throw TtsException(context.getString(R.string.vits_tts_error_lexicon_duplicate_word, word))
                }
                val ids = ArrayList<Long>()
                parts.drop(1).forEach { token ->
                    val tokenIds = tokenMap[token]
                        ?: throw TtsException(context.getString(R.string.vits_tts_error_lexicon_unknown_token, token))
                    ids.addAll(tokenIds)
                }
                entries[word] = ids.toLongArray()
            }
        } catch (e: TtsException) {
            throw e
        } catch (e: Exception) {
            throw TtsException(context.getString(R.string.vits_tts_error_lexicon_parse_failed, 0), cause = e)
        }
        return PackageLexicon(entries, tokenMap)
    }

    private fun resolveInputBindings(activeSession: OrtSession): InputBindings {
        val inputNames = activeSession.inputNames.toSet()
        val idsInput = resolveInputName(
            inputNames = inputNames,
            explicitOptionKeys = listOf("ids_input", "input_ids_name"),
            candidates = listOf("input", "input_ids", "ids", "text", "x"),
            requiredLabel = "input ids"
        )
        val lengthInput = resolveOptionalInputName(
            inputNames = inputNames,
            explicitOptionKeys = listOf("length_input", "input_lengths_name"),
            candidates = listOf("input_lengths", "text_lengths", "lengths", "x_lengths")
        )
        val scalesInput = resolveOptionalInputName(
            inputNames = inputNames,
            explicitOptionKeys = listOf("scales_input", "scales_name"),
            candidates = listOf("scales")
        )
        val sidInput = resolveOptionalInputName(
            inputNames = inputNames,
            explicitOptionKeys = listOf("sid_input", "speaker_input"),
            candidates = listOf("sid", "speaker_id", "speaker")
        )

        return InputBindings(
            idsInputName = idsInput,
            lengthInputName = lengthInput,
            scalesInputName = scalesInput,
            sidInputName = sidInput
        )
    }

    private fun resolveInputName(
        inputNames: Set<String>,
        explicitOptionKeys: List<String>,
        candidates: List<String>,
        requiredLabel: String
    ): String {
        explicitOptionKeys.firstNotNullOfOrNull { key ->
            config.options[key]?.trim()?.takeIf { it.isNotBlank() }
        }?.let { explicit ->
            if (explicit !in inputNames) {
                throw TtsException(context.getString(R.string.vits_tts_error_input_not_found, explicit))
            }
            return explicit
        }

        return candidates.firstOrNull { it in inputNames }
            ?: throw TtsException(
                context.getString(
                    R.string.vits_tts_error_input_name_not_resolved,
                    requiredLabel,
                    inputNames.joinToString()
                )
            )
    }

    private fun resolveOptionalInputName(
        inputNames: Set<String>,
        explicitOptionKeys: List<String>,
        candidates: List<String>
    ): String? {
        explicitOptionKeys.firstNotNullOfOrNull { key ->
            config.options[key]?.trim()?.takeIf { it.isNotBlank() }
        }?.let { explicit ->
            if (explicit !in inputNames) {
                throw TtsException(context.getString(R.string.vits_tts_error_input_not_found, explicit))
            }
            return explicit
        }
        return candidates.firstOrNull { it in inputNames }
    }

    private fun tensorInfo(activeSession: OrtSession, name: String): TensorInfo {
        return activeSession.inputInfo[name]?.info as? TensorInfo
            ?: throw TtsException(context.getString(R.string.vits_tts_error_tensor_info_missing, name))
    }

    private fun createIntegerTensor(
        name: String,
        values: LongArray,
        shape: LongArray,
        info: TensorInfo
    ): OnnxTensor {
        return when (info.type) {
            OnnxJavaType.INT64 -> OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
            OnnxJavaType.INT32 -> {
                val intValues = IntArray(values.size) { index ->
                    val value = values[index]
                    if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
                        throw TtsException(context.getString(R.string.vits_tts_error_integer_out_of_range, name))
                    }
                    value.toInt()
                }
                OnnxTensor.createTensor(env, IntBuffer.wrap(intValues), shape)
            }
            else -> throw TtsException(
                context.getString(R.string.vits_tts_error_unsupported_input_type, name, info.type.name)
            )
        }
    }

    private fun createFloatTensor(
        name: String,
        values: FloatArray,
        shape: LongArray,
        info: TensorInfo
    ): OnnxTensor {
        return when (info.type) {
            OnnxJavaType.FLOAT -> OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)
            OnnxJavaType.DOUBLE -> {
                val doubleValues = DoubleArray(values.size) { values[it].toDouble() }
                OnnxTensor.createTensor(env, DoubleBuffer.wrap(doubleValues), shape)
            }
            else -> throw TtsException(
                context.getString(R.string.vits_tts_error_unsupported_input_type, name, info.type.name)
            )
        }
    }

    private fun shapeForValues(info: TensorInfo, valueCount: Int, inputName: String): LongArray {
        val shape = info.shape ?: return longArrayOf(valueCount.toLong())
        if (shape.isEmpty()) {
            if (valueCount != 1) {
                throw TtsException(context.getString(R.string.vits_tts_error_shape_unsupported, inputName))
            }
            return longArrayOf()
        }

        val normalized = shape.copyOf()
        val unknownIndices = ArrayList<Int>()
        var knownProduct = 1L
        normalized.forEachIndexed { index, dim ->
            if (dim <= 0) {
                unknownIndices.add(index)
            } else {
                knownProduct *= dim
            }
        }

        if (unknownIndices.isNotEmpty()) {
            if (knownProduct <= 0 || valueCount.toLong() % knownProduct != 0L) {
                throw TtsException(context.getString(R.string.vits_tts_error_shape_unsupported, inputName))
            }
            unknownIndices.dropLast(1).forEach { index ->
                normalized[index] = 1L
            }
            normalized[unknownIndices.last()] = valueCount.toLong() / knownProduct
        }

        val product = normalized.fold(1L) { acc, dim -> acc * dim }
        if (product != valueCount.toLong()) {
            throw TtsException(context.getString(R.string.vits_tts_error_shape_unsupported, inputName))
        }
        return normalized
    }

    private fun extractPcm16(value: Any?): ShortArray {
        flattenFloats(value).takeIf { it.isNotEmpty() }?.let { return floatsToPcm16(it) }
        flattenDoubles(value).takeIf { it.isNotEmpty() }?.let { return doublesToPcm16(it) }
        flattenShorts(value).takeIf { it.isNotEmpty() }?.let { return it }
        flattenInts(value).takeIf { it.isNotEmpty() }?.let { ints ->
            return ShortArray(ints.size) { index ->
                ints[index].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        flattenBytes(value).takeIf { it.isNotEmpty() }?.let { bytes ->
            if (bytes.size % 2 != 0) {
                throw TtsException(context.getString(R.string.vits_tts_error_output_unsupported))
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return ShortArray(bytes.size / 2) { buffer.short }
        }
        throw TtsException(context.getString(R.string.vits_tts_error_output_unsupported))
    }

    private fun floatsToPcm16(samples: FloatArray): ShortArray {
        return ShortArray(samples.size) { index ->
            val sample = samples[index]
            val safeSample = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
            (safeSample * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    private fun doublesToPcm16(samples: DoubleArray): ShortArray {
        return ShortArray(samples.size) { index ->
            val sample = samples[index]
            val safeSample = if (sample.isFinite()) sample.coerceIn(-1.0, 1.0) else 0.0
            (safeSample * Short.MAX_VALUE).roundToInt().toShort()
        }
    }

    private fun flattenFloats(value: Any?): FloatArray {
        val result = ArrayList<Float>()
        fun walk(v: Any?) {
            when (v) {
                is FloatArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return result.toFloatArray()
    }

    private fun flattenDoubles(value: Any?): DoubleArray {
        val result = ArrayList<Double>()
        fun walk(v: Any?) {
            when (v) {
                is DoubleArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return result.toDoubleArray()
    }

    private fun flattenShorts(value: Any?): ShortArray {
        val result = ArrayList<Short>()
        fun walk(v: Any?) {
            when (v) {
                is ShortArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return result.toShortArray()
    }

    private fun flattenInts(value: Any?): IntArray {
        val result = ArrayList<Int>()
        fun walk(v: Any?) {
            when (v) {
                is IntArray -> v.forEach { result.add(it) }
                is LongArray -> v.forEach { result.add(it.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return result.toIntArray()
    }

    private fun flattenBytes(value: Any?): ByteArray {
        val result = ArrayList<Byte>()
        fun walk(v: Any?) {
            when (v) {
                is ByteArray -> v.forEach { result.add(it) }
                is Array<*> -> v.forEach { walk(it) }
            }
        }
        walk(value)
        return result.toByteArray()
    }

    private fun firstTokenMapObject(root: JSONObject): Pair<String, JSONObject>? {
        listOf("phoneme_id_map", "token_id_map", "tokens", "vocab").forEach { key ->
            root.optJSONObject(key)?.let { return key to it }
        }
        root.optJSONObject("model")?.optJSONObject("vocab")?.let { return "model.vocab" to it }
        return null
    }

    private fun firstTokenArray(root: JSONObject): JSONArray? {
        root.optJSONArray("symbols")?.let { return it }
        root.optJSONObject("model")?.optJSONArray("symbols")?.let { return it }
        return null
    }

    private fun parseTokenMap(obj: JSONObject): Map<String, List<Long>> {
        val result = LinkedHashMap<String, List<Long>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "_" || key.isEmpty() || key == "<blank>" || key == "^" || key == "\$") {
                continue
            }
            val ids = parseJsonIds(obj.opt(key), key)
            if (ids.isNotEmpty()) {
                result[key] = ids
            }
        }
        return result
    }

    private fun parseTokenArray(arr: JSONArray): Map<String, List<Long>> {
        val result = LinkedHashMap<String, List<Long>>()
        for (i in 0 until arr.length()) {
            val symbol = arr.optString(i, "")
            if (symbol.isNotEmpty() && symbol != "_" && symbol != "<blank>" && symbol != "^" && symbol != "\$") {
                result[symbol] = listOf(i.toLong())
            }
        }
        return result
    }

    private fun readFirstTokenId(obj: JSONObject, key: String): Long? {
        return readTokenIds(obj, key).firstOrNull()
    }

    private fun readTokenIds(obj: JSONObject, key: String): List<Long> {
        if (!obj.has(key)) return emptyList()
        return parseJsonIds(obj.opt(key), key)
    }

    private fun parseJsonIds(value: Any?, label: String): List<Long> {
        return when (value) {
            is Number -> listOf(value.toLong())
            is String -> listOf(parseLong(value, label))
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    add(parseJsonId(value.opt(i), "$label[$i]"))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseJsonId(value: Any?, label: String): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> parseLong(value, label)
            else -> throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_id, label))
        }
    }

    private fun parseIds(raw: String, label: String): List<Long> {
        return raw.split(',', ';', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { parseLong(it, label) }
    }

    private fun parseLong(raw: String, label: String): Long {
        return raw.trim().toLongOrNull()
            ?: throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_id, label))
    }

    private fun optionalIds(key: String): List<Long>? {
        val raw = config.options[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return parseIds(raw, key)
    }

    private fun optionalString(key: String): String {
        return config.options[key]?.trim().orEmpty()
    }

    private fun optionalLong(key: String): Long? {
        val raw = config.options[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw.toLongOrNull()
            ?: throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_id, key))
    }

    private fun optionalInt(key: String): Int? {
        val raw = config.options[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw.toIntOrNull()
            ?: throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_int, key))
    }

    private fun optionalFloat(key: String): Float? {
        val raw = config.options[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw.toFloatOrNull()
            ?: throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_float, key))
    }

    private fun optionalBool(key: String): Boolean? {
        val raw = config.options[key]?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (raw.lowercase(Locale.ROOT)) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> throw TtsException(context.getString(R.string.vits_tts_error_config_invalid_bool, key))
        }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optionalFloat(key: String): Float? {
        if (!has(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        }
    }

    private fun manifestPath(root: File, manifest: JSONObject?, key: String): File? {
        val raw = manifest?.optString(key, "")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return resolveRelativeFile(root, raw)
    }

    private fun optionPath(root: File, key: String): File? {
        val raw = optionalString(key).takeIf { it.isNotBlank() } ?: return null
        return resolveRelativeFile(root, raw)
    }

    private fun resolveRelativeFile(root: File, raw: String): File {
        val file = File(raw)
        if (file.isAbsolute) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_path_unsafe, raw))
        }

        val canonicalRoot = root.canonicalFile
        val resolved = File(root, raw).canonicalFile
        if (!resolved.path.startsWith(canonicalRoot.path + File.separator)) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_path_unsafe, raw))
        }
        if (!resolved.isFile) {
            throw TtsException(context.getString(R.string.vits_tts_error_package_file_not_found, raw))
        }
        return resolved
    }

    private fun singleCandidate(files: List<File>, missingRes: Int, ambiguousRes: Int): File {
        if (files.isEmpty()) throw TtsException(context.getString(missingRes))
        if (files.size > 1) throw TtsException(context.getString(ambiguousRes, files.joinToString { it.name }))
        return files.single()
    }

    private fun optionalSingleCandidate(files: List<File>, ambiguousRes: Int): File? {
        if (files.isEmpty()) return null
        if (files.size > 1) throw TtsException(context.getString(ambiguousRes, files.joinToString { it.name }))
        return files.single()
    }

    private fun isTtsConfigJson(file: File): Boolean {
        val text = file.readText(Charsets.UTF_8)
        return text.contains("\"phoneme_id_map\"") ||
            text.contains("\"token_id_map\"") ||
            text.contains("\"symbols\"")
    }

    private fun normalizeLocalPath(raw: String): String {
        return raw.trim().removePrefix("file://")
    }

    private fun tokenizeSymbolsByMap(text: String, tokenMap: Map<String, List<Long>>): LongArray {
        val sortedKeys = tokenMap.keys
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
        val result = ArrayList<Long>()
        var index = 0
        while (index < text.length) {
            val matched = sortedKeys.firstOrNull { key ->
                text.regionMatches(index, key, 0, key.length, ignoreCase = false)
            } ?: throw TtsException(
                context.getString(
                    R.string.vits_tts_error_unknown_token,
                    printableSymbol(String(Character.toChars(text.codePointAt(index))))
                )
            )
            result.addAll(tokenMap.getValue(matched))
            index += matched.length
        }
        return result.toLongArray()
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isCurrentPlayback(generation: Long): Boolean {
        return synchronized(stateLock) { playbackGeneration == generation && currentAudioTrack != null }
    }

    private fun isPlaybackPaused(generation: Long): Boolean {
        return synchronized(stateLock) { playbackGeneration == generation && paused }
    }

    private fun printableSymbol(symbol: String): String {
        return when (symbol) {
            "\n" -> "\\n"
            "\r" -> "\\r"
            "\t" -> "\\t"
            " " -> "space"
            else -> symbol
        }
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(SPEECH_PREVIEW_MAX)
    }
}
