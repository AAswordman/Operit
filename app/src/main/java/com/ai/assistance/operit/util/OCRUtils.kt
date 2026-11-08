package com.ai.assistance.operit.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.annotation.WorkerThread
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** OCR工具类，封装ML Kit的文本识别功能 */
object OCRUtils {
    private const val TAG = "OCRUtils"

    // 识别器缓存
    private var latinRecognizer: TextRecognizer? = null
    private var chineseRecognizer: TextRecognizer? = null
    private var japaneseRecognizer: TextRecognizer? = null
    private var koreanRecognizer: TextRecognizer? = null

    /** 文本识别语言选项 */
    enum class Language {
        LATIN, // 拉丁语系（英文、法文、德文等）
        CHINESE, // 中文
        JAPANESE, // 日文
        KOREAN // 韩文
    }

    /** 识别质量选项 */
    enum class Quality {
        /** 快速、低精度，适用于预览或简单文本 */
        LOW,
        /** 慢速、高精度，会进行图像预处理以提升准确率 */
        HIGH
    }

    /** 初始化识别器 */
    @Synchronized
    private fun getRecognizer(language: Language): TextRecognizer {
        return when (language) {
            Language.LATIN -> {
                if (latinRecognizer == null) {
                    latinRecognizer =
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                }
                latinRecognizer!!
            }
            Language.CHINESE -> {
                if (chineseRecognizer == null) {
                    chineseRecognizer =
                            TextRecognition.getClient(
                                    ChineseTextRecognizerOptions.Builder().build()
                            )
                }
                chineseRecognizer!!
            }
            Language.JAPANESE -> {
                if (japaneseRecognizer == null) {
                    japaneseRecognizer =
                            TextRecognition.getClient(
                                    JapaneseTextRecognizerOptions.Builder().build()
                            )
                }
                japaneseRecognizer!!
            }
            Language.KOREAN -> {
                if (koreanRecognizer == null) {
                    koreanRecognizer =
                            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                }
                koreanRecognizer!!
            }
        }
    }

