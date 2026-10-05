package com.ai.assistance.operit.core.tools.system

import java.util.Locale

/** 用户选择的操作模式，不代表已经获得某一种系统授权。 */
enum class AndroidPermissionLevel {
    STANDARD, // 普通应用权限
    ADMIN,    // 组合使用无障碍与 Shizuku
    ROOT;     // 允许使用 Root，并复用已授权的管理员能力

    companion object {
        /**
         * 从字符串转换为权限等级
         * @param value 权限等级字符串
         * @return 对应的权限等级，如果无法识别则默认为STANDARD
         */
        fun fromString(value: String?): AndroidPermissionLevel {
            return when(value?.uppercase(Locale.ROOT)) {
                "STANDARD" -> STANDARD
                "ACCESSIBILITY", "DEBUGGER" -> ADMIN
                "ADMIN" -> ADMIN
                "ROOT" -> ROOT
                else -> STANDARD // 默认为最低权限
            }
        }
    }
} 