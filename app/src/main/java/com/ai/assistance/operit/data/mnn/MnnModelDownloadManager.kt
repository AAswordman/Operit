package com.ai.assistance.operit.data.mnn

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.storage.LocalModelDeleteOutcome
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class MnnModel(
    val modelName: String,
    val size_gb: Double,
    val tags: List<String> = emptyList(),
    val sources: Map<String, String> = emptyMap(),
    val description: String = ""
)

@Serializable
data class ModelMarketData(
    val models: List<MnnModel> = emptyList()
)

// ModelScope API 响应数据结构
@Serializable
data class MsRepoInfo(
    val Code: Int = 0,
    val Data: MsResponseData? = null,
    val Message: String? = null,
    val Success: Boolean = false
)

@Serializable
data class MsResponseData(
    val Files: List<MsFileInfo>? = null
)

@Serializable
data class MsFileInfo(
    val Name: String? = null,
    val Path: String? = null,
    val Size: Long = 0,
    val Type: String? = null
)

@Serializable
private data class PersistentDownloadState(
    val modelName: String,
    val url: String,
    val modelFolderName: String,
    val totalBytes: Long,
    val fileTasks: List<PersistentFileTask> = emptyList() // For multi-file downloads
)

@Serializable
private data class PersistentFileTask(
    val path: String,
    val size: Long
)
sealed class DownloadState {
    object Idle : DownloadState()
    object Connecting : DownloadState() // 新增状态，表示正在连接或准备下载
    data class Downloading(
        val progress: Float,
        val speed: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentFile: String = "",
        val currentFileIndex: Int = 0,
        val totalFiles: Int = 1
    ) : DownloadState()
    data class Paused(val progress: Float, val downloadedBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

data class MnnDownloadSnapshot(
    val modelName: String,
    val modelFolder: File,
    val state: DownloadState,
    val expectedBytes: Long?,
)

class MnnModelDownloadManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: MnnModelDownloadManager? = null

        fun getInstance(context: Context): MnnModelDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MnnModelDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        private const val TAG = "MnnModelDownloadMgr"
        private const val MODEL_MARKET_URL = "https://meta.alicdn.com/data/mnn/apis/model_market.json"
        private const val CACHE_FILE_NAME = "mnn_model_market_cache.json"
        private const val PERSISTENT_STATE_FILE_NAME = "mnn_download_states.json"
        private const val MODEL_FOLDER_PREFERENCES = "mnn_model_folders"
        private const val TEMP_SUFFIX = ".tmp"
        
        val MODEL_DIR = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Operit/models/mnn"
        )
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val downloadStates = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val deletingModels = ConcurrentHashMap.newKeySet<String>()
    private var persistentStates = ConcurrentHashMap<String, PersistentDownloadState>()
    private val folderPreferences =
        context.getSharedPreferences(MODEL_FOLDER_PREFERENCES, Context.MODE_PRIVATE)
    private val modelFolderNames = ConcurrentHashMap<String, String>().apply {
        folderPreferences.all.forEach { (modelName, value) ->
            (value as? String)?.let { folderName -> put(modelName, folderName) }
        }
    }
    private val _downloadSnapshots = MutableStateFlow<List<MnnDownloadSnapshot>>(emptyList())

    val downloadSnapshots: StateFlow<List<MnnDownloadSnapshot>> =
        _downloadSnapshots.asStateFlow()

    init {
        if (!MODEL_DIR.exists()) {
            MODEL_DIR.mkdirs()
        }
        publishDownloadSnapshots()
        loadPersistentStates()
    }

