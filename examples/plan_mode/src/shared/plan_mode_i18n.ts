export type PlanModeI18n = {
  menuTitle: string;
  menuDescriptionEnabled: string;
  menuDescriptionDisabled: string;
  menuDescriptionWorkspaceMissing: string;
  toastWorkspaceRequired: string;
  toastChatViewMissing: string;
  toastPlanEmpty: string;
  toastPlanStarted: string;
  toastPlanAlreadyStarted: string;
  toastPlanWriteFailedPrefix: string;
  toastPlanSendFailedPrefix: string;
  toastPlanAlreadyCompleted: string;
  implementationMessage: string;
  rendererTitle: string;
  rendererSubtitle: string;
  rendererStreamingHint: string;
  rendererReadyHint: string;
  rendererButtonIdle: string;
  rendererButtonBusy: string;
  rendererStarted: string;
  rendererEmpty: string;
  rendererExpand: string;
  rendererCollapse: string;
  askRendererTitle: string;
  askRendererStreamingHint: string;
  askRendererDescriptionFallback: string;
  askRendererSubmitIdle: string;
  askRendererSubmitBusy: string;
  askRendererSubmitted: string;
  askRendererSelectRequired: string;
  askRendererQuestionPrefix: string;
  askRendererCustomFieldLabel: string;
  askRendererCustomFieldPlaceholder: string;
  askRendererOptionSelected: string;
  askRendererOptionUnselected: string;
  askToastAnswerSent: string;
  askToastAnswerSendFailedPrefix: string;
  promptPlanningMode: string;
  promptExistingPlanPrefix: string;
};

