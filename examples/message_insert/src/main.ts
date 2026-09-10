import toolboxUI from "./ui/index.ui.js";
import {
  appendExtraInfoToMessage,
  getAppContext,
  getExtraInfoInjectionEnabled,
  loadSettings,
  logExtraInfoInjectionError,
  logExtraInfoInjectionInfo,
  resolveExtraInfoI18n,
  setExtraInfoInjectionEnabled,
} from "./shared";

const EnhancedAIService = Java.com.ai.assistance.operit.api.chat.EnhancedAIService;
const InputProcessingStateBase = "com.ai.assistance.operit.data.model.InputProcessingState$";

function resolveInjectionStatusText(): string {
  const locale = typeof getLang === "function" ? String(getLang() || "").trim().toLowerCase() : "";
  return locale.startsWith("en")
    ? "Injecting extra info"
    : "正在注入额外信息";
}

function pushInjectionProcessingState(chatId?: string): void {
  try {
    const context = getAppContext();
    if (!context) {
      logExtraInfoInjectionInfo("processing_state.skipped", "reason=application_context_unavailable");
      return;
    }
    const resolvedChatId = String(chatId ?? getChatId() ?? "").trim();
    const service = resolvedChatId
      ? EnhancedAIService.getChatInstance(context, resolvedChatId)
      : EnhancedAIService.getInstance(context);
    const state = Java.newInstance(
      InputProcessingStateBase + "Processing",
      resolveInjectionStatusText()
    );
    service.setInputProcessingState(state);
  } catch (error) {
    logExtraInfoInjectionError("processing_state.failed", error);
  }
}

async function appendExtraInfoWithStatus(
  processedInput: string,
  chatId?: string,
  activePrompt?: ToolPkg.ActivePromptSnapshot
) {
  const startedAt = Date.now();
  pushInjectionProcessingState(chatId);
  logExtraInfoInjectionInfo(
    "injection.started",
    `chat_id_present=${Boolean(chatId)} active_prompt_type=${activePrompt?.type ?? "none"}`
  );

  try {
    const result = await appendExtraInfoToMessage(
      processedInput,
      chatId || undefined,
      activePrompt
    );
    logExtraInfoInjectionInfo(
      "injection.completed",
      `injected=${Boolean(result)} elapsed_ms=${Date.now() - startedAt}`
    );
    return result;
  } catch (error) {
    logExtraInfoInjectionError(
      "injection.failed",
      error,
      `elapsed_ms=${Date.now() - startedAt}`
    );
    throw error;
  }
}

function resolveHookActivePrompt(
  input: ToolPkg.PromptInputHookEvent | ToolPkg.PromptFinalizeHookEvent
): ToolPkg.ActivePromptSnapshot | undefined {
  return input.eventPayload.metadata?.activePrompt;
}

export function registerToolPkg(): boolean {
  ToolPkg.registerToolboxUiModule({
    id: "message_insert_settings",
    runtime: "compose_dsl",
    screen: toolboxUI,
    params: {},
    title: {
      zh: "额外信息注入",
      en: "Extra Info Injection",
    },
  });

  ToolPkg.registerPromptInputHook({
    id: "message_insert_prompt_input",
    function: onPromptInput,
  });

  ToolPkg.registerPromptFinalizeHook({
    id: "message_insert_prompt_finalize",
    function: onPromptFinalize,
  });

  ToolPkg.registerInputMenuTogglePlugin({
    id: "message_insert_input_menu_toggle",
    function: onInputMenuToggle,
  });

  logExtraInfoInjectionInfo("plugin.registered");
  return true;
}

// Prompt hooks run for multiple pipeline stages, so normal no-op branches stay silent on this hot path.
export async function onPromptInput(
  input: ToolPkg.PromptInputHookEvent
) {
  const stage = String(input.eventPayload.stage ?? input.eventName ?? "");
  if (stage !== "before_process") {
    return null;
  }

  const settings = loadSettings();
  if (!settings.persistInjectedContent) {
    return null;
  }

  const processedInput = String(
    input.eventPayload.processedInput ?? input.eventPayload.rawInput ?? ""
  );
  if (!processedInput.trim()) {
    return null;
  }

  const chatId = String(input.eventPayload.chatId ?? getChatId() ?? "").trim();
  const activePrompt = resolveHookActivePrompt(input);
  return appendExtraInfoWithStatus(
    processedInput,
    chatId || undefined,
    activePrompt
  );
}

export async function onPromptFinalize(
  input: ToolPkg.PromptFinalizeHookEvent
) {
  const stage = String(input.eventPayload.stage ?? input.eventName ?? "");
  if (stage !== "before_send_to_model") {
    return null;
  }

  const settings = loadSettings();
  if (settings.persistInjectedContent) {
    return null;
  }

  const processedInput = String(
    input.eventPayload.processedInput ?? input.eventPayload.rawInput ?? ""
  );
  if (!processedInput.trim()) {
    return null;
  }

  const chatId = String(input.eventPayload.chatId ?? getChatId() ?? "").trim();
  const activePrompt = resolveHookActivePrompt(input);
  return appendExtraInfoWithStatus(
    processedInput,
    chatId || undefined,
    activePrompt
  );
}

// Menu definitions can be requested repeatedly during UI refreshes; only user-triggered changes are logged.
export function onInputMenuToggle(
  input: ToolPkg.InputMenuToggleHookEvent
): ToolPkg.InputMenuToggleDefinitionResult[] {
  const action = String(input.eventPayload.action ?? "").toLowerCase();

  if (action === "toggle") {
    const enabled = !getExtraInfoInjectionEnabled();
    setExtraInfoInjectionEnabled(enabled);
    logExtraInfoInjectionInfo("menu_toggle.updated", `enabled=${enabled}`);
    return [];
  }

  if (action !== "create") {
    return [];
  }

  const text = resolveExtraInfoI18n();
  return [
    {
      id: "message_extra_info_injection",
      title: text.menuTitle,
      description: text.menuDescription,
      isChecked: getExtraInfoInjectionEnabled(),
    },
  ];
}
