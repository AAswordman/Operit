package com.ai.assistance.operit.core.workflow.runtime

import com.ai.assistance.operit.core.workflow.model.WorkflowExecutionIssue
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowIssueClassifierTest {
    @Test fun networkTimeoutAndInvalidCredentialsHaveDifferentReasons() {
        assertEquals(WorkflowExecutionIssue.TIMEOUT, classifyWorkflowIssue("SocketTimeoutException: timed out"))
        assertEquals(WorkflowExecutionIssue.NETWORK, classifyWorkflowIssue("Unable to connect to host"))
        assertEquals(WorkflowExecutionIssue.AUTHENTICATION, classifyWorkflowIssue("HTTP 401 Unauthorized"))
    }
    @Test fun localizedValidationErrorsIdentifyTheNodeProblem() {
        assertEquals(WorkflowExecutionIssue.REFERENCE, classifyWorkflowIssue("引用节点 a 尚未完成执行"))
        assertEquals(WorkflowExecutionIssue.INVALID_INPUT, classifyWorkflowIssue("节点没有配置 actionType"))
        assertEquals(WorkflowExecutionIssue.PERMISSION, classifyWorkflowIssue("缺少执行权限"))
    }
    @Test fun timeoutCancellationIsStillReportedAsTimeout() {
        assertEquals(WorkflowExecutionIssue.TIMEOUT, classifyWorkflowIssue("TimeoutCancellationException: timed out"))
        assertEquals(WorkflowExecutionIssue.CANCELLED, classifyWorkflowIssue("JobCancellationException"))
    }
    @Test fun missingToolNamesDoNotHideTheCause() {
        assertEquals(WorkflowExecutionIssue.TOOL_MISSING, classifyWorkflowIssue("Tool example:run not found"))
    }
    @Test fun unknownErrorsKeepAnExplicitUnknownCategory() {
        assertEquals(WorkflowExecutionIssue.UNKNOWN, classifyWorkflowIssue("An unexpected condition occurred"))
    }
}
