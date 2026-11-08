package com.ai.assistance.operit.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.ai.assistance.operit.util.logging.AppLogger

object AssetCopyUtils {
    fun copyAssetToCache(
        context: Context,
        assetPath: String,
        overwrite: Boolean = false
    ): File {
        val cacheFile = File(context.cacheDir, assetPath)
        return copyAssetToFile(context, assetPath, cacheFile, overwrite)
    }

    fun copyAssetToFile(
        context: Context,
        assetPath: String,
        outputFile: File,
        overwrite: Boolean = false
    ): File {
        if (outputFile.exists()) {
            if (!overwrite) {
                return outputFile
            }
            outputFile.delete()
        }

        val parent = outputFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        context.assets.open(assetPath).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return outputFile
    }

    @Throws(IOException::class)
    fun copyAssetDirToCache(
        context: Context,
        assetDir: String,
        targetDir: File,
        overwrite: Boolean = false
    ): File {
        if (!overwrite && targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            AppLogger.d("AssetCopyUtils", "Assets already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }

        targetDir.mkdirs()
        val assetManager = context.assets
        val fileList = assetManager.list(assetDir)
        if (fileList.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        fileList.forEach { fileName ->
            val assetPath = "$assetDir/$fileName"
            val targetFile = File(targetDir, fileName)
            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        return targetDir
    }

    @Throws(IOException::class)
    fun copyAssetDirRecursive(
        context: Context,
        assetDir: String,
        targetDir: File,
        overwrite: Boolean = false
    ): File {
        if (overwrite && targetDir.exists()) {
            targetDir.deleteRecursively()
        } else if (!overwrite && targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            AppLogger.d("AssetCopyUtils", "Assets already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Failed to create target directory: ${targetDir.absolutePath}")
        }

        val assetManager = context.assets
        val entries = assetManager.list(assetDir)
        if (entries.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        entries.forEach { entry ->
            val assetPath = "$assetDir/$entry"
            val outputFile = File(targetDir, entry)
            val children = assetManager.list(assetPath)
            if (!children.isNullOrEmpty()) {
                copyAssetDirRecursive(context, assetPath, outputFile, overwrite = true)
            } else {
                assetManager.open(assetPath).use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }

        return targetDir
    }
}
