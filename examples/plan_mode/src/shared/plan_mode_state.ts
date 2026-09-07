export type PlanModeRuntime = "main" | "floating";

export type PlanModeTrackedChatView = {
  viewId: string;
  runtime: PlanModeRuntime;
  chatId: string;
  workspacePath: string;
  workspaceEnv?: string;
  title: string;
  updatedAt: number;
};

type PlanModeState = {
  enabledChatIds: Record<string, true>;
  startedPlanKeys: Record<string, true>;
  trackedViewsByChatId: Record<string, PlanModeTrackedChatView>;
};

const state: PlanModeState = {
  enabledChatIds: {},
  startedPlanKeys: {},
  trackedViewsByChatId: {},
};

function cloneTrackedChatView(view: PlanModeTrackedChatView): PlanModeTrackedChatView {
  return {
    viewId: view.viewId,
    runtime: view.runtime,
    chatId: view.chatId,
    workspacePath: view.workspacePath,
    workspaceEnv: view.workspaceEnv,
    title: view.title,
    updatedAt: view.updatedAt,
  };
}

function cloneTrackedViewsByChatId(): Record<string, PlanModeTrackedChatView> {
  const next: Record<string, PlanModeTrackedChatView> = {};
  Object.entries(state.trackedViewsByChatId).forEach(([chatId, view]) => {
    next[chatId] = cloneTrackedChatView(view);
  });
  return next;
}

export function readPlanModeStateSnapshot(): PlanModeState {
  return {
    enabledChatIds: { ...state.enabledChatIds },
    startedPlanKeys: { ...state.startedPlanKeys },
    trackedViewsByChatId: cloneTrackedViewsByChatId(),
  };
}

export async function readPlanModeStateAsync(): Promise<PlanModeState> {
  return readPlanModeStateSnapshot();
}

export function isPlanModeEnabledInState(chatId: string): boolean {
  return state.enabledChatIds[chatId] === true;
}

export async function setPlanModeEnabledForChatAsync(
  chatId: string,
  enabled: boolean
): Promise<void> {
  if (enabled) {
    state.enabledChatIds[chatId] = true;
    return;
  }
  delete state.enabledChatIds[chatId];
}

export function isPlanStartRecorded(planKey: string): boolean {
  return state.startedPlanKeys[planKey] === true;
}

export function recordPlanStart(planKey: string): void {
  state.startedPlanKeys[planKey] = true;
}

export function forgetPlanStart(planKey: string): void {
  delete state.startedPlanKeys[planKey];
}

export function readActiveChatViewForRuntime(runtime: PlanModeRuntime): PlanModeTrackedChatView | null {
  const view = Object.values(state.trackedViewsByChatId).find((item) => item.runtime === runtime);
  return view ? cloneTrackedChatView(view) : null;
}

export function readTrackedChatViewByChatId(chatId: string): PlanModeTrackedChatView | null {
  const view = state.trackedViewsByChatId[chatId];
  return view ? cloneTrackedChatView(view) : null;
}

export function readSingleActiveChatView(): PlanModeTrackedChatView | null {
  const views = Object.values(state.trackedViewsByChatId);
  if (views.length !== 1) {
    return null;
  }
  return cloneTrackedChatView(views[0]);
}

export async function upsertTrackedChatViewAsync(view: PlanModeTrackedChatView): Promise<void> {
  Object.entries(state.trackedViewsByChatId).forEach(([chatId, trackedView]) => {
    if (trackedView.runtime === view.runtime && chatId !== view.chatId) {
      delete state.trackedViewsByChatId[chatId];
    }
  });
  state.trackedViewsByChatId[view.chatId] = cloneTrackedChatView(view);
}

export async function removeTrackedChatViewAsync(
  runtime: PlanModeRuntime,
  viewId: string
): Promise<void> {
  Object.entries(state.trackedViewsByChatId).forEach(([chatId, trackedView]) => {
    if (trackedView.runtime === runtime && trackedView.viewId === viewId) {
      delete state.trackedViewsByChatId[chatId];
    }
  });
}
