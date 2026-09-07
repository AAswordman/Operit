import * as planModeI18n from "./plan_mode_i18n.js";

export type StartPlanImplementationResult = {
  success: boolean;
  error?: string;
  alreadyStarted?: boolean;
};

export const PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL = "plan_mode.start_implementation";
export const PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL = "plan_mode.is_plan_started";

/**
 * Renderer state is rebuilt from scratch whenever the chat view is recreated, so the
 * plan card asks the plugin runtime whether this exact plan was already handed off.
 */
export async function isPlanImplementationStarted(planContent: string): Promise<boolean> {
  const normalizedPlanContent = planContent.trim();
  if (!normalizedPlanContent) {
    return false;
  }
  try {
    return await ToolPkg.ipc.call<string, boolean>(
      PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL,
      normalizedPlanContent
    );
  } catch (error) {
    const errorText = error instanceof Error
      ? error.message || "error"
      : (typeof error === "string" || error == null ? error || "error" : "error");
    console.error(
      `[plan_mode_execution] isPlanImplementationStarted failed: channel=${PLAN_MODE_IS_PLAN_STARTED_IPC_CHANNEL}, planLength=${normalizedPlanContent.length}, error=${errorText}`
    );
    return false;
  }
}

export async function startPlanImplementation(
  planContent: string
): Promise<StartPlanImplementationResult> {
  const text = planModeI18n.resolvePlanModeI18n();
  const normalizedPlanContent = planContent.trim();
  if (!normalizedPlanContent) {
    const message = text.toastPlanEmpty;
    await Tools.System.toast(message);
    return { success: false, error: message };
  }

  try {
    return await ToolPkg.ipc.call<string, StartPlanImplementationResult>(
      PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL,
      normalizedPlanContent
    );
  } catch (error) {
    const errorText = error instanceof Error
      ? error.message || "error"
      : (typeof error === "string" || error == null ? error || "error" : "error");
    const message = `${text.toastPlanWriteFailedPrefix}${errorText}`;
    console.error(
      `[plan_mode_execution] startPlanImplementation failed: channel=${PLAN_MODE_START_IMPLEMENTATION_IPC_CHANNEL}, planLength=${normalizedPlanContent.length}, error=${errorText}`
    );
    await Tools.System.toast(message);
    return { success: false, error: message };
  }
}
