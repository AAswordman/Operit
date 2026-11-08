package com.ai.assistance.operit.data.resources.ocr

import android.content.Context
import com.ai.assistance.operit.data.resources.packs.PackResources
import com.ai.assistance.operit.data.storage.LocalModelRuntimeRegistry
import com.ai.assistance.operit.data.storage.LocalModelUsageHandle
import java.io.File

/** 将固定摘要校验过的模型 ZIP 接入 Android 资产空间，保持 ML Kit 原有模型路径。 */
object OcrModelResources {
    private val lock = Any()
    private var loaded = false
    private var usage: LocalModelUsageHandle? = null
    private var retainedLoader: Any? = null

    fun requireAvailable(context: Context) = synchronized(lock) {
        if (loaded || PackResources.isBundled(context, PackResources.OCR_MODELS)) return@synchronized
        val app = context.applicationContext
        val root = PackResources.requireDirectory(app, PackResources.OCR_MODELS)
        val hold = LocalModelRuntimeRegistry.acquire(root) ?: error("OCR 模型正在移除")
        try {
            val assetPackage = File(root, "mlkit-models.apk")
            retainedLoader = AndroidModelAssetLoader.attach(app, assetPackage)
            // ML Kit 原生层可能缓存模型映射，引用保持到进程结束，避免运行中删除资产。
            usage = hold
            loaded = true
        } catch (failure: Throwable) {
            hold.close()
            throw IllegalStateException("无法加载 OCR 模型，请检查资源或重启应用", failure)
        }
    }
}
