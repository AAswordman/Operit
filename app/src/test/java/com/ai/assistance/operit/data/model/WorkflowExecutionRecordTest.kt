package com.ai.assistance.operit.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionIssue
import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionLogEntry
import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionRecord
import com.ai.assistance.operit.core.workflow.model.WorkflowLogLevel
import com.ai.assistance.operit.core.workflow.model.WorkflowNodeExecutionReport
import com.ai.assistance.operit.core.workflow.model.WorkflowNodeRunStatus

class WorkflowExecutionRecordTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun olderRecordsRemainReadableWithoutInventingNodeDetails() {
        val record = json.decodeFromString<WorkflowExecutionRecord>(
            """{"workflowId":"one","workflowName":"旧工作流","success":false,"message":"failed"}"""
        )
        assertTrue(record.nodes.isEmpty())
        assertNull(record.issue)
    }
    @Test fun nodeResultsAndLocalizedIssueCodesSurvivePersistence() {
        val source = WorkflowExecutionRecord(
            workflowId = "one", workflowName = "工作流", success = false, message = "failed",
            issue = WorkflowExecutionIssue.NETWORK,
            logs = listOf(WorkflowExecutionLogEntry(
                level = WorkflowLogLevel.ERROR, message = "网络连接失败", nodeId = "n",
            )),
            nodes = listOf(WorkflowNodeExecutionReport(
                nodeId = "n", nodeName = "发送消息", status = WorkflowNodeRunStatus.FAILED,
                startedAt = 10L, finishedAt = 25L, issue = WorkflowExecutionIssue.NETWORK,
            )),
        )
        val encoded = json.encodeToString(source)
        val stored = json.parseToJsonElement(encoded).jsonObject
        // 默认值省略开启时，运行元数据和日志时间也必须实际写入文件。
        assertTrue(stored.containsKey("runId"))
        assertTrue(stored.containsKey("startedAt"))
        assertTrue(stored.containsKey("finishedAt"))
        assertTrue(stored.getValue("logs").jsonArray.single().jsonObject.containsKey("timestamp"))
        assertEquals(source, json.decodeFromString<WorkflowExecutionRecord>(encoded))
    }
}
