import {
  CHAT_VIEW_HOOK_ID,
  MENU_HOOK_ID,
  PLANASK_XML_RENDER_HOOK_ID,
  PLANASK_XML_TAG,
  PROMPT_ESTIMATE_FINALIZE_HOOK_ID,
  PROMPT_FINALIZE_HOOK_ID,
  PROMPT_HOOK_ID,
  TOOL_PROMPT_HOOK_ID,
  TOGGLE_ID,
  TOGGLE_ICON,
  XML_RENDER_HOOK_ID,
  XML_TAG,
} from "../shared/plan_mode_constants.js";
import { resolvePlanModeI18n } from "../shared/plan_mode_i18n.js";
import {
  PlanModeShared,
  registerSharedMethods,
} from "../shared/plan_mode_runtime_ipc.js";
import { appendPrompt, buildExistingPlanPrompt, buildPlanningModePrompt } from "../shared/plan_mode_prompt.js";
import { type PlanModeRuntime } from "../shared/plan_mode_state.js";
import {
  PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL,
  PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL,
  type StartPlanImplementationResult,
} from "../shared/plan_mode_execution.js";
import {
  PLAN_MODE_SUBMIT_ANSWERS_IPC_CHANNEL,
  type SubmitPlanaskAnswersResult,
} from "../shared/plan_mode_ask_execution.js";
import {
  logPlanModeDebug,
} from "../shared/plan_mode_workspace.js";
import { onPlanaskXmlRender } from "./planask-xml-render-plugin.js";
import { onPlantodoXmlRender } from "./plantodo-xml-render-plugin.js";

const PLAN_MODE_BLOCKED_TOOL_NAMES = new Set([
  "apply_file",
  "create_file",
  "edit_file",
  "delete_file",
]);

let planModeIpcRegistered = false;

function usesChatPrompt(payload: ToolPkg.PromptHookEventPayload): boolean {
  const promptFunctionType = payload.promptFunctionType;
  if (promptFunctionType !== undefined && promptFunctionType !== "") {
    return promptFunctionType === "CHAT";
  }
  const functionType = payload.functionType;
  return functionType === undefined || functionType === "" || functionType === "CHAT";
}

function isPlanModeRuntime(value: string | undefined): value is PlanModeRuntime {
  return value === "main" || value === "floating";
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim() !== "";
}

async function handleSubmitPlanaskAnswersIpc(
  message: string
): Promise<SubmitPlanaskAnswersResult> {
  const text = resolvePlanModeI18n();
  try {
    const activeView = await PlanModeShared.getSingleActiveChatView();
    if (!activeView) {
      await Tools.System.toast(text.toastChatViewMissing);
      return {
        success: false,
        error: text.toastChatViewMissing,
      };
    }

    void Tools.Chat.sendMessage(
      message,
      activeView.chatId,
      undefined,
      undefined,
      { runtime: activeView.runtime }
    ).catch((error) => {
      const errorText = error instanceof Error
        ? error.message || "error"
        : (typeof error === "string" || error == null ? error || "error" : "error");
      const toastMessage = `${text.askToastAnswerSendFailedPrefix}${errorText}`;
      void Tools.System.toast(toastMessage);
    });
    await Tools.System.toast(text.askToastAnswerSent);
    return { success: true };
  } catch (error) {
    const errorText = error instanceof Error
      ? error.message || "error"
      : (typeof error === "string" || error == null ? error || "error" : "error");
    const messageText = `${text.askToastAnswerSendFailedPrefix}${errorText}`;
    await Tools.System.toast(messageText);
    return {
      success: false,
      error: messageText,
    };
  }
}

