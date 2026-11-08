package com.ai.assistance.operit.data.resources.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import java.io.File

/** 模型资产保留原始路径，独立资源包不替换应用内的资源标识。 */
internal object AndroidModelAssetLoader {
    @SuppressLint("DiscouragedPrivateApi")
    fun attach(context: Context, assetPackage: File): Any {
        if (Build.VERSION.SDK_INT >= 30) return ModernAssets.attach(context, assetPackage)
        // Android 8～10 没有 ResourcesLoader；只在这些版本使用既有资产路径接口。
        val method = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
        val cookie = method.invoke(context.assets, assetPackage.absolutePath) as Int
        check(cookie != 0) { "设备无法加载 OCR 模型资产" }
        return cookie
    }
    @RequiresApi(30)
    private object ModernAssets {
        fun attach(context: Context, apk: File): Any {
            val provider = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use {
                android.content.res.loader.ResourcesProvider.loadFromApk(it)
            }
            val loader = android.content.res.loader.ResourcesLoader()
            try {
                loader.addProvider(provider)
                context.resources.addLoaders(loader)
                return loader
            } catch (failure: Throwable) { provider.close(); throw failure }
        }
    }
}
