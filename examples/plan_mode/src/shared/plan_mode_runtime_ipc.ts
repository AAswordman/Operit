import {
  disablePlanMode,
  enablePlanModeForChat,
  isPlanModeEnabledForChat,
} from "./plan_mode_mode.js";
import {
  hasPlanFile,
  normalizePlanText,
  planFileMatchesContent,
  writePlanFile,
  type PlanFileRecord,
} from "./plan_mode_plan_file.js";
import {
  forgetPlanStart as forgetPlanStartRecord,
  isPlanStartRecorded,
  readSingleActiveChatView,
  recordPlanStart,
  removeTrackedChatViewAsync,
  upsertTrackedChatViewAsync,
  type PlanModeRuntime,
  type PlanModeTrackedChatView,
} from "./plan_mode_state.js";
import {
  resolveChatWorkspace,
  type ChatWorkspaceBinding,
} from "./plan_mode_workspace.js";

type SharedMethodMetadata = {
  channel: string;
  methodName: string;
  original: (...args: readonly unknown[]) => unknown;
};

const sharedMethodMetadataMap = new WeakMap<object, SharedMethodMetadata[]>();

function readSharedMethodMetadata(target: object): SharedMethodMetadata[] {
  return sharedMethodMetadataMap.get(target) ?? [];
}

function appendSharedMethodMetadata(target: object, metadata: SharedMethodMetadata): void {
  const existing = readSharedMethodMetadata(target);
  sharedMethodMetadataMap.set(target, [...existing, metadata]);
}

function Shared(channel: string) {
  return function <
    TTarget extends object,
    TArgs extends readonly unknown[],
    TResult
  >(
    target: TTarget,
    propertyKey: string,
    descriptor: TypedPropertyDescriptor<(...args: TArgs) => Promise<TResult>>
  ): void {
    const original = descriptor.value;
    if (!original) {
      throw new Error(`@Shared can only decorate methods: ${propertyKey}`);
    }
    appendSharedMethodMetadata(target, {
      channel,
      methodName: propertyKey,
      original: original as (...args: readonly unknown[]) => unknown,
    });
    descriptor.value = (async (...args: TArgs): Promise<TResult> => {
      return await ToolPkg.ipc.call<TArgs, TResult>(channel, args);
    }) as (...args: TArgs) => Promise<TResult>;
  };
}

export function registerSharedMethods(target: object): void {
  const entries = readSharedMethodMetadata(target);
  entries.forEach((entry) => {
    ToolPkg.ipc.on<readonly unknown[], unknown>(entry.channel, async (payload) => {
      const args = Array.isArray(payload) ? payload : [];
      return await entry.original.apply(target, args);
    });
  });
}

export const PLAN_MODE_GET_SINGLE_ACTIVE_CHAT_VIEW_IPC_CHANNEL = "plan_mode.get_single_active_chat_view";
export const PLAN_MODE_IS_ENABLED_IPC_CHANNEL = "plan_mode.is_enabled";
export const PLAN_MODE_ENABLE_IPC_CHANNEL = "plan_mode.enable";
export const PLAN_MODE_DISABLE_IPC_CHANNEL = "plan_mode.disable";
export const PLAN_MODE_RESOLVE_WORKSPACE_IPC_CHANNEL = "plan_mode.resolve_workspace";
export const PLAN_MODE_HAS_PLAN_FILE_IPC_CHANNEL = "plan_mode.has_plan_file";
export const PLAN_MODE_WRITE_PLAN_FILE_IPC_CHANNEL = "plan_mode.write_plan_file";
export const PLAN_MODE_IS_PLAN_STARTED_SHARED_IPC_CHANNEL = "plan_mode.shared_is_plan_started";
export const PLAN_MODE_CLAIM_PLAN_START_IPC_CHANNEL = "plan_mode.claim_plan_start";
export const PLAN_MODE_FORGET_PLAN_START_IPC_CHANNEL = "plan_mode.forget_plan_start";
export const PLAN_MODE_UPSERT_TRACKED_CHAT_VIEW_IPC_CHANNEL = "plan_mode.upsert_tracked_chat_view";
export const PLAN_MODE_REMOVE_TRACKED_CHAT_VIEW_IPC_CHANNEL = "plan_mode.remove_tracked_chat_view";

