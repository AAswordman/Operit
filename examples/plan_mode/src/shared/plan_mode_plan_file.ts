import { PLAN_FILE_DIRECTORY_NAME } from "./plan_mode_constants.js";
import { buildPlanFilePath, resolveChatWorkspace, type ChatWorkspaceBinding } from "./plan_mode_workspace.js";

export type PlanFileRecord = ChatWorkspaceBinding & {
  path: string;
  content: string;
};

type PlanFileBinding = ChatWorkspaceBinding & {
  path: string;
};

export function normalizePlanText(content: string): string {
  return content.replace(/\r\n/g, "\n").trim();
}

export function normalizePlanContent(content: string): string {
  const normalized = normalizePlanText(content);
  if (!normalized) {
    throw new Error("plan content is empty");
  }
  return `${normalized}\n`;
}

export function resolvePlanFileBinding(chatId: string): PlanFileBinding | null {
  const binding = resolveChatWorkspace(chatId);
  if (!binding) {
    return null;
  }
  return {
    ...binding,
    path: buildPlanFilePath(binding.workspacePath),
  };
}

export async function hasPlanFile(chatId: string): Promise<boolean> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    return false;
  }
  const result = await Tools.Files.exists(binding.path, "android");
  return result.exists;
}

export async function readPlanFile(chatId: string): Promise<PlanFileRecord | null> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    return null;
  }
  const exists = await Tools.Files.exists(binding.path, "android");
  if (!exists.exists) {
    return null;
  }
  const result = await Tools.Files.read({ path: binding.path, environment: "android" });
  return {
    ...binding,
    content: result.content.replace(/\r\n/g, "\n"),
  };
}

export async function planFileMatchesContent(chatId: string, content: string): Promise<boolean> {
  const normalized = normalizePlanText(content);
  if (!normalized) {
    return false;
  }
  const plan = await readPlanFile(chatId);
  return plan !== null && normalizePlanText(plan.content) === normalized;
}

export async function writePlanFile(chatId: string, content: string): Promise<PlanFileRecord> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    throw new Error("workspace is not bound");
  }
  const normalized = normalizePlanContent(content);
  await Tools.Files.mkdir(`${binding.workspacePath}/${PLAN_FILE_DIRECTORY_NAME}`, true, "android");
  await Tools.Files.write(binding.path, normalized, false, "android");
  return {
    ...binding,
    path: binding.path,
    content: normalized,
  };
}

export async function deletePlanFile(chatId: string): Promise<{
  chatId: string;
  workspacePath: string;
  path: string;
  deleted: boolean;
}> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    throw new Error("workspace is not bound");
  }
  const exists = await Tools.Files.exists(binding.path, "android");
  if (exists.exists) {
    await Tools.Files.deleteFile(binding.path, false, "android");
  }
  return {
    chatId: binding.chatId,
    workspacePath: binding.workspacePath,
    path: binding.path,
    deleted: exists.exists,
  };
}
