package com.ai.assistance.operit.integrations.http.commands

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.content.IntentCompat
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.integrations.http.ExternalChatHttpState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal object WebChatForegroundControl {
    const val RESULT_EXTRA = "operit.web_chat.command_result"

    suspend fun request(context: Context, start: Boolean): ExternalChatHttpState = withTimeout(20_000L) {
        suspendCancellableCoroutine { continuation ->
            val receiver = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (continuation.isActive) {
                        if (resultData == null) {
                            continuation.resumeWithException(IllegalStateException("Missing HTTP service result"))
                            return
                        }
                        continuation.resume(ExternalChatHttpState(
                            isRunning = resultData.getBoolean("running"),
                            port = if (resultData.containsKey("port")) resultData.getInt("port") else null,
                            lastError = resultData.getString("error"),
                        ))
                    }
                }
            }
            try {
                AIForegroundService.requestExternalHttpControl(context, start, receiver)
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
    }

    fun reply(intent: Intent?, state: ExternalChatHttpState) {
        if (intent == null) return
        val receiver = IntentCompat.getParcelableExtra(intent, RESULT_EXTRA, ResultReceiver::class.java)
        if (receiver != null) deliver(receiver, state)
    }

    fun deliver(receiver: ResultReceiver, state: ExternalChatHttpState) {
        receiver.send(0, Bundle().apply {
            putBoolean("running", state.isRunning)
            state.port?.let { putInt("port", it) }
            putString("error", state.lastError)
        })
    }
}