export class PlanModeShared {
  @Shared(PLAN_MODE_GET_SINGLE_ACTIVE_CHAT_VIEW_IPC_CHANNEL)
  static async getSingleActiveChatView(): Promise<PlanModeTrackedChatView | null> {
    return readSingleActiveChatView();
  }

  @Shared(PLAN_MODE_IS_ENABLED_IPC_CHANNEL)
  static async isEnabled(chatId: string): Promise<boolean> {
    return isPlanModeEnabledForChat(chatId);
  }

  @Shared(PLAN_MODE_ENABLE_IPC_CHANNEL)
  static async enable(chatId: string): Promise<void> {
    await enablePlanModeForChat(chatId);
  }

  @Shared(PLAN_MODE_DISABLE_IPC_CHANNEL)
  static async disable(chatId: string): Promise<void> {
    await disablePlanMode(chatId);
  }

  @Shared(PLAN_MODE_RESOLVE_WORKSPACE_IPC_CHANNEL)
  static async resolveWorkspace(
    chatId: string,
    runtime?: PlanModeRuntime
  ): Promise<ChatWorkspaceBinding | null> {
    return resolveChatWorkspace(chatId, runtime);
  }

  @Shared(PLAN_MODE_HAS_PLAN_FILE_IPC_CHANNEL)
  static async hasPlanFile(chatId: string): Promise<boolean> {
    return await hasPlanFile(chatId);
  }

  @Shared(PLAN_MODE_WRITE_PLAN_FILE_IPC_CHANNEL)
  static async writePlanFile(chatId: string, content: string): Promise<PlanFileRecord> {
    return await writePlanFile(chatId, content);
  }

  /**
   * A plan counts as started once this session handed it off, or once the workspace plan
   * file still holds exactly this plan because an earlier session started it.
   */
  @Shared(PLAN_MODE_IS_PLAN_STARTED_SHARED_IPC_CHANNEL)
  static async isPlanStarted(planContent: string): Promise<boolean> {
    const planKey = normalizePlanText(planContent);
    if (!planKey) {
      return false;
    }
    if (isPlanStartRecorded(planKey)) {
      return true;
    }
    const view = readSingleActiveChatView();
    if (!view) {
      return false;
    }
    return await planFileMatchesContent(view.chatId, planKey);
  }

  @Shared(PLAN_MODE_CLAIM_PLAN_START_IPC_CHANNEL)
  static async claimPlanStart(planContent: string): Promise<boolean> {
    const planKey = normalizePlanText(planContent);
    if (!planKey) {
      return false;
    }
    if (isPlanStartRecorded(planKey)) {
      return false;
    }
    // Record before awaiting so a second tap cannot slip through the same check.
    recordPlanStart(planKey);
    const view = readSingleActiveChatView();
    if (view && (await planFileMatchesContent(view.chatId, planKey))) {
      return false;
    }
    return true;
  }

  @Shared(PLAN_MODE_FORGET_PLAN_START_IPC_CHANNEL)
  static async forgetPlanStart(planContent: string): Promise<void> {
    forgetPlanStartRecord(normalizePlanText(planContent));
  }

  @Shared(PLAN_MODE_UPSERT_TRACKED_CHAT_VIEW_IPC_CHANNEL)
  static async upsertTrackedChatView(view: PlanModeTrackedChatView): Promise<void> {
    await upsertTrackedChatViewAsync(view);
  }

  @Shared(PLAN_MODE_REMOVE_TRACKED_CHAT_VIEW_IPC_CHANNEL)
  static async removeTrackedChatView(runtime: PlanModeRuntime, viewId: string): Promise<void> {
    await removeTrackedChatViewAsync(runtime, viewId);
  }
}
