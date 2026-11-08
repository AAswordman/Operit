package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

@Serializable
enum class PackageActivationErrorCode {
    MISSING_PACKAGE_NAME,
    PACKAGE_NOT_FOUND,
    PACKAGE_NOT_ACTIVATABLE,
    PACKAGE_DISABLED,
    PACKAGE_LOAD_FAILED,
    REQUIRED_ENV_MISSING,
    SKILL_HIDDEN,
    MCP_CONNECTION_FAILED,
}

/** 激活失败携带稳定错误类型，普通导入包与外置内置包使用同一协议。 */
@Serializable
data class PackageActivationErrorData(
    val code: PackageActivationErrorCode,
    val packageName: String,
    val message: String,
) : ToolResultData() {
    override fun toString(): String = message
}