    private fun loadPersistentStates() {
        applicationScope.launch {
            val stateFile = File(context.filesDir, PERSISTENT_STATE_FILE_NAME)
            if (!stateFile.exists()) return@launch

            try {
                val jsonString = stateFile.readText()
                if (jsonString.isBlank()) return@launch

                val states = json.decodeFromString<List<PersistentDownloadState>>(jsonString)
                persistentStates = ConcurrentHashMap(states.associateBy { it.modelName })
                states.forEach { state ->
                    rememberModelFolder(state.modelName, state.modelFolderName)
                }
                publishDownloadSnapshots()
                AppLogger.d(TAG, "成功加载 ${states.size} 个持久化下载状态")

                states.forEach { state ->
                    val modelFolder = File(MODEL_DIR, state.modelFolderName)
                    var downloadedBytes = 0L

                    if (state.fileTasks.isNotEmpty()) {
                        // 多文件下载
                        state.fileTasks.forEach { task ->
                            val tempFile = File(modelFolder, "${task.path}$TEMP_SUFFIX")
                            if (tempFile.exists()) {
                                downloadedBytes += tempFile.length()
                            } else {
                                val finalFile = File(modelFolder, task.path)
                                if (finalFile.exists() && finalFile.length() == task.size) {
                                    downloadedBytes += finalFile.length()
                                }
                            }
                        }
                    } else {
                        // 单文件下载
                        val fileName = getFileName(state.modelName)
                        val tempFile = File(modelFolder, "$fileName$TEMP_SUFFIX")
                        if (tempFile.exists()) {
                            downloadedBytes = tempFile.length()
                        }
                    }

                    if (downloadedBytes > 0 && downloadedBytes < state.totalBytes) {
                        val progress = if (state.totalBytes > 0) downloadedBytes.toFloat() / state.totalBytes else 0f
                        updateDownloadState(state.modelName, DownloadState.Paused(progress, downloadedBytes))
                        AppLogger.d(TAG, "恢复 '${state.modelName}' 为暂停状态，进度: ${"%.2f".format(progress * 100)}%")
                    } else if (downloadedBytes >= state.totalBytes && state.totalBytes > 0) {
                        // 发现下载已完成但未清理状态，进行清理
                        removePersistentState(state.modelName)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载持久化状态失败", e)
            }
        }
    }

    private fun savePersistentStates() {
        applicationScope.launch {
            try {
                val stateFile = File(context.filesDir, PERSISTENT_STATE_FILE_NAME)
                val states = persistentStates.values.toList()
                if (states.isEmpty()) {
                    if (stateFile.exists()) stateFile.delete()
                } else {
                    val jsonString = json.encodeToString(
                        ListSerializer(PersistentDownloadState.serializer()),
                        states
                    )
                    stateFile.writeText(jsonString)
                }
                AppLogger.d(TAG, "成功保存 ${states.size} 个持久化下载状态")
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存持久化状态失败", e)
            }
        }
    }

    private fun addPersistentState(state: PersistentDownloadState) {
        persistentStates[state.modelName] = state
        rememberModelFolder(state.modelName, state.modelFolderName)
        savePersistentStates()
    }

    private fun removePersistentState(modelName: String) {
        if (persistentStates.containsKey(modelName)) {
            persistentStates.remove(modelName)
            savePersistentStates()
        }
    }

    private fun rememberModelFolder(modelName: String, folderName: String) {
        modelFolderNames[modelName] = folderName
        folderPreferences.edit().putString(modelName, folderName).apply()
    }

    private fun folderNameFor(modelName: String): String =
        persistentStates[modelName]?.modelFolderName
            ?: modelFolderNames[modelName]
            ?: getLastFileName(modelName)

    private fun modelFolderFor(modelName: String): File =
        File(MODEL_DIR, folderNameFor(modelName))

    private fun publishDownloadSnapshots() {
        val modelNames = (downloadStates.keys + persistentStates.keys + modelFolderNames.keys).toSet()
        _downloadSnapshots.value = modelNames.mapNotNull { modelName ->
            val modelFolder = modelFolderFor(modelName)
            val state = downloadStates[modelName]?.value
                ?: if (modelFolder.isDirectory) {
                    DownloadState.Completed
                } else {
                    DownloadState.Idle
                }
            val relevant =
                persistentStates.containsKey(modelName) ||
                    modelFolder.exists() ||
                    state is DownloadState.Connecting ||
                    state is DownloadState.Downloading ||
                    state is DownloadState.Paused ||
                    state is DownloadState.Failed
            if (!relevant) return@mapNotNull null
            MnnDownloadSnapshot(
                modelName = modelName,
                modelFolder = modelFolder,
                state = state,
                expectedBytes = persistentStates[modelName]?.totalBytes,
            )
        }.sortedBy { it.modelName }
    }
    
    fun getDownloadState(modelName: String): StateFlow<DownloadState> {
        return downloadStates.getOrPut(modelName) {
            val initialState = if (isModelDownloaded(modelName)) {
                DownloadState.Completed
            } else {
                DownloadState.Idle
            }
            MutableStateFlow(initialState)
        }.asStateFlow().also { publishDownloadSnapshots() }
    }
    
    suspend fun fetchModelList(): Result<List<MnnModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(MODEL_MARKET_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext loadFromCache()
                }

                val jsonString = response.body?.string() ?: ""
                val marketData = json.decodeFromString<ModelMarketData>(jsonString)
                saveToCache(jsonString)
                return@withContext Result.success(marketData.models)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取模型列表失败", e)
            val cachedResult = loadFromCache()
            return@withContext if (cachedResult.isSuccess) cachedResult else Result.failure(e)
        }
    }
    
    private fun saveToCache(jsonString: String) {
        try {
            File(context.filesDir, CACHE_FILE_NAME).writeText(jsonString)
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存缓存失败", e)
        }
    }
    
    private fun loadFromCache(): Result<List<MnnModel>> {
        return try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (!cacheFile.exists()) {
                return Result.failure(Exception(context.getString(R.string.mnn_no_cache_data)))
            }
            val jsonString = cacheFile.readText()
            val marketData = json.decodeFromString<ModelMarketData>(jsonString)
            Result.success(marketData.models)
        } catch (e: Exception) {
            AppLogger.e(TAG, "从缓存加载失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取 ModelScope 仓库的文件列表
     * 参考: MsApiService.java:11-15
     */
    private suspend fun fetchRepoFiles(
        modelName: String,
        modelScopeId: String,
    ): Result<List<MsFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val parts = modelScopeId.split("/")
            if (parts.size != 2) {
                return@withContext Result.failure(Exception("Invalid ModelScope ID format: $modelScopeId"))
            }

            val url = "https://modelscope.cn/api/v1/models/${parts[0]}/${parts[1]}/repo/files?Recursive=1"
            AppLogger.d(TAG, "获取文件列表: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                .addHeader("Accept", "application/json")
                .build()
            val call = newTrackedCall(modelName, request)
            try {
                call.execute().use { response ->
                    AppLogger.d(TAG, "响应码: ${response.code}")

                    if (!response.isSuccessful) {
                        val error = "HTTP ${response.code}: ${response.message}"
                        AppLogger.e(TAG, "获取文件列表失败: $error")
                        val body = response.body?.string() ?: ""
                        if (body.isNotEmpty()) {
                            AppLogger.e(TAG, "响应体: $body")
                        }
                        return@withContext Result.failure(Exception(error))
                    }

                    val jsonString = response.body?.string() ?: ""
                    if (jsonString.isEmpty()) {
                        AppLogger.e(TAG, "响应体为空")
                        return@withContext Result.failure(
                            Exception(context.getString(R.string.mnn_response_empty))
                        )
                    }

                    AppLogger.d(TAG, "文件列表响应长度: ${jsonString.length}")
                    AppLogger.d(TAG, "文件列表响应前500字符: ${jsonString.take(500)}")

                    val repoInfo = json.decodeFromString<MsRepoInfo>(jsonString)
                    if (!repoInfo.Success) {
                        return@withContext Result.failure(
                            Exception(repoInfo.Message ?: "Unknown error")
                        )
                    }

                    val files = repoInfo.Data?.Files?.filter { it.Type != "tree" } ?: emptyList()
                    Result.success(files)
                }
            } finally {
                clearTrackedCall(modelName, call)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取文件列表异常: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadModel(modelName: String, url: String) {
        // 立即更新状态，防止重复点击
        val currentState = getDownloadState(modelName).value
        if (currentState is DownloadState.Downloading || currentState is DownloadState.Connecting) {
            AppLogger.w(TAG, "Download for $modelName is already in progress or connecting, ignoring click.")
            return
        }
        updateDownloadState(modelName, DownloadState.Connecting)

        // 先取消可能存在的旧任务
        downloadJobs[modelName]?.cancel()
        AppLogger.d(TAG, "Starting download for $modelName. Any existing job was cancelled.")

        val job = applicationScope.launch {
            try {
                // 重置暂停标志
                pauseFlags[modelName] = false
                
                // 创建模型文件夹（参考 MsModelDownloader.kt:174-176）
                val modelFolderName = getLastFileName(url)
                val modelFolder = File(MODEL_DIR, modelFolderName)
                rememberModelFolder(modelName, modelFolderName)
                
                if (!modelFolder.exists()) {
                    modelFolder.mkdirs()
                }
                
                AppLogger.d(TAG, "========== 开始下载模型 ==========")
                AppLogger.d(TAG, "模型名称: $modelName")
                AppLogger.d(TAG, "源地址: $url")
                AppLogger.d(TAG, "模型文件夹: ${modelFolder.absolutePath}")
                
                // 构建下载URL（参考 MsModelDownloader.kt:244-248）
                val downloadUrl = if (url.startsWith("http")) {
                    url
                } else {
                    // ModelScope仓库格式: owner/repo
                    // 先获取仓库文件列表，找到实际的文件路径
                    val filesResult = fetchRepoFiles(modelName, url)
                    if (filesResult.isFailure) {
                        val error = context.getString(R.string.mnn_fetch_repo_files_failed, filesResult.exceptionOrNull()?.message ?: "")
                        updateDownloadState(modelName, DownloadState.Failed(error))
                        return@launch
                    }
                    
                    val files = filesResult.getOrNull() ?: emptyList()
                    val totalSize = files.sumOf { it.Size }
                    
                    // 持久化状态
                    val persistentTasks = files.map { PersistentFileTask(it.Path!!, it.Size) }
                    addPersistentState(PersistentDownloadState(modelName, url, modelFolderName, totalSize, persistentTasks))

                    AppLogger.d(TAG, "仓库文件列表 (${files.size} 个文件):")
                    files.forEach { file ->
                        AppLogger.d(TAG, "  - ${file.Path} (${file.Size} 字节, ${file.Type})")
                    }
                    
                    // 下载所有非目录文件（参考 MsModelDownloader.kt:236-241）
                    downloadAllFiles(modelName, url, files, modelFolder)
                    return@launch
                }
                
                // 单文件下载（直接URL）
                val fileName = getFileName(modelName)
                val targetFile = File(modelFolder, fileName)
                val tempFile = File(modelFolder, "$fileName$TEMP_SUFFIX")
                
                AppLogger.d(TAG, "目标文件路径: ${targetFile.absolutePath}")
                AppLogger.d(TAG, "临时文件路径: ${tempFile.absolutePath}")
                
                // 获取服务器文件大小
                AppLogger.d(TAG, "发送 HEAD 请求获取文件大小...")
                try {
                    val headRequest = Request.Builder().url(downloadUrl).head().build()
                    val call = newTrackedCall(modelName, headRequest)
                    val headResult = try {
                        call.execute().use { response ->
                            Pair(
                                response.header("Content-Length")?.toLongOrNull() ?: -1L,
                                response.code,
                            )
                        }
                    } finally {
                        clearTrackedCall(modelName, call)
                    }
                    val serverFileSize = headResult.first
                    val responseCode = headResult.second
                    
                    if (serverFileSize > 0) {
                        addPersistentState(PersistentDownloadState(modelName, url, modelFolderName, serverFileSize))
                    }

                    AppLogger.d(TAG, "HEAD 响应码: $responseCode")
                    AppLogger.d(TAG, "服务器文件大小: $serverFileSize 字节 (${formatFileSize(serverFileSize)})")
                    
                    // 检查文件是否已完整下载
                    if (targetFile.exists()) {
                        val localFileSize = targetFile.length()
                        AppLogger.d(TAG, "发现本地文件，大小: $localFileSize 字节 (${formatFileSize(localFileSize)})")
                        
                        if (serverFileSize > 0 && localFileSize == serverFileSize) {
                            AppLogger.d(TAG, "✅ 文件大小匹配，已完整下载，跳过下载")
                            updateDownloadState(modelName, DownloadState.Completed)
                            removePersistentState(modelName)
                            return@launch
                        } else {
                            AppLogger.w(TAG, "❌ 文件大小不匹配！期望: $serverFileSize, 实际: $localFileSize")
                            AppLogger.w(TAG, "删除损坏的文件...")
                            val deleted = targetFile.delete()
                            AppLogger.d(TAG, "删除${if (deleted) "成功" else "失败"}")
                        }
                    } else {
                        AppLogger.d(TAG, "本地文件不存在，需要下载")
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    AppLogger.e(TAG, "HEAD 请求失败: ${e.message}", e)
                    // HEAD 失败不影响继续下载，只是无法验证已存在的文件
                    if (targetFile.exists()) {
                        AppLogger.w(TAG, "无法验证文件完整性，删除后重新下载")
                        targetFile.delete()
                    }
                }
                
                downloadSingleFile(
                    modelName = modelName,
                    downloadUrl = downloadUrl,
                    targetFile = targetFile,
                    tempFile = tempFile,
                )
            } catch (cancellation: CancellationException) {
                AppLogger.d(TAG, "下载已取消: $modelName")
                throw cancellation
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                AppLogger.e(TAG, "模型: $modelName", e)
                AppLogger.e(TAG, "错误: ${e.javaClass.simpleName}: ${e.message}")
                if (!deletingModels.contains(modelName)) {
                    updateDownloadState(
                        modelName,
                        DownloadState.Failed(
                            e.message ?: context.getString(R.string.mnn_unknown_error)
                        ),
                    )
                }
            }
        }
        downloadJobs[modelName] = job
        job.invokeOnCompletion {
            downloadJobs.remove(modelName)
        }
    }

    fun pauseDownload(modelName: String) {
        pauseFlags[modelName] = true
    }

    suspend fun cancelDownload(modelName: String) {
        activeCalls.remove(modelName)?.cancel()
        downloadJobs.remove(modelName)?.cancelAndJoin()
        if (!deletingModels.contains(modelName)) {
            updateDownloadState(modelName, DownloadState.Idle)
        }
    }

    suspend fun deleteModel(modelName: String): LocalModelDeleteOutcome =
        deleteModelFolder(modelFolderFor(modelName), setOf(modelName), null)

    suspend fun deleteModelFolder(
        modelFolder: File,
        onProgress: ((deletedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): LocalModelDeleteOutcome = deleteModelFolder(modelFolder, emptySet(), onProgress)

    private suspend fun deleteModelFolder(
        modelFolder: File,
        additionalModelNames: Set<String>,
        onProgress: ((deletedBytes: Long, totalBytes: Long) -> Unit)?,
    ): LocalModelDeleteOutcome = withContext(Dispatchers.IO) {
        val canonicalTarget = canonicalPath(modelFolder)
        val knownModelNames = downloadStates.keys + persistentStates.keys + modelFolderNames.keys
        val affectedModelNames =
            (knownModelNames.filter { modelName ->
                canonicalPath(modelFolderFor(modelName)) == canonicalTarget
            } + additionalModelNames).toSet()

        if (LocalModelRuntimeRegistry.isInUse(modelFolder)) {
            return@withContext LocalModelDeleteOutcome.IN_USE
        }

        deletingModels.addAll(affectedModelNames)
        try {
            affectedModelNames.forEach { modelName ->
                activeCalls.remove(modelName)?.cancel()
            }
            affectedModelNames.mapNotNull { modelName -> downloadJobs.remove(modelName) }
                .forEach { job -> job.cancelAndJoin() }

            val outcome = LocalModelRuntimeRegistry.deleteIfUnused(modelFolder, onProgress)
            if (outcome == LocalModelDeleteOutcome.DELETED) {
                val preferenceEditor = folderPreferences.edit()
                affectedModelNames.forEach { modelName ->
                    persistentStates.remove(modelName)
                    pauseFlags.remove(modelName)
                    downloadStates.remove(modelName)
                    modelFolderNames.remove(modelName)
                    preferenceEditor.remove(modelName)
                }
                preferenceEditor.apply()
                savePersistentStates()
            } else {
                val fallbackState =
                    if (modelFolder.isDirectory) DownloadState.Completed else DownloadState.Idle
                affectedModelNames.forEach { modelName ->
                    downloadStates[modelName]?.value = fallbackState
                }
            }
            publishDownloadSnapshots()
            outcome
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppLogger.e(TAG, "删除模型失败", error)
            LocalModelDeleteOutcome.FAILED
        } finally {
            deletingModels.removeAll(affectedModelNames)
        }
    }

    fun getDownloadedModels(): List<File> {
        return MODEL_DIR.listFiles { file ->
            file.isDirectory && !file.name.endsWith(TEMP_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun isModelDownloaded(modelName: String): Boolean = modelFolderFor(modelName).isDirectory

    fun isModelInUse(modelName: String): Boolean =
        LocalModelRuntimeRegistry.isInUse(modelFolderFor(modelName))

    private fun canonicalPath(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun newTrackedCall(modelName: String, request: Request): Call {
        val call = okHttpClient.newCall(request)
        activeCalls.put(modelName, call)?.cancel()
        return call
    }

    private fun clearTrackedCall(modelName: String, call: Call) {
        activeCalls.remove(modelName, call)
    }
    
    private fun updateDownloadState(modelName: String, state: DownloadState) {
        val stateFlow = downloadStates.getOrPut(modelName) { MutableStateFlow(state) }
        stateFlow.value = state
        publishDownloadSnapshots()
    }
    
    private fun getFileName(modelName: String): String {
        return if (modelName.endsWith(".mnn")) modelName else "$modelName.mnn"
    }
    
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond < 1024 -> String.format("%.0f B/s", bytesPerSecond)
            bytesPerSecond < 1024 * 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024))
        }
    }
    
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun getLastFileName(path: String): String {
        // 从路径中提取最后一部分作为文件夹名
        // 例如: "moxin-org/moxin-chat-7b" -> "moxin-chat-7b"
        return path.substringAfterLast('/')
    }
    
    private suspend fun downloadSingleFile(
        modelName: String,
        downloadUrl: String,
        targetFile: File,
        tempFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            if (downloadedBytes > 0) {
                AppLogger.d(
                    TAG,
                    "发现临时文件，使用断点续传，已下载: $downloadedBytes 字节 (${formatFileSize(downloadedBytes)})",
                )
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .apply {
                    if (downloadedBytes > 0) {
                        header("Range", "bytes=$downloadedBytes-")
                    }
                }
                .build()
            val call = newTrackedCall(modelName, request)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        val error = context.getString(
                            R.string.mnn_download_failed_http,
                            response.code,
                            response.message,
                        )
                        updateDownloadState(modelName, DownloadState.Failed(error))
                        return@withContext Result.failure(Exception(error))
                    }

                    // A server may ignore Range and return the complete file with HTTP 200.
                    val append = downloadedBytes > 0 && response.code == 206
                    if (!append && downloadedBytes > 0) {
                        tempFile.delete()
                        downloadedBytes = 0L
                    }

                    val contentLength = response.body?.contentLength() ?: 0L
                    val totalBytes = if (response.code == 206) {
                        downloadedBytes + contentLength
                    } else {
                        contentLength
                    }
                    val input = response.body?.byteStream() ?: run {
                        val error = context.getString(R.string.mnn_response_empty)
                        updateDownloadState(modelName, DownloadState.Failed(error))
                        return@withContext Result.failure(Exception(error))
                    }

                    input.use { inputStream ->
                        FileOutputStream(tempFile, append).use { output ->
                            val buffer = ByteArray(8192)
                            var currentDownloaded = downloadedBytes
                            var lastUpdateTime = System.currentTimeMillis()
                            var lastDownloaded = downloadedBytes
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (pauseFlags[modelName] == true) {
                                    val progress = if (totalBytes > 0) {
                                        currentDownloaded.toFloat() / totalBytes
                                    } else {
                                        0f
                                    }
                                    updateDownloadState(
                                        modelName,
                                        DownloadState.Paused(progress, currentDownloaded),
                                    )
                                    return@withContext Result.success(tempFile)
                                }

                                output.write(buffer, 0, bytesRead)
                                currentDownloaded += bytesRead
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime >= 500) {
                                    val seconds = (currentTime - lastUpdateTime) / 1000.0
                                    val speed = formatSpeed(
                                        (currentDownloaded - lastDownloaded) / seconds
                                    )
                                    val progress = if (totalBytes > 0) {
                                        currentDownloaded.toFloat() / totalBytes
                                    } else {
                                        0f
                                    }
                                    updateDownloadState(
                                        modelName,
                                        DownloadState.Downloading(
                                            progress = progress,
                                            speed = speed,
                                            downloadedBytes = currentDownloaded,
                                            totalBytes = totalBytes,
                                        ),
                                    )
                                    lastUpdateTime = currentTime
                                    lastDownloaded = currentDownloaded
                                }
                            }
                        }
                    }
                }
            } finally {
                clearTrackedCall(modelName, call)
            }

            if (targetFile.exists() && !targetFile.delete()) {
                val error = context.getString(R.string.mnn_rename_failed)
                updateDownloadState(modelName, DownloadState.Failed(error))
                return@withContext Result.failure(Exception(error))
            }
            if (!tempFile.renameTo(targetFile)) {
                val error = context.getString(R.string.mnn_rename_failed)
                updateDownloadState(modelName, DownloadState.Failed(error))
                return@withContext Result.failure(Exception(error))
            }

            updateDownloadState(modelName, DownloadState.Completed)
            removePersistentState(modelName)
            Result.success(targetFile)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!deletingModels.contains(modelName)) {
                updateDownloadState(
                    modelName,
                    DownloadState.Failed(
                        error.message ?: context.getString(R.string.mnn_unknown_error)
                    ),
                )
            }
            Result.failure(error)
        }
    }

    /**
     * 下载仓库中的所有文件到模型文件夹
     * 参考 MsModelDownloader.kt:162-219
     */
    private suspend fun downloadAllFiles(
        modelName: String,
        repositoryPath: String,
        files: List<MsFileInfo>,
        modelFolder: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 计算总大小
            var totalBytes = 0L
            val downloadTasks = mutableListOf<Pair<MsFileInfo, File>>()
            
            for (file in files) {
                if (file.Type == "tree") continue // 跳过目录
                
                val targetFile = File(modelFolder, file.Path ?: continue)
                targetFile.parentFile?.mkdirs()
                
                downloadTasks.add(file to targetFile)
                totalBytes += file.Size
            }
            
            AppLogger.d(TAG, "准备下载 ${downloadTasks.size} 个文件，总大小: ${formatFileSize(totalBytes)}")
            
            // 下载每个文件
            var downloadedBytes = 0L
            var currentFileIndex = 0
            
            for ((fileInfo, targetFile) in downloadTasks) {
                currentFileIndex++
                val fileName = fileInfo.Path ?: continue
                
                AppLogger.d(TAG, "下载文件 [$currentFileIndex/${downloadTasks.size}]: $fileName")
                
                // 如果文件已存在且大小匹配，跳过
                if (targetFile.exists() && targetFile.length() == fileInfo.Size) {
                    AppLogger.d(TAG, "文件已存在，跳过: $fileName")
                    downloadedBytes += fileInfo.Size
                    
                    val progress = downloadedBytes.toFloat() / totalBytes
                    updateDownloadState(
                        modelName,
                        DownloadState.Downloading(
                            progress = progress,
                            speed = "0 B/s",
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            currentFile = fileName,
                            currentFileIndex = currentFileIndex,
                            totalFiles = downloadTasks.size
                        )
                    )
                    continue
                }
                
                // 构建下载URL
                val downloadUrl = String.format(
                    "https://modelscope.cn/api/v1/models/%s/repo?FilePath=%s",
                    repositoryPath,
                    fileName
                )
                
                val tempFile = File(targetFile.parentFile, "${targetFile.name}$TEMP_SUFFIX")
                
                // 断点续传支持
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                downloadedBytes += existingBytes
                
                val request = Request.Builder()
                    .url(downloadUrl)
                    .apply {
                        if (existingBytes > 0) {
                            addHeader("Range", "bytes=$existingBytes-")
                        }
                    }
                    .build()
                
                val call = newTrackedCall(modelName, request)
                try {
                    val response = call.execute()
                    response.use { currentResponse ->
                        if (!currentResponse.isSuccessful && currentResponse.code != 206) {
                            val error = context.getString(
                                R.string.mnn_download_file_failed,
                                fileName,
                                currentResponse.code,
                            )
                            updateDownloadState(modelName, DownloadState.Failed(error))
                            return@withContext Result.failure(Exception(error))
                        }

                        val append = existingBytes > 0 && currentResponse.code == 206
                        if (!append && existingBytes > 0) {
                            tempFile.delete()
                            downloadedBytes -= existingBytes
                        }

                        val input = currentResponse.body?.byteStream()
                            ?: return@withContext Result.failure(
                                Exception(context.getString(R.string.mnn_response_empty))
                            )
                        input.use {
                            FileOutputStream(tempFile, append).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var lastUpdateTime = System.currentTimeMillis()
                                var lastDownloadedBytes = downloadedBytes

                                while (it.read(buffer).also { read -> bytesRead = read } != -1) {
                                    if (pauseFlags[modelName] == true) {
                                        val progress = if (totalBytes > 0) {
                                            downloadedBytes.toFloat() / totalBytes
                                        } else {
                                            0f
                                        }
                                        updateDownloadState(
                                            modelName,
                                            DownloadState.Paused(progress, downloadedBytes),
                                        )
                                        return@withContext Result.success(modelFolder)
                                    }

                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastUpdateTime >= 500) {
                                        val deltaTime = (currentTime - lastUpdateTime) / 1000.0
                                        val deltaBytes = downloadedBytes - lastDownloadedBytes
                                        val speed = formatSpeed(deltaBytes / deltaTime)
                                        val progress = downloadedBytes.toFloat() / totalBytes

                                        updateDownloadState(
                                            modelName,
                                            DownloadState.Downloading(
                                                progress = progress,
                                                speed = speed,
                                                downloadedBytes = downloadedBytes,
                                                totalBytes = totalBytes,
                                                currentFile = fileName,
                                                currentFileIndex = currentFileIndex,
                                                totalFiles = downloadTasks.size,
                                            ),
                                        )

                                        lastUpdateTime = currentTime
                                        lastDownloadedBytes = downloadedBytes
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    clearTrackedCall(modelName, call)
                }

                if (tempFile.exists() && !tempFile.renameTo(targetFile)) {
                    val error = context.getString(R.string.mnn_rename_failed)
                    updateDownloadState(modelName, DownloadState.Failed(error))
                    return@withContext Result.failure(Exception(error))
                }

                AppLogger.d(TAG, "文件下载完成: $fileName")
            }
            
            AppLogger.d(TAG, "所有文件下载完成！模型文件夹: ${modelFolder.absolutePath}")
            updateDownloadState(modelName, DownloadState.Completed)
            removePersistentState(modelName)
            
            Result.success(modelFolder)
        } catch (cancellation: CancellationException) {
            AppLogger.d(TAG, "多文件下载已取消: $modelName")
            throw cancellation
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (!deletingModels.contains(modelName)) {
                updateDownloadState(
                    modelName,
                    DownloadState.Failed(
                        e.message ?: context.getString(R.string.mnn_unknown_error)
                    ),
                )
            }
            Result.failure(e)
        }
    }
}

