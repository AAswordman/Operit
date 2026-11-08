package com.ai.assistance.operit.core.workflow.runtime

import com.ai.assistance.operit.core.workflow.model.ExecuteNode
import com.ai.assistance.operit.core.workflow.model.WorkflowNodeExecutionReport
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkflowFailureDescriptionTest {
    @Test fun capturesNestedExceptionCausesBeforeFormatting() {
        val error = IllegalStateException("调用失败", java.net.SocketTimeoutException("连接 api.example.test 超时"))
        assertEquals("调用失败\n连接 api.example.test 超时", readableWorkflowFailure(workflowFailureCause(error)))
    }

    @Test fun keepsJavaCauseMessagesWithoutStackFramesOrExceptionPrefixes() {
        val raw = "java.lang.IllegalStateException: 调用失败\n\tat example.Runner.run(Runner.kt:12)\nCaused by: java.net.SocketTimeoutException: read timed out\n\t... 2 more"
        assertEquals("调用失败\nread timed out", readableWorkflowFailure(raw))
    }

    @Test fun removesJavascriptStackFrames() {
        assertEquals("服务拒绝请求", readableWorkflowFailure("Error: 服务拒绝请求\n    at request (main.js:18:4)"))
    }

    @Test fun removesPythonTracebackSourceAndKeepsActualError() {
        val raw = "Traceback (most recent call last):\n  File \"main.py\", line 12, in run\n    request()\n    ^^^^^^^^^\nTimeoutError: 服务未响应"
        assertEquals("服务未响应", readableWorkflowFailure(raw))
    }

    @Test fun resolvesReferencedNodeNamesWithoutReplacingHttpCodesOrDurations() {
        assertEquals("HTTP 401，等待 1 秒；引用节点 获取令牌 执行失败", readableWorkflowFailure(
            "HTTP 401，等待 1 秒；引用节点 1 执行失败", mapOf("1" to "获取令牌"),
        ))
    }

    @Test fun preservesUsefulMultilineDetails() {
        val reason = "HTTP 429\n服务返回：今日额度已用完\n请稍后重试"
        assertEquals(reason, readableWorkflowFailure(reason))
        assertEquals("", readableWorkflowFailure(null))
    }

    @Test fun identifiesNamedAndUnnamedExecuteNodesByUserMeaning() {
        assertEquals("读取群消息", ExecuteNode(id = "private-id", name = "读取群消息", actionType = "http_get").readableWorkflowName())
        assertEquals("extended_http_tools:http_get", ExecuteNode(id = "private-id", actionType = "extended_http_tools:http_get").readableWorkflowName())
    }

    @Test fun oldNodeReportsDecodeWithoutNewFailureFields() {
        val report = Json.decodeFromString<WorkflowNodeExecutionReport>("""{"nodeId":"private-id","nodeName":"读取消息","status":"FAILED","issue":"TIMEOUT"}""")
        assertNull(report.failureReason)
        assertNull(report.actionName)
        val updated = report.copy(failureReason = "HTTP 503", actionName = "http_get")
        assertEquals("HTTP 503", updated.failureReason)
    }
}
