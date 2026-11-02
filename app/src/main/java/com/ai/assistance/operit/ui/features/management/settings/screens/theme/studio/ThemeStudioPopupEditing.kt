package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import com.ai.assistance.operit.data.model.theme.ThemeScriptPopupOffset

/** 既有双向数值按原来的净位移编辑，保留位置并避免上下控件同时锁住。 */
internal fun ThemeScriptPopupOffset.exclusiveVertical(): ThemeScriptPopupOffset = copy(
    up = (up - down).coerceAtLeast(0f),
    down = (down - up).coerceAtLeast(0f),
)
