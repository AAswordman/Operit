package com.ai.assistance.operit.ui.common.displays

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ai.assistance.operit.util.logging.AppLogger
import android.util.LruCache
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Cache for LaTeX drawable objects to improve rendering performance. This class implements an LRU
 * (Least Recently Used) cache for LaTeX formulas to avoid re-rendering the same formulas multiple
 * times.
 */
object LatexCache {
    // Cache size: 50 entries should be enough for most use cases
    private const val CACHE_SIZE = 50

    // 计算应用可用内存的大小
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    // 使用可用内存的1/8作为图片缓存
    private val BITMAP_CACHE_SIZE = maxMemory / 8

    // LRU cache for LaTeX drawables
    private val cache = LruCache<String, JLatexMathDrawable>(CACHE_SIZE)

    // LRU cache for bitmap representations of rendered LaTeX
    private val bitmapCache =
            object : LruCache<String, Bitmap>(BITMAP_CACHE_SIZE) {
                override fun sizeOf(key: String, bitmap: Bitmap): Int {
                    // 返回bitmap的大小（KB为单位）
                    return bitmap.byteCount / 1024
                }

                override fun entryRemoved(
                        evicted: Boolean,
                        key: String,
                        oldValue: Bitmap,
                        newValue: Bitmap?
                ) {
                    // 如果Bitmap被移出缓存，确保回收内存
                    if (evicted && newValue == null && !oldValue.isRecycled) {
                        oldValue.recycle()
                    }
                }
            }

    /**
     * Get a cached LaTeX drawable or create and cache a new one
     *
     * @param formula The LaTeX formula to render
     * @param builder A configured builder for creating the drawable if not cached
     * @return The cached or newly created JLatexMathDrawable
     */
    @Synchronized
    fun getDrawable(formula: String, builder: JLatexMathDrawable.Builder): JLatexMathDrawable {
        JLatexMathCompatibility.ensureRegistered()

        // Create a cache key that includes the formula and rendering properties
        val cacheKey = buildCacheKey(formula, builder)

        // Try to get from cache
        var drawable = cache.get(cacheKey)

        if (drawable == null) {
            // Not in cache, create new drawable
            drawable = builder.build()

            // Store in cache
            cache.put(cacheKey, drawable)
            AppLogger.d(
                    "LatexCache",
                    "Cache miss for formula: ${formula.take(20)}${if (formula.length > 20) "..." else ""}"
            )
        } else {
            AppLogger.d(
                    "LatexCache",
                    "Cache hit for formula: ${formula.take(20)}${if (formula.length > 20) "..." else ""}"
            )
        }

        return drawable
    }

    /**
     * Get a cached bitmap of rendered LaTeX or create and cache a new one
     *
     * @param formula The LaTeX formula to render
     * @param builder A configured builder for creating the drawable if bitmap not cached
     * @return The cached or newly created Bitmap
     */
    @Synchronized
    fun getLatexBitmap(formula: String, builder: JLatexMathDrawable.Builder): Bitmap {
        val cacheKey = "bitmap_${buildCacheKey(formula, builder)}"

        // Try to get from bitmap cache first
        var bitmap = bitmapCache.get(cacheKey)

        if (bitmap == null) {
            AppLogger.d(
                    "LatexCache",
                    "Bitmap cache miss, rendering: ${formula.take(20)}${if (formula.length > 20) "..." else ""}"
            )

            // Get the drawable (from cache or create new)
            val drawable = getDrawable(formula, builder)

            // Render drawable to bitmap
            bitmap = renderToBitmap(drawable)

            // Store in bitmap cache
            bitmapCache.put(cacheKey, bitmap)
        } else {
            AppLogger.d(
                    "LatexCache",
                    "Bitmap cache hit: ${formula.take(20)}${if (formula.length > 20) "..." else ""}"
            )
        }

        return bitmap
    }

    /**
     * Renders a LaTeX drawable to bitmap for faster display
     *
     * @param drawable The JLatexMathDrawable to render
     * @return A bitmap representation of the rendered LaTeX
     */
    private fun renderToBitmap(drawable: JLatexMathDrawable): Bitmap {
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight

        // Make sure we have valid dimensions
        val safeWidth = if (width <= 0) 1 else width
        val safeHeight = if (height <= 0) 1 else height

        // Create bitmap with optimal configuration for text
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)

        // Create canvas and draw the drawable
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, safeWidth, safeHeight)
        drawable.draw(canvas)

        return bitmap
    }

    /** Clear the entire cache */
    fun clearCache() {
        cache.evictAll()

        // Also clear bitmap cache
        val bitmapCount = bitmapCache.size()
        bitmapCache.evictAll()

        AppLogger.d("LatexCache", "All caches cleared. Freed ${bitmapCount} bitmaps")
    }

    /** Build a cache key that uniquely identifies a formula with its styling parameters */
    private fun buildCacheKey(formula: String, builder: JLatexMathDrawable.Builder): String {
        // We need to create a cache key that includes both the formula
        // and important styling parameters to ensure correct rendering

        // Extract key properties from builder if possible using reflection
        val builderClass = builder.javaClass

        // Default cache key components
        var textSize = 0f
        var textColor = 0
        var align = 0
        var padding = 0
        var background = 0

        try {
            // 工具函数：安全获取字段值
            fun safeGetField(fieldName: String) {
                try {
                    builderClass.getDeclaredField(fieldName).apply {
                        isAccessible = true
                        when (fieldName) {
                            "textSize" -> textSize = 
                                try { getFloat(builder) } 
                                catch (e: Exception) { get(builder)?.hashCode()?.toFloat() ?: 0f }
                            "color" -> textColor = 
                                try { getInt(builder) } 
                                catch (e: Exception) { get(builder)?.hashCode() ?: 0 }
                            "align" -> align = 
                                try { getInt(builder) } 
                                catch (e: Exception) { get(builder)?.hashCode() ?: 0 }
                            "padding" -> padding = 
                                try { getInt(builder) } 
                                catch (e: Exception) { get(builder)?.hashCode() ?: 0 }
                            "background" -> background = 
                                try { getInt(builder) } 
                                catch (e: Exception) { get(builder)?.hashCode() ?: 0 }
                        }
                    }
                } catch (e: NoSuchFieldException) {
                    // 字段不存在，忽略
                } catch (e: Exception) {
                    // 其他异常，使用日志但继续执行
                    AppLogger.d("LatexCache", "Error accessing $fieldName: ${e.message}")
                }
            }
            
            // 获取所有需要的字段
            safeGetField("textSize")
            safeGetField("color")
            safeGetField("align") 
            safeGetField("padding")
            safeGetField("background")
            
        } catch (e: Exception) {
            AppLogger.w("LatexCache", "Error accessing builder properties: ${e.message}")
            // Fallback to formula-only key if reflection fails
            return formula
        }

        // Combine all parameters into a single key
        return "${formula.hashCode()}:$textSize:$textColor:$align:$padding:$background"
    }

    /**
     * Get cache statistics
     * @return A string with cache hit count, miss count, etc.
     */
    fun getStats(): String {
        return "Drawable cache: size=${cache.size()}, " +
                "max=${cache.maxSize()}, " +
                "hits=${cache.hitCount()}, " +
                "misses=${cache.missCount()} | " +
                "Bitmap cache: size=${bitmapCache.size()}, " +
                "max=${bitmapCache.maxSize()}, " +
                "hits=${bitmapCache.hitCount()}, " +
                "misses=${bitmapCache.missCount()}"
    }
}