    /**
     * 对Bitmap进行预处理以提高OCR识别率
     *
     * @param bitmap 原始Bitmap
     * @return 经过处理的Bitmap，如果无需处理则返回原始Bitmap
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // 对于高质量模式，我们放大图像。这可以显著提高小图像的OCR准确性。
        // 我们使用一个缩放因子，但避免使图像过大。
        val scaleFactor = 2.0f
        val maxDimension = 4096 // 限制最大尺寸以避免OOM

        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        // 如果图像已经足够大或放大后会超出限制，则不进行处理
        if (bitmap.width >= newWidth ||
                        bitmap.height >= newHeight ||
                        newWidth > maxDimension ||
                        newHeight > maxDimension
        ) {
            AppLogger.d(TAG, "Bitmap already large enough, not upscaling for OCR.")
            return bitmap
        }

        AppLogger.d(
                TAG,
                "Upscaling bitmap from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight} for OCR."
        )
        return Bitmap.createScaledBitmap(
                bitmap,
                newWidth,
                newHeight,
                true // filter=true for better quality scaling
        )
    }

    // --------- 低级API：直接返回OCRResult结果 ---------//

    /**
     * 从Bitmap识别文本（同步方法）
     *
     * @param bitmap 要识别的图像
     * @param language 识别语言
     * @param quality 识别质量
     * @return 识别结果
     */
    @WorkerThread
    suspend fun recognizeTextFromBitmap(
            bitmap: Bitmap,
            language: Language = Language.LATIN,
            quality: Quality = Quality.LOW
    ): OCRResult {
        val processedBitmap =
                if (quality == Quality.HIGH) {
                    preprocessBitmap(bitmap)
                } else {
                    bitmap
                }

        return try {
            val image = InputImage.fromBitmap(processedBitmap, 0)
            val result = processImage(image, language)
            OCRResult.Success(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error recognizing text from bitmap: ${e.message}", e)
            OCRResult.Error(e.message ?: "Unknown error")
        } finally {
            // 如果创建了新的Bitmap，则回收它
            if (processedBitmap !== bitmap) {
                processedBitmap.recycle()
            }
        }
    }

    /**
     * 从Uri识别文本（同步方法）
     *
     * @param context 上下文
     * @param uri 图像的Uri
     * @param language 识别语言
     * @param quality 识别质量
     * @return 识别结果
     */
    @WorkerThread
    suspend fun recognizeTextFromUri(
            context: Context,
            uri: Uri,
            language: Language = Language.LATIN,
            quality: Quality = Quality.LOW
    ): OCRResult {
        // 低质量模式直接使用MLKit的API，效率更高
        if (quality == Quality.LOW) {
            return try {
                val image = InputImage.fromFilePath(context, uri)
                val result = processImage(image, language)
                OCRResult.Success(result)
            } catch (e: IOException) {
                AppLogger.e(TAG, "Error reading image: ${e.message}", e)
                OCRResult.Error(context.getString(R.string.ocr_cannot_read_image, e.message))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error recognizing text from uri: ${e.message}", e)
                OCRResult.Error(e.message ?: "Unknown error")
            }
        }

        // 高质量模式需要先加载Bitmap进行预处理
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    if (originalBitmap != null) {
                        val result = recognizeTextFromBitmap(originalBitmap, language, quality)
                        originalBitmap.recycle() // 回收从输入流创建的Bitmap
                        result
                    } else {
                        OCRResult.Error(context.getString(R.string.ocr_cannot_decode_bitmap_from_uri))
                    }
                }
                        ?: OCRResult.Error(context.getString(R.string.ocr_cannot_open_uri_stream))
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error recognizing text from uri (high quality): ${e.message}", e)
                OCRResult.Error(e.message ?: "Unknown error on high quality path")
            }
        }
    }

    /** 处理图像并识别文本 */
    private suspend fun processImage(image: InputImage, language: Language): Text = withContext(Dispatchers.IO) {
        com.ai.assistance.operit.data.resources.ocr.OcrModelResources.requireAvailable(com.ai.assistance.operit.core.application.OperitApplication.instance)
        suspendCancellableCoroutine { continuation ->
            getRecognizer(language).process(image)
                .addOnSuccessListener { text -> if (continuation.isActive) continuation.resume(text) }
                .addOnFailureListener { failure ->
                    AppLogger.e(TAG, "文字识别失败", failure)
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
        }
    }
    // --------- 高级API：简化的接口，直接返回文本字符串 ---------//

    /**
     * 识别图像中的文本（同时识别拉丁文和中文）
     *
     * @param context 上下文
     * @param bitmap 图像
     * @param quality 识别质量
     * @param failOnError 工具读取时启用，识别异常抛出而不伪装成空识别结果
     * @return 识别到的文本
     */
    @WorkerThread
    suspend fun recognizeText(
            context: Context,
            bitmap: Bitmap,
            quality: Quality = Quality.LOW,
            failOnError: Boolean = false,
    ): String {
        withContext(Dispatchers.IO) { com.ai.assistance.operit.data.resources.ocr.OcrModelResources.requireAvailable(context) }
        // 同时进行拉丁文和中文识别
        val latinResult = recognizeTextFromBitmap(bitmap, Language.LATIN, quality)
        val chineseResult = recognizeTextFromBitmap(bitmap, Language.CHINESE, quality)

        if (failOnError) {
            val errors = listOf(latinResult, chineseResult).filterIsInstance<OCRResult.Error>()
                .map { it.message }.distinct()
            check(errors.isEmpty()) { errors.joinToString("\n") }
        }
        val latinText = if (latinResult is OCRResult.Success) latinResult.getFullText() else ""
        val chineseText = if (chineseResult is OCRResult.Success) chineseResult.getFullText() else ""

        // 合并结果，如果两种结果相同或其中一个为空，则直接返回非空结果
        return when {
            latinText.isEmpty() -> chineseText
            chineseText.isEmpty() -> latinText
            latinText == chineseText -> latinText
            else -> "$latinText\n$chineseText" // 不同结果合并返回
        }
    }

    /**
     * 识别图像中的文本（指定语言）
     *
     * @param context 上下文
     * @param bitmap 图像
     * @param language 语言
     * @param quality 识别质量
     * @return 识别到的文本，如果失败则返回空字符串
     */
    @WorkerThread
    suspend fun recognizeText(
            context: Context,
            bitmap: Bitmap,
            language: Language,
            quality: Quality = Quality.LOW
    ): String {
        withContext(Dispatchers.IO) { com.ai.assistance.operit.data.resources.ocr.OcrModelResources.requireAvailable(context) }
        val result = recognizeTextFromBitmap(bitmap, language, quality)
        return when (result) {
            is OCRResult.Success -> result.getFullText()
            is OCRResult.Error -> {
                AppLogger.e(TAG, "Text recognition failed: ${result.message}")
                ""
            }
        }
    }

    /**
     * 从文件Uri识别文本（同时识别拉丁文和中文）
     *
     * @param context 上下文
     * @param uri 图像Uri
     * @param quality 识别质量
     * @return 识别到的文本，如果失败则返回空字符串
     */
    @WorkerThread
    suspend fun recognizeText(context: Context, uri: Uri, quality: Quality = Quality.LOW): String {
        withContext(Dispatchers.IO) { com.ai.assistance.operit.data.resources.ocr.OcrModelResources.requireAvailable(context) }
        // 同时进行拉丁文和中文识别
        val latinResult = recognizeTextFromUri(context, uri, Language.LATIN, quality)
        val chineseResult = recognizeTextFromUri(context, uri, Language.CHINESE, quality)

        val latinText = if (latinResult is OCRResult.Success) latinResult.getFullText() else ""
        val chineseText = if (chineseResult is OCRResult.Success) chineseResult.getFullText() else ""

        // 合并结果，如果两种结果相同或其中一个为空，则直接返回非空结果
        return when {
            latinText.isEmpty() -> chineseText
            chineseText.isEmpty() -> latinText
            latinText == chineseText -> latinText
            else -> "$latinText\n$chineseText" // 不同结果合并返回
        }
    }

    /**
     * 扫描并提取图像中所有的文本块
     *
     * @param bitmap 要识别的图像
     * @param languages 要尝试的语言列表，按优先级排序
     * @param quality 识别质量
     * @return 识别到的所有文本块列表
     */
    @WorkerThread
    suspend fun extractTextBlocks(
            bitmap: Bitmap,
            languages: List<Language> = listOf(Language.LATIN, Language.CHINESE),
            quality: Quality = Quality.LOW
    ): List<String> {
        val textBlocks = mutableListOf<String>()

        for (language in languages) {
            val result = recognizeTextFromBitmap(bitmap, language, quality)
            if (result is OCRResult.Success) {
                result.getTextBlocks().forEach { block -> textBlocks.add(block.text) }
                // 如果有结果，就不需要继续尝试其他语言
                if (textBlocks.isNotEmpty()) {
                    break
                }
            }
        }

        return textBlocks
    }

    /**
     * 保存Bitmap到临时文件，用于OCR处理
     *
     * @param context 上下文
     * @param bitmap 位图
     * @return 临时文件
     */
    @WorkerThread
    suspend fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): File? =
            withContext(Dispatchers.IO) {
                val cacheDir = context.cacheDir
                val tempFile = File(cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")

                try {
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    return@withContext tempFile
                } catch (e: IOException) {
                    AppLogger.e(TAG, "Failed to save bitmap to temp file", e)
                    return@withContext null
                }
            }

    /** 清理资源 */
    fun closeRecognizers() {
        latinRecognizer?.close()
        latinRecognizer = null

        chineseRecognizer?.close()
        chineseRecognizer = null

        japaneseRecognizer?.close()
        japaneseRecognizer = null

        koreanRecognizer?.close()
        koreanRecognizer = null
    }

    /** OCR识别结果 */
    sealed class OCRResult {
        /** 识别成功 */
        data class Success(val text: Text) : OCRResult() {
            /** 获取识别到的完整文本 */
            fun getFullText(): String = text.text

            /** 获取所有文本块 */
            fun getTextBlocks(): List<Text.TextBlock> = text.textBlocks

            /** 获取结构化文本信息 */
            fun getStructuredText(): String {
                val sb = StringBuilder()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        sb.append(line.text).append("\n")
                    }
                    sb.append("\n")
                }
                return sb.toString().trim()
            }
        }

        /** 识别失败 */
        data class Error(val message: String) : OCRResult()
    }
}
