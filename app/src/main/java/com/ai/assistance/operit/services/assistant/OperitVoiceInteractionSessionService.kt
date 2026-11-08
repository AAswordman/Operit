package com.ai.assistance.operit.services.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.ai.assistance.operit.util.logging.AppLogger
import android.view.View
import android.widget.FrameLayout
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode

/**
 * Operit 语音交互会话服务
 * 
 * 当用户触发助手时（如长按 Home 键），系统会创建这个服务的会话实例。
 * 我们在这里启动悬浮窗来提供 AI 助手功能。
 */
class OperitVoiceInteractionSessionService : VoiceInteractionSessionService() {
    
    companion object {
        private const val TAG = "OperitSessionService"
    }
    
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        AppLogger.d(TAG, "Creating new voice interaction session")
        return OperitVoiceInteractionSession(this)
    }
    
    /**
     * Operit 的语音交互会话实现
     */
    private class OperitVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
        
        companion object {
            private const val TAG = "OperitSession"
        }
        
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            AppLogger.d(TAG, "Session show requested with flags: $showFlags")
            
            // 启动悬浮窗服务来提供 AI 助手功能
            startFloatingChatService()
            
            // 立即结束会话，因为我们使用悬浮窗而不是系统覆盖层
            finish()
        }
        
        override fun onHide() {
            super.onHide()
            AppLogger.d(TAG, "Session hide requested")
            finish()
        }
        
        override fun onDestroy() {
            AppLogger.d(TAG, "Session destroyed")
            super.onDestroy()
        }
        
        /**
         * 启动悬浮窗聊天服务
         */
        private fun startFloatingChatService() {
            try {
                val intent = Intent(context, FloatingChatService::class.java).apply {
                    putExtra("INITIAL_MODE", FloatingMode.FULLSCREEN.name)
                    putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                AppLogger.d(TAG, "Floating chat service started")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to start floating chat service", e)
            }
        }
    }
}
