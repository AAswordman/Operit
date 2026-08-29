package com.ai.assistance.operit.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.theme.NativeThemeGlanceHost
import com.ai.assistance.operit.ui.theme.NativeThemeGlancePaletteV1
import kotlinx.coroutines.flow.first

/**
 * Voice Assistant Widget using Glance (Jetpack Compose for Widgets)
 * 
 * This widget provides a quick launcher for the voice assistant fullscreen mode.
 * Users can add it to their home screen and tap to instantly launch the voice assistant.
 * 
 * The widget directly starts the FloatingChatService without going through MainActivity,
 * which simplifies the launch process and improves performance.
 */
class VoiceAssistantGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val themeSnapshot =
            ActivePromptManager.getInstance(context)
                .activeThemePreferenceSnapshotFlow
                .first()
        provideContent {
            NativeThemeGlanceHost(
                context = context,
                initialSnapshot = themeSnapshot,
            ) { themePalette ->
                VoiceAssistantWidgetContent(
                    context = context,
                    themePalette = themePalette,
                )
            }
        }
    }
}

@Composable
internal fun VoiceAssistantWidgetContent(
    context: Context,
    themePalette: NativeThemeGlancePaletteV1,
) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(themePalette.primary.withAlpha(0.8f).toColorProvider())
                .padding(16.dp)
                .clickable {
                    // 直接启动 FloatingChatService，不需要经过 MainActivity
                    val intent = Intent(context, FloatingChatService::class.java).apply {
                        // 设置初始模式为全屏语音模式
                        putExtra("INITIAL_MODE", FloatingMode.FULLSCREEN.name)
                        putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
                    }
                    
                    // 启动前台服务
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                
                // 麦克风图标
                Image(
                    provider = ImageProvider(R.drawable.ic_microphone),
                    contentDescription = context.getString(R.string.voice_assistant_widget_title),
                    modifier = GlanceModifier.size(48.dp),
                    colorFilter = ColorFilter.tint(themePalette.onPrimary.toColorProvider()),
                )

                Spacer(modifier = GlanceModifier.height(12.dp))

                // 标题文字
                Text(
                    text = "Operit",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = themePalette.onPrimary.toColorProvider(),
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 副标题
                Text(
                    text = context.getString(R.string.voice_assistant_widget_title),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = themePalette.onPrimary.withAlpha(0.9f).toColorProvider(),
                    )
                )
            }
        }
    }
}
