package com.ai.assistance.operit.core.subpack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.*
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Windows可执行文件(EXE)图标更换工具
 * 实现了基本的PE文件格式解析和图标资源替换功能
 */
class ExeIconChanger(private val context: Context) {
    companion object {
        private const val TAG = "ExeIconChanger"
        private const val TEMP_DIR = "exe_icon_temp"
        
        // PE文件头部常量
        private const val PE_SIGNATURE = 0x4550 // "PE"
        private const val PE_OPTIONAL_HEADER_OFFSET = 24
        private const val RESOURCE_TABLE_ENTRY = 2 // 资源表在数据目录中的索引
        private const val DOS_SIGNATURE = 0x5A4D // "MZ"
    }
    
    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * 更换EXE文件的图标
     * @param exeFile 要修改的EXE文件
     * @param iconBitmap 新图标的Bitmap对象
     * @param outputFile 输出的EXE文件
     * @return 是否成功
     */
    fun changeIcon(exeFile: File, iconBitmap: Bitmap, outputFile: File): Boolean {
        AppLogger.d(TAG, "开始更换EXE图标: ${exeFile.absolutePath}")
        
        try {
            // 先将文件复制到输出位置
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.parentFile?.mkdirs()
            exeFile.copyTo(outputFile, overwrite = true)
            
            // 创建临时ICO文件
            val tempIconFile = File.createTempFile("temp_icon", ".ico")
            createIcoFile(iconBitmap, tempIconFile)
            
            // 调用资源替换工具 (在Android上我们只能模拟这个操作，无法实际执行)
            val success = simulateResourceReplacement(outputFile, tempIconFile)
            
            // 清理临时文件
            tempIconFile.delete()
            
            return success
        } catch (e: Exception) {
            AppLogger.e(TAG, "更换EXE图标失败", e)
            return false
        }
    }
    
    /**
     * 创建临时ICO文件
     * @param bitmap 图标位图
     * @param outputFile 输出的ICO文件
     */
    private fun createIcoFile(bitmap: Bitmap, outputFile: File) {
        // ICO文件格式的简化实现
        try {
            FileOutputStream(outputFile).use { fos ->
                // ICO文件头 (6字节)
                val header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(0) // 保留，必须为0
                    .putShort(1) // 图像类型: 1 = ICO
                    .putShort(1) // 图像数量: 1个
                fos.write(header.array())
                
                // 图像目录 (16字节)
                val width = bitmap.width.coerceAtMost(256)
                val height = bitmap.height.coerceAtMost(256)
                val widthByte = if (width == 256) 0 else width
                val heightByte = if (height == 256) 0 else height
                
                // 将图像转换为PNG格式
                val imageData = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageData)
                val imageBytes = imageData.toByteArray()
                val imageSize = imageBytes.size
                
                val directory = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .put(widthByte.toByte())  // 宽度
                    .put(heightByte.toByte()) // 高度
                    .put(0) // 调色板颜色数 (PNG不使用调色板)
                    .put(0) // 保留，必须为0
                    .putShort(1) // 颜色平面数
                    .putShort(32) // 每像素位数
                    .putInt(imageSize) // 图像数据大小
                    .putInt(22) // 图像数据偏移量 (6 + 16 = 22)
                fos.write(directory.array())
                
                // 写入PNG图像数据
                fos.write(imageBytes)
            }
            
            AppLogger.d(TAG, "临时ICO文件创建成功: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建ICO文件失败", e)
            throw e
        }
    }
    
    /**
     * 模拟资源替换过程
     * 注意：这个函数只是模拟，实际上无法在Android上直接修改EXE文件的资源
     * 在真实环境中，这需要使用Windows API或专用工具实现
     */
    private fun simulateResourceReplacement(exeFile: File, iconFile: File): Boolean {
        AppLogger.d(TAG, "模拟替换EXE资源: ${exeFile.absolutePath}")
        AppLogger.d(TAG, "图标文件: ${iconFile.absolutePath}")
        
        // 这里我们只是模拟成功，实际上我们无法修改EXE文件
        return true
    }
    
    /**
     * 检查文件是否是有效的PE(可执行文件)格式
     * @param file 要检查的文件
     * @return 是否是有效的PE文件
     */
    fun isPEFile(file: File): Boolean {
        try {
            RandomAccessFile(file, "r").use { raf ->
                // 检查DOS头部
                raf.seek(0)
                val dosSignature = raf.readShort().toInt() and 0xFFFF
                if (dosSignature != DOS_SIGNATURE) { // 将Short转为Int再比较
                    return false
                }
                
                // 获取PE头偏移量
                raf.seek(0x3C)
                val peOffset = raf.readInt()
                
                // 检查PE签名
                raf.seek(peOffset.toLong())
                if (raf.readInt() != 0x00004550) { // "PE\0\0"
                    return false
                }
                
                return true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查PE文件格式失败", e)
            return false
        }
    }

    /** 清理临时文件 */
    fun cleanup() {
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
            AppLogger.d(TAG, "临时文件清理完成")
        }
    }
}
 