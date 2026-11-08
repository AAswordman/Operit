import * as service from "./worldbook_service.js";
import type { WorldBookError } from "./worldbook_service.js";

export type WorldBookReply<T> = { ok: true; value: T } | { ok: false; error: WorldBookError };
const PREFIX = "worldbook.storage.";
let registered = false;
function errorDetails(error: unknown): WorldBookError {
  if (error !== null && typeof error === "object" && "code" in error && "message" in error &&
      typeof error.code === "string" && typeof error.message === "string") {
    return { code: error.code, message: error.message, details: "details" in error ? error.details : undefined };
  }
  if (error instanceof Error) return { code: "WORLD_BOOK_STORAGE_ERROR", message: error.message };
  throw error;
}
function bind<P, R>(name: string, handler: (payload: P) => Promise<R>): void {
  ToolPkg.ipc.on<P, WorldBookReply<R>>(PREFIX + name, async (payload) => {
    try {
      return { ok: true, value: await handler(payload) };
    } catch (error) {
      console.error("World book UI operation failed", name, error);
      return { ok: false, error: errorDetails(error) };
    }
  });
}
export function registerWorldBookStorageService(): void {
  if (registered) return;
  registered = true;
  bind("listEntries", () => service.listWorldBookEntries());
  bind("getEntry", (id: string) => service.getWorldBookEntry(id));
  bind("createEntry", (params: service.WorldBookMutationParams) => service.createWorldBookEntry(params));
  bind("importEntries", (params: service.WorldBookImportParams) => service.importWorldBookEntries(params));
  bind("updateEntry", (params: service.WorldBookMutationParams) => service.updateWorldBookEntry(params));
  bind("deleteEntry", (id: string) => service.deleteWorldBookEntry(id));
  bind("toggleEntry", (id: string) => service.toggleWorldBookEntry(id));
  bind("listCharacterCards", () => service.listWorldBookCharacterCards());
  bind("listGroups", () => service.listWorldBookGroups());
  bind("createGroup", (params: service.WorldBookGroupCreateParams) => service.createWorldBookGroup(params));
  bind("deleteGroup", (params: { id: string; deleteEntries: boolean }) => service.deleteWorldBookGroup(params.id, params.deleteEntries));
  bind("copyEntry", (params: { entryId: string; targetGroupId: string }) => service.copyEntryToGroup(params.entryId, params.targetGroupId));
  bind("updateGroup", (params: service.WorldBookGroupMutationParams) => service.updateWorldBookGroup(params));
}