const ZH_CN: PlanModeI18n = {
  menuTitle: "计划模式",
  menuDescriptionEnabled: "已开启：禁止写入工具，确认后输出 <plantodo> 计划。",
  menuDescriptionDisabled: "开启后，对话将不会修改文件，而是向用户确定一个计划，批准后才开始实施。",
  menuDescriptionWorkspaceMissing: "未绑定工作区，无法开启。",
  toastWorkspaceRequired: "当前聊天未绑定工作区，不能开启计划模式。",
  toastChatViewMissing: "当前没有可用的聊天视图，无法开始实施。",
  toastPlanEmpty: "计划内容为空，无法开始实施。",
  toastPlanStarted: "已写入内部计划文件，并发送“开始实施”。",
  toastPlanAlreadyStarted: "这份计划已经开始实施，不会重复发起。",
  toastPlanWriteFailedPrefix: "写入计划失败：",
  toastPlanSendFailedPrefix: "发送开始实施失败：",
  toastPlanAlreadyCompleted: "当前工作区没有计划文件。",
  implementationMessage: "开始实施",
  rendererTitle: "实施计划",
  rendererSubtitle: "计划闭合后会显示开始实施按钮，并自动写入内部计划文件。",
  rendererStreamingHint: "计划仍在生成中。",
  rendererReadyHint: "计划已就绪。点击后会写入内部计划文件、关闭计划模式并发送“开始实施”。",
  rendererButtonIdle: "开始实施",
  rendererButtonBusy: "正在开始实施…",
  rendererStarted: "开始实施消息已发出。",
  rendererEmpty: "计划内容为空。",
  rendererExpand: "展开全部计划",
  rendererCollapse: "收起计划",
  askRendererTitle: "计划确认",
  askRendererStreamingHint: "问题仍在生成中，等 <planask> 完整闭合后再提交。",
  askRendererDescriptionFallback: "请先确认下面这些选择问题，我会根据你的答案继续制定计划。",
  askRendererSubmitIdle: "提交答案",
  askRendererSubmitBusy: "正在提交…",
  askRendererSubmitted: "答案已发送。",
  askRendererSelectRequired: "请先完成所有问题的选择。",
  askRendererQuestionPrefix: "问题",
  askRendererCustomFieldLabel: "自定义回答",
  askRendererCustomFieldPlaceholder: "如果这些选项都不合适，可以在这里自己填写",
  askRendererOptionSelected: "已选",
  askRendererOptionUnselected: "点击选择",
  askToastAnswerSent: "已发送计划确认答案。",
  askToastAnswerSendFailedPrefix: "发送计划确认答案失败：",
  promptPlanningMode: [
    "当前处于计划模式。",
    "- 你现在的任务是先产出一份待确认的实施计划，不要开始实施。",
    "- 在正式制定计划前，你可以先做适度的阅读、搜索、检查与分析，用来理解现状和发现约束。",
    "- 如果仍然存在会影响方案的不确定事项、分支选择或关键假设，你应该先向用户确认，再继续。",
    "- 只要还有这些未确认问题，就不要输出最终计划。",
    "- 等用户确认完这些问题之后，再输出最终计划。",
    "- 如果你需要向用户提出 1 到 3 个单选确认问题，优先输出一个 <planask>...</planask> 块，而不是普通文本提问。",
    "- <planask> 的结构必须是：<planask><title>标题</title><description>说明</description><question id=\"q1\"><title>问题一</title><option id=\"a\">选项A</option><option id=\"b\">选项B</option></question>...</planask>。",
    "- <planask> 最多包含 3 个 <question>，每个问题必须是单选，且至少有 2 个 <option>。",
    "- 当你输出 <planask> 的那一轮，不要先用普通文本把问题再说一遍，直接输出完整的 <planask>...</planask> 即可。",
    "- 用户提交后，你会收到一条人类可读的确认答复消息，你应根据其中答案继续探索、确认或输出最终计划。",
    "- 不要调用 `plan_mode_tools:get_plan` 或 `plan_mode_tools:complete_plan`。",
    "- 不要自行创建、写入、修改、删除任何文件。",
    "- 你禁止调用任何写入、编辑、删除、移动、重命名、执行命令、发送消息、修改设置或其他具有副作用的工具。",
    "- 你只允许做分析、阅读、搜索、推理与确认，不允许实施。",
    "- 当你完全确定后，不要先用普通文本把计划再说一遍，直接输出完整的 <plantodo>...</plantodo> 即可。",
    "- <plantodo> 内部只需要输出 Markdown 计划正文，不要再添加任何 XML 子标签，例如 <title>、<description>、<step> 等。",
    "- 不要在 <plantodo> 之外再重复输出一份普通文本计划。",
  ].join("\n"),
  promptExistingPlanPrefix: "先读取并遵守当前计划：",
};

