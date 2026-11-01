package com.ai.assistance.operit.ui.common.composedsl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslRenderResult
import kotlinx.coroutines.yield

@Composable
internal fun RetainToolPkgComposeDslActions(
    jsEngine: JsEngine,
    renderResult: ToolPkgComposeDslRenderResult?
) {
    // 组合提交后再确认新树，避免后台生成中间结果时提前释放屏幕仍在使用的回调。
    LaunchedEffect(jsEngine, renderResult?.actionSnapshot) {
        // 等待本次组合中的子视图 SideEffect 更新回调引用，再通知脚本引擎。
        yield()
        if (renderResult != null) jsEngine.retainComposeDslActions(renderResult)
    }
}
