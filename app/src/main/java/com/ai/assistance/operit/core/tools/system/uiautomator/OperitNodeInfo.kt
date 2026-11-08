package com.ai.assistance.operit.core.tools.system.uiautomator

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * 一个可序列化的数据类，用于替代AccessibilityNodeInfo，以便在进程间传递。
 * 它包含了节点的大部分关键信息，并且可以方便地通过Gson进行序列化和反序列化。
 */
data class OperitNodeInfo(
    @SerializedName("children")
    val children: List<OperitNodeInfo> = emptyList(),

    @SerializedName("className")
    val className: String?,

    @SerializedName("packageName")
    val packageName: String?,

    @SerializedName("text")
    val text: String?,

    @SerializedName("content-desc")
    val contentDescription: String?,

    @SerializedName("resource-id")
    val viewIdResourceName: String?,

    @SerializedName("bounds")
    val boundsInScreen: String?, // e.g., "[0,0][1080,2220]"

    @SerializedName("clickable")
    val isClickable: Boolean,

    @SerializedName("visible")
    val isVisibleToUser: Boolean,

    @SerializedName("focused")
    val isFocused: Boolean,

    @SerializedName("checked")
    val isChecked: Boolean
    // 可以根据需要添加更多属性
) : Serializable {

    // 为了方便，可以添加一些辅助函数来解析bounds
    fun getBounds(): android.graphics.Rect? {
        if (boundsInScreen == null) return null
        return try {
            val parts = boundsInScreen.replace("[", "").replace("]", ",").split(",")
            android.graphics.Rect(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
        } catch (e: Exception) {
            null
        }
    }
}
