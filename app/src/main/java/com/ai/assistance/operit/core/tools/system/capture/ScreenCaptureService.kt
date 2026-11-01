package com.ai.assistance.operit.core.tools.system.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.logging.AppLogger

/**
 * A dedicated foreground service to satisfy Android 14+ MediaProjection requirements.
 * This service must be running before MediaProjection is requested and while it is active.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "ScreenCaptureChannel"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_START = "com.ai.assistance.operit.action.SCREEN_CAPTURE_FGS_START"

        @Volatile
        var isMediaProjectionForegroundReady: Boolean = false
            private set
        
        fun start(context: Context) {
            isMediaProjectionForegroundReady = false
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            isMediaProjectionForegroundReady = false
            val intent = Intent(context, ScreenCaptureService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "ScreenCaptureService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "ScreenCaptureService started")
        if (intent?.action == ACTION_START) {
            startForegroundService()
            return START_NOT_STICKY
        }

        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isMediaProjectionForegroundReady = false
        AppLogger.d(TAG, "ScreenCaptureService destroyed")
    }

    private fun startForegroundService() {
        try {
            val notification = createNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            isMediaProjectionForegroundReady = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Operit is capturing screen content")
            .setSmallIcon(R.drawable.ic_launcher_simple_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
