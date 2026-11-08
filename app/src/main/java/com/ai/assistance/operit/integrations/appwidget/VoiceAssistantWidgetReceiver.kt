package com.ai.assistance.operit.integrations.appwidget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Widget Receiver for Voice Assistant Widget
 * 
 * This receiver handles widget lifecycle events and binds to the Glance widget.
 */
class VoiceAssistantWidgetReceiver : GlanceAppWidgetReceiver() {
    
    override val glanceAppWidget: GlanceAppWidget
        get() = VoiceAssistantGlanceWidget()
}

