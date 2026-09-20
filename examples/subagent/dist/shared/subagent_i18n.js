"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveSubagentI18n = resolveSubagentI18n;
const ZH_CN = {
    stageAccepted: "已接受任务",
    stagePlanningTargets: "正在分析目标文件",
    stagePlanningTask: "正在分析委托任务",
    stageExecuting: "正在执行委托任务",
    stageSummarizing: "正在汇总结果",
    toolTriggeredGeneric: "已触发内部工具",
    toolCalledPrefix: toolName => `已调用 ${toolName}`,
    errorNoAppContext: "无法获取应用上下文",
    errorNoSummary: "子代理未返回总结文本",
    errorExecutionFailedPrefix: error => `执行失败：${error}`,
    stageLabelAccepted: "已接受",
    stageLabelPlanning: "规划中",
    stageLabelTool: "工具处理中",
    stageLabelExecuting: "执行中",
    stageLabelSummarizing: "汇总中",
    stageLabelDefault: "进行中",
    titleStarted: "Subagent 已启动",
    titleCompleted: "Subagent 已完成",
    titleFailed: "Subagent 失败",
    badgeSuccess: "成功",
    badgeFailure: "失败",
    detailTargetCount: count => `目标文件 ${count} 个`,
    badgeTargetCount: count => `目标 ${count}`,
    detailRun: runId => `运行 ${runId}`,
    badgeToolCount: count => `工具 ${count}`,
    detailToolCalls: count => `工具调用 ${count} 次`,
};
const EN_US = {
    stageAccepted: "Task accepted",
    stagePlanningTargets: "Analyzing target paths",
    stagePlanningTask: "Analyzing the delegated task",
    stageExecuting: "Running the delegated task",
    stageSummarizing: "Summarizing the result",
    toolTriggeredGeneric: "Internal tool invoked",
    toolCalledPrefix: toolName => `Called ${toolName}`,
    errorNoAppContext: "Failed to obtain the application context",
    errorNoSummary: "The subagent did not return a summary",
    errorExecutionFailedPrefix: error => `Execution failed: ${error}`,
    stageLabelAccepted: "Accepted",
    stageLabelPlanning: "Planning",
    stageLabelTool: "Tool running",
    stageLabelExecuting: "Executing",
    stageLabelSummarizing: "Summarizing",
    stageLabelDefault: "In progress",
    titleStarted: "Subagent started",
    titleCompleted: "Subagent completed",
    titleFailed: "Subagent failed",
    badgeSuccess: "Success",
    badgeFailure: "Failed",
    detailTargetCount: count => `${count} target files`,
    badgeTargetCount: count => `Targets ${count}`,
    detailRun: runId => `Run ${runId}`,
    badgeToolCount: count => `Tools ${count}`,
    detailToolCalls: count => `${count} tool calls`,
};
function shouldUseEnglish(useEnglish) {
    if (typeof useEnglish === "boolean") {
        return useEnglish;
    }
    const locale = typeof useEnglish === "string" ? useEnglish : getLang();
    return typeof locale === "string" && locale.toLowerCase().startsWith("en");
}
function resolveSubagentI18n(useEnglish) {
    return shouldUseEnglish(useEnglish) ? EN_US : ZH_CN;
}
