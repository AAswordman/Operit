package com.ai.assistance.operit.ui.features.conversation.chat.modules.attachments.images

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal enum class ImageLibraryAccess {
    NONE,
    SELECTED,
    FULL,
}

internal data class GalleryImage(
    val uri: Uri,
    val displayName: String?,
    val albumId: String?,
    val albumName: String?,
)

internal fun imageLibraryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun imageLibraryAccess(context: Context): ImageLibraryAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
        return ImageLibraryAccess.FULL
    }
    val fullPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (granted(fullPermission)) return ImageLibraryAccess.FULL
    if (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
        return ImageLibraryAccess.SELECTED
    }
    return ImageLibraryAccess.NONE
}

/** 媒体元数据在 IO 线程读取；缩略图由可见网格项按需加载。 */
internal suspend fun loadGalleryImages(context: Context): List<GalleryImage> = withContext(Dispatchers.IO) {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )
    val cursor = context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
    ) ?: throw IOException("MediaStore 未返回图片查询结果")
    cursor.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val albumNameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        buildList {
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                add(
                    GalleryImage(
                        uri = ContentUris.withAppendedId(collection, it.getLong(idColumn)),
                        displayName = it.getString(nameColumn),
                        albumId = it.getString(albumIdColumn),
                        albumName = it.getString(albumNameColumn),
                    )
                )
            }
        }
    }
}