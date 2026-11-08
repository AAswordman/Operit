package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.util.logging.AppLogger
import java.io.BufferedReader
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 协议确认完成后只补读尾部用量；连接关闭不能把已完成回答变成新的失败请求。 */
internal suspend fun BufferedReader.readResponseLine(
    completionConfirmed: Boolean,
    isCancelled: () -> Boolean,
): String? {
    currentCoroutineContext().ensureActive()
    return try {
        readLine()
    } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        if (!completionConfirmed || isCancelled()) throw failure
        // 只处理行读取的传输结束；解析错误、服务端错误及下游保存异常仍由原入口报告。
        AppLogger.w("AIService", "协议已确认完成，结束尾部用量读取：${failure.javaClass.simpleName}", failure)
        null
    }
}