const EN_US: PlanModeI18n = {
  menuTitle: "Plan Mode",
  menuDescriptionEnabled: "Enabled: writing tools are forbidden; output the final plan in <plantodo>.",
  menuDescriptionDisabled: "Disabled: if a current plan exists in the workspace, the AI must read it first and clear it when done.",
  menuDescriptionWorkspaceMissing: "No workspace is bound, so this mode cannot be enabled.",
  toastWorkspaceRequired: "This chat has no bound workspace, so plan mode cannot be enabled.",
  toastChatViewMissing: "No active chat view is available, so implementation cannot start.",
  toastPlanEmpty: "The plan is empty, so implementation cannot start.",
  toastPlanStarted: "The internal plan file has been written and \"Start implementation\" was sent.",
  toastPlanAlreadyStarted: "This plan is already being implemented, so it was not started again.",
  toastPlanWriteFailedPrefix: "Failed to write the plan: ",
  toastPlanSendFailedPrefix: "Failed to send the implementation kickoff: ",
  toastPlanAlreadyCompleted: "The current workspace does not have a plan file.",
  implementationMessage: "Start implementation",
  rendererTitle: "Implementation Plan",
  rendererSubtitle: "The action button appears after the plan is fully closed and will write the internal plan file.",
  rendererStreamingHint: "The plan is still streaming. Wait until <plantodo> is fully closed before starting implementation.",
  rendererReadyHint: "The plan is ready. This will write the internal plan file, close plan mode, and send \"Start implementation\".",
  rendererButtonIdle: "Start Implementation",
  rendererButtonBusy: "Starting…",
  rendererStarted: "The implementation kickoff message has been sent.",
  rendererEmpty: "The plan is empty.",
  rendererExpand: "Expand Full Plan",
  rendererCollapse: "Collapse Plan",
  askRendererTitle: "Plan Questions",
  askRendererStreamingHint: "The questions are still streaming. Wait until <planask> is fully closed before submitting.",
  askRendererDescriptionFallback: "Please confirm the following choices so I can continue planning.",
  askRendererSubmitIdle: "Submit Answers",
  askRendererSubmitBusy: "Submitting…",
  askRendererSubmitted: "Answers sent.",
  askRendererSelectRequired: "Please answer every question first.",
  askRendererQuestionPrefix: "Question",
  askRendererCustomFieldLabel: "Custom Answer",
  askRendererCustomFieldPlaceholder: "If none of the options fit, type your own answer here",
  askRendererOptionSelected: "Selected",
  askRendererOptionUnselected: "Tap to choose",
  askToastAnswerSent: "Plan clarification answers were sent.",
  askToastAnswerSendFailedPrefix: "Failed to send plan clarification answers: ",
  promptPlanningMode: [
    "The conversation is currently in plan mode.",
    "- Your job right now is to produce a plan for confirmation first. Do not start implementing.",
    "- Before drafting the final plan, you may do reasonable reading, searching, inspection, and analysis to understand the situation and constraints.",
    "- If there are still uncertainties, branching choices, or key assumptions that would affect the plan, ask the user to confirm them first.",
    "- As long as such questions remain unresolved, do not output the final plan yet.",
    "- Only after the user has confirmed those open questions may you output the final plan.",
    "- If you need to ask the user 1 to 3 single-choice confirmation questions, prefer outputting one <planask>...</planask> block instead of plain-text questions.",
    "- The <planask> structure must be: <planask><title>Title</title><description>Description</description><question id=\"q1\"><title>Question 1</title><option id=\"a\">Option A</option><option id=\"b\">Option B</option></question>...</planask>.",
    "- A <planask> block may contain at most 3 <question> blocks. Every question must be single-choice and have at least 2 <option> entries.",
    "- On any turn where you output <planask>, do not restate the questions in plain text first. Output the complete <planask>...</planask> block directly.",
    "- After the user submits answers, you will receive a human-readable clarification message. Use those answers to continue exploring, confirming, or producing the final plan.",
    "- Do not call `plan_mode_tools:get_plan` or `plan_mode_tools:complete_plan`.",
    "- Do not create, write, modify, or delete any file yourself.",
    "- You must not call any tool that writes, edits, deletes, moves, renames, executes commands, sends messages, changes settings, or causes side effects.",
    "- You may only analyze, read, search, reason, and confirm. Do not implement anything yet.",
    "- Once you are completely certain, do not restate the plan in plain text first. Output the complete <plantodo>...</plantodo> block directly.",
    "- Inside <plantodo>, output only the Markdown plan body. Do not add extra XML child tags such as <title>, <description>, or <step>.",
    "- Do not output a second plain-text copy of the plan outside the <plantodo> block.",
  ].join("\n"),
  promptExistingPlanPrefix: "Read and follow the current plan first:",
};

function shouldUseEnglish(useEnglish?: boolean | string): boolean {
  if (typeof useEnglish === "boolean") {
    return useEnglish;
  }
  const locale = typeof useEnglish === "string" ? useEnglish : getLang();
  return typeof locale === "string" && locale.toLowerCase().startsWith("en");
}

export function resolvePlanModeI18n(useEnglish?: boolean | string): PlanModeI18n {
  return shouldUseEnglish(useEnglish) ? EN_US : ZH_CN;
}
