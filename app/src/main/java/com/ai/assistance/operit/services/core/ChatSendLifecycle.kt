package com.ai.assistance.operit.services.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 将发送、异常处理和收尾拆成独立状态机，取消只向上游传播，收尾必须完整执行。 */
internal suspend fun runChatSendLifecycle(
    onCancelled: (CancellationException) -> Unit,
    onFailure: suspend (Exception) -> Unit,
    onFinally: suspend () -> Unit = {},
    send: suspend () -> Unit,
) {
    try {
        send()
    } catch (error: CancellationException) {
        onCancelled(error)
        throw error
    } catch (error: Exception) {
        onFailure(error)
    } finally {
        // 取消后仍需解除工作区钩子、释放运行态并完成消息持久化。
        withContext(NonCancellable) { onFinally() }
    }
}