async function handleStartImplementationIpc(
  planContent: string
): Promise<StartPlanImplementationResult> {
  const text = resolvePlanModeI18n();
  const normalizedPlanContent = planContent.trim();
  if (!normalizedPlanContent) {
    const messageText = text.toastPlanEmpty;
    await Tools.System.toast(messageText);
    return { success: false, error: messageText };
  }

  try {
    const activeView = await PlanModeShared.getSingleActiveChatView();
    if (!activeView) {
      await Tools.System.toast(text.toastChatViewMissing);
      return { success: false, error: text.toastChatViewMissing };
    }
    if (!(await PlanModeShared.claimPlanStart(normalizedPlanContent))) {
      await Tools.System.toast(text.toastPlanAlreadyStarted);
      return { success: true, alreadyStarted: true };
    }
    try {
      const written = await PlanModeShared.writePlanFile(activeView.chatId, normalizedPlanContent);
      await PlanModeShared.disable(written.chatId);
      void Tools.Chat.sendMessage(
        text.implementationMessage,
        written.chatId,
        undefined,
        undefined,
        { runtime: activeView.runtime }
      ).catch((error) => {
        const errorText = error instanceof Error
          ? error.message || "error"
          : (typeof error === "string" || error == null ? error || "error" : "error");
        const messageText = `${text.toastPlanSendFailedPrefix}${errorText}`;
        void Tools.System.toast(messageText);
      });
      void Tools.System.toast(text.toastPlanStarted);
      return { success: true };
    } catch (error) {
      await PlanModeShared.forgetPlanStart(normalizedPlanContent);
      throw error;
    }
  } catch (error) {
    const errorText = error instanceof Error
      ? error.message || "error"
      : (typeof error === "string" || error == null ? error || "error" : "error");
    const messageText = `${text.toastPlanWriteFailedPrefix}${errorText}`;
    await Tools.System.toast(messageText);
    return { success: false, error: messageText };
  }
}

