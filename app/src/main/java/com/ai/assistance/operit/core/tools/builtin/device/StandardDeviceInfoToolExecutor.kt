package com.ai.assistance.operit.core.tools.builtin.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.ai.assistance.operit.core.tools.DeviceInfoResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.runtime.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Device information tool that collects comprehensive system details Provides information about
 * hardware, software, network, and current device state
 */
open class StandardDeviceInfoToolExecutor(private val context: Context) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult {
        return try {
            // Get basic device information
            val deviceId =
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            // Get device model and manufacturer
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER

            // Get Android version
            val androidVersion = Build.VERSION.RELEASE
            val sdkVersion = Build.VERSION.SDK_INT

            // Get screen information
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val screenResolution = "${screenWidth}x${screenHeight}"
            val screenDensity = displayMetrics.density

            // Get memory information
            val activityManager =
                    context.getSystemService(Context.ACTIVITY_SERVICE) as
                            android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val availableMemory = formatSize(memoryInfo.availMem)
            val totalMemory = formatSize(memoryInfo.totalMem)

            // Get storage information
            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            val availableBlocks = statFs.availableBlocksLong
            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableStorage = formatSize(availableBlocks * blockSize)
            val totalStorage = formatSize(totalBlocks * blockSize)

            // Get battery information
            var batteryLevel = 0
            var isCharging = false

            try {
                val batteryIntent =
                        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent != null) {
                    val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    batteryLevel = (level * 100 / scale.toFloat()).toInt()

                    val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging =
                            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL
                }
            } catch (e: Exception) {
                // Battery info couldn't be retrieved
            }

            // Get CPU information
            val cpuInfo =
                    try {
                        val processBuilder = ProcessBuilder("getprop", "ro.product.cpu.abi")
                        val process = processBuilder.start()
                        val reader =
                                java.io.BufferedReader(
                                        java.io.InputStreamReader(process.inputStream)
                                )
                        val cpuAbi = reader.readLine() ?: "Unknown"
                        process.waitFor()
                        reader.close()
                        cpuAbi
                    } catch (e: Exception) {
                        "Unknown"
                    }

            // Get network information
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as
                            android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            val networkType =
                    when {
                        networkCapabilities == null -> "No connection"
                        networkCapabilities.hasTransport(
                                android.net.NetworkCapabilities.TRANSPORT_WIFI
                        ) -> "WiFi"
                        networkCapabilities.hasTransport(
                                android.net.NetworkCapabilities.TRANSPORT_CELLULAR
                        ) -> "Mobile data"
                        networkCapabilities.hasTransport(
                                android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
                        ) -> "Bluetooth"
                        networkCapabilities.hasTransport(
                                android.net.NetworkCapabilities.TRANSPORT_ETHERNET
                        ) -> "Ethernet"
                        else -> "Other"
                    }

            // Collect additional system properties
            val additionalInfo = mutableMapOf<String, String>()
            additionalInfo["Device name"] = Build.DEVICE
            additionalInfo["Product name"] = Build.PRODUCT
            additionalInfo["Hardware name"] = Build.HARDWARE
            additionalInfo["Build fingerprint"] = Build.FINGERPRINT
            additionalInfo["Build time"] = java.util.Date(Build.TIME).toString()

            // Create result data object
            val deviceInfoResult =
                    DeviceInfoResultData(
                            deviceId = deviceId,
                            model = model,
                            manufacturer = manufacturer,
                            androidVersion = androidVersion,
                            sdkVersion = sdkVersion,
                            screenResolution = screenResolution,
                            screenDensity = screenDensity,
                            totalMemory = totalMemory,
                            availableMemory = availableMemory,
                            totalStorage = totalStorage,
                            availableStorage = availableStorage,
                            batteryLevel = batteryLevel,
                            batteryCharging = isCharging,
                            cpuInfo = cpuInfo,
                            networkType = networkType,
                            additionalInfo = additionalInfo
                    )

            ToolResult(toolName = tool.name, success = true, result = deviceInfoResult)
        } catch (e: Exception) {
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error retrieving device info: ${e.message}"
            )
        }
    }

    /** Formats a byte size into a human-readable string with appropriate unit (KB, MB, GB) */
    private fun formatSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val tb = gb * 1024

        return when {
            size < kb -> "$size B"
            size < mb -> String.format("%.2f KB", size / kb)
            size < gb -> String.format("%.2f MB", size / mb)
            size < tb -> String.format("%.2f GB", size / gb)
            else -> String.format("%.2f TB", size / tb)
        }
    }
}