async function handleIsPlanStartedIpc(planContent: string): Promise<boolean> {
  try {
    return await PlanModeShared.isPlanStarted(planContent);
  } catch (error) {
    logPlanModeDebug("handleIsPlanStartedIpc.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return false;
  }
}

function registerPlanModeIpc(): void {
  if (planModeIpcRegistered) {
    return;
  }
  planModeIpcRegistered = true;
  registerSharedMethods(PlanModeShared);
  ToolPkg.ipc.on<string, SubmitPlanaskAnswersResult>(
    PLAN_MODE_SUBMIT_ANSWERS_IPC_CHANNEL,
    handleSubmitPlanaskAnswersIpc
  );
  ToolPkg.ipc.on<string, StartPlanImplementationResult>(
    PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL,
    handleStartImplementationIpc
  );
  ToolPkg.ipc.on<string, boolean>(
    PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL,
    handleIsPlanStartedIpc
  );
}

function filterPlanModeTools(
  availableTools: ToolPkg.ToolPromptItem[]
): ToolPkg.ToolPromptItem[] {
  return availableTools.filter((tool) => !PLAN_MODE_BLOCKED_TOOL_NAMES.has(tool.name));
}

function appendPlanPromptToPreparedHistory(
  preparedHistory: ToolPkg.PromptTurn[],
  extraPrompt: string
): ToolPkg.PromptTurn[] {
  const nextHistory = preparedHistory.slice();
  const systemIndex = nextHistory.findIndex((turn) => turn.kind === "SYSTEM");
  if (systemIndex < 0) {
    return [
      {
        kind: "SYSTEM",
        content: extraPrompt,
      },
      ...nextHistory,
    ];
  }
  const systemTurn = nextHistory[systemIndex];
  nextHistory[systemIndex] = {
    ...systemTurn,
    content: appendPrompt(systemTurn.content, extraPrompt),
  };
  return nextHistory;
}

export async function onInputMenuToggle(
  event: ToolPkg.InputMenuToggleHookEvent
): Promise<ToolPkg.InputMenuToggleObjectResult | null> {
  try {
    const payload = event.eventPayload;
    const action = payload.action;
    const runtime = payload.runtime;
    const chatId = payload.chatId;
    if ((action !== "create" && action !== "toggle") || !isPlanModeRuntime(runtime) || chatId === undefined) {
      return null;
    }
    const text = resolvePlanModeI18n();
    const enabled = await PlanModeShared.isEnabled(chatId);
    const workspace = await PlanModeShared.resolveWorkspace(chatId, runtime);
    logPlanModeDebug("onInputMenuToggle", {
      action,
      runtime,
      chatId,
      enabled,
      hasWorkspace: !!workspace,
      workspacePath: workspace?.workspacePath,
    });

    if (action === "create") {
      return {
        toggles: [
          {
            id: TOGGLE_ID,
            title: text.menuTitle,
            description: workspace
              ? enabled
                ? text.menuDescriptionEnabled
                : text.menuDescriptionDisabled
              : text.menuDescriptionWorkspaceMissing,
            icon: TOGGLE_ICON,
            isChecked: enabled,
          },
        ],
      };
    }

    if (action === "toggle" && payload.toggleId === TOGGLE_ID) {
      if (enabled) {
        logPlanModeDebug("toggle.disable", { chatId, runtime });
        await PlanModeShared.disable(chatId);
        return null;
      }
      if (!workspace) {
        logPlanModeDebug("toggle.enable_denied_workspace_missing", { chatId, runtime });
        await Tools.System.toast(text.toastWorkspaceRequired);
        return null;
      }
      logPlanModeDebug("toggle.enable", {
        chatId,
        runtime,
        workspacePath: workspace.workspacePath,
        workspaceEnv: workspace.workspaceEnv,
      });
      await PlanModeShared.enable(workspace.chatId);
    }

    return null;
  } catch (error) {
    logPlanModeDebug("onInputMenuToggle.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return null;
  }
}

export async function onChatViewEvent(
  event: ToolPkg.ChatViewHookEvent
): Promise<void> {
  try {
    const payload = event.eventPayload;
    const eventName = event.eventName;
    const runtime = payload.runtime;
    const viewId = payload.viewId;
    const chatId = payload.chatId;
    const workspacePath = payload.workspacePath;
    const workspaceEnv = payload.workspaceEnv;
    const title = payload.title;
    if (
      !isPlanModeRuntime(runtime) ||
      !isNonEmptyString(viewId) ||
      !isNonEmptyString(chatId) ||
      !isNonEmptyString(workspacePath) ||
      typeof title !== "string"
    ) {
      logPlanModeDebug("onChatViewEvent.ignore_invalid_workspace", {
        eventName,
        runtime,
        viewId: typeof viewId === "string" ? viewId : undefined,
        chatId: typeof chatId === "string" ? chatId : undefined,
        workspacePath:
          typeof workspacePath === "string" ? workspacePath : workspacePath === null ? "null" : undefined,
        workspaceEnv:
          typeof workspaceEnv === "string" ? workspaceEnv : workspaceEnv === null ? "null" : undefined,
      });
      return;
    }

    logPlanModeDebug("onChatViewEvent", {
      eventName,
      runtime,
      viewId,
      chatId,
      workspacePath,
      workspaceEnv,
      title,
    });

    if (eventName === "view_closed") {
      await PlanModeShared.removeTrackedChatView(runtime, viewId);
      return;
    }

    await PlanModeShared.upsertTrackedChatView({
      viewId,
      runtime,
      chatId,
      workspacePath,
      workspaceEnv: isNonEmptyString(workspaceEnv) ? workspaceEnv : undefined,
      title,
      updatedAt: Date.now(),
    });
  } catch (error) {
    logPlanModeDebug("onChatViewEvent.error", {
      message: error instanceof Error ? error.message : "error",
    });
  }
}

export async function onSystemPromptCompose(
  event: ToolPkg.SystemPromptComposeHookEvent
): Promise<ToolPkg.PromptHookObjectResult | null> {
  try {
    const payload = event.eventPayload;
    const stage = payload.stage === undefined ? event.eventName : payload.stage;
    if (stage !== "after_compose_system_prompt" || !usesChatPrompt(payload)) {
      return null;
    }

    const useEnglish = payload.useEnglish === true;
    const chatId = payload.chatId;
    const currentPrompt = payload.systemPrompt;
    if (chatId === undefined || currentPrompt === undefined) {
      return null;
    }
    if (await PlanModeShared.isEnabled(chatId)) {
      return {
        systemPrompt: appendPrompt(currentPrompt, buildPlanningModePrompt(useEnglish)),
      };
    }

    const workspace = await PlanModeShared.resolveWorkspace(chatId);
    if (!workspace || !(await PlanModeShared.hasPlanFile(workspace.chatId))) {
      return null;
    }

    return {
      systemPrompt: appendPrompt(currentPrompt, buildExistingPlanPrompt(useEnglish)),
    };
  } catch (error) {
    logPlanModeDebug("onSystemPromptCompose.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return null;
  }
}

export async function onToolPromptCompose(
  event: ToolPkg.ToolPromptComposeHookEvent
): Promise<ToolPkg.PromptHookObjectResult | null> {
  try {
    const payload = event.eventPayload;
    const stage = payload.stage === undefined ? event.eventName : payload.stage;
    if (stage !== "filter_tool_prompt_items" && stage !== "filter_tool_call_tools") {
      return null;
    }

    const chatId = payload.chatId;
    const availableTools = payload.availableTools;
    if (chatId === undefined || availableTools === undefined || !usesChatPrompt(payload)) {
      return null;
    }
    if (!(await PlanModeShared.isEnabled(chatId))) {
      return null;
    }

    const filteredTools = filterPlanModeTools(availableTools);
    logPlanModeDebug("onToolPromptCompose", {
      chatId,
      stage,
      toolCountBefore: availableTools.length,
      toolCountAfter: filteredTools.length,
    });
    return {
      availableTools: filteredTools,
    };
  } catch (error) {
    logPlanModeDebug("onToolPromptCompose.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return null;
  }
}

async function buildPromptFinalizeResult(
  payload: ToolPkg.PromptHookEventPayload
): Promise<ToolPkg.PromptHookObjectResult | null> {
  const chatId = payload.chatId;
  const preparedHistory = payload.preparedHistory;
  if (chatId === undefined || preparedHistory === undefined || !usesChatPrompt(payload)) {
    return null;
  }
  const useEnglish = payload.useEnglish === true;
  if (await PlanModeShared.isEnabled(chatId)) {
    return {
      preparedHistory: appendPlanPromptToPreparedHistory(
        preparedHistory,
        buildPlanningModePrompt(useEnglish)
      ),
    };
  }

  const workspace = await PlanModeShared.resolveWorkspace(chatId);
  if (!workspace || !(await PlanModeShared.hasPlanFile(workspace.chatId))) {
    return null;
  }

  return {
    preparedHistory: appendPlanPromptToPreparedHistory(
      preparedHistory,
      buildExistingPlanPrompt(useEnglish)
    ),
  };
}

export async function onPromptFinalize(
  event: ToolPkg.PromptFinalizeHookEvent
): Promise<ToolPkg.PromptHookObjectResult | null> {
  try {
    const payload = event.eventPayload;
    const stage = payload.stage === undefined ? event.eventName : payload.stage;
    if (stage !== "before_send_to_model") {
      return null;
    }
    return await buildPromptFinalizeResult(payload);
  } catch (error) {
    logPlanModeDebug("onPromptFinalize.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return null;
  }
}

export async function onPromptEstimateFinalize(
  event: ToolPkg.PromptEstimateFinalizeHookEvent
): Promise<ToolPkg.PromptHookObjectResult | null> {
  try {
    const payload = event.eventPayload;
    const stage = payload.stage === undefined ? event.eventName : payload.stage;
    if (stage !== "before_send_to_model") {
      return null;
    }
    return await buildPromptFinalizeResult(payload);
  } catch (error) {
    logPlanModeDebug("onPromptEstimateFinalize.error", {
      message: error instanceof Error ? error.message : "error",
    });
    return null;
  }
}

registerPlanModeIpc();

export function registerToolPkg(): boolean {
  ToolPkg.registerInputMenuTogglePlugin({
    id: MENU_HOOK_ID,
    function: onInputMenuToggle,
  });
  ToolPkg.registerChatViewHook({
    id: CHAT_VIEW_HOOK_ID,
    function: onChatViewEvent,
  });
  ToolPkg.registerSystemPromptComposeHook({
    id: PROMPT_HOOK_ID,
    function: onSystemPromptCompose,
  });
  ToolPkg.registerToolPromptComposeHook({
    id: TOOL_PROMPT_HOOK_ID,
    function: onToolPromptCompose,
  });
  ToolPkg.registerPromptFinalizeHook({
    id: PROMPT_FINALIZE_HOOK_ID,
    function: onPromptFinalize,
  });
  ToolPkg.registerPromptEstimateFinalizeHook({
    id: PROMPT_ESTIMATE_FINALIZE_HOOK_ID,
    function: onPromptEstimateFinalize,
  });
  ToolPkg.registerXmlRenderPlugin({
    id: XML_RENDER_HOOK_ID,
    tag: XML_TAG,
    function: onPlantodoXmlRender,
  });
  ToolPkg.registerXmlRenderPlugin({
    id: PLANASK_XML_RENDER_HOOK_ID,
    tag: PLANASK_XML_TAG,
    function: onPlanaskXmlRender,
  });
  return true;
}
