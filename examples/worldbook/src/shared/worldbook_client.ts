import type {
  CharacterCardOption, WorldBookEntry, WorldBookListEntry, WorldBookMutationParams,
  WorldBookImportParams, WorldBookImportResult, WorldBookGroup,
  WorldBookGroupCreateParams, WorldBookGroupMutationParams
} from "./worldbook_service.js";
import type { WorldBookReply } from "./worldbook_rpc.js";
export type * from "./worldbook_service.js";

async function call<P, R>(name: string, payload: P): Promise<R> {
  const reply = await ToolPkg.ipc.call<P, WorldBookReply<R>>("worldbook.storage." + name, payload, { targetRuntime: "main" });
  if (reply.ok === false) throw reply.error;
  return reply.value;
}
export function listWorldBookEntries(): Promise<WorldBookListEntry[]> { return call("listEntries", null); }
export function getWorldBookEntry(id: string): Promise<WorldBookEntry> { return call("getEntry", id); }
export function createWorldBookEntry(params: WorldBookMutationParams): Promise<WorldBookEntry> { return call("createEntry", params); }
export function importWorldBookEntries(params: WorldBookImportParams): Promise<WorldBookImportResult> { return call("importEntries", params); }
export function updateWorldBookEntry(params: WorldBookMutationParams): Promise<WorldBookEntry> { return call("updateEntry", params); }
export function deleteWorldBookEntry(id: string): Promise<WorldBookEntry> { return call("deleteEntry", id); }
export function toggleWorldBookEntry(id: string): Promise<WorldBookListEntry> { return call("toggleEntry", id); }
export function listWorldBookCharacterCards(): Promise<CharacterCardOption[]> { return call("listCharacterCards", null); }
export function listWorldBookGroups(): Promise<WorldBookGroup[]> { return call("listGroups", null); }
export function createWorldBookGroup(params: WorldBookGroupCreateParams): Promise<WorldBookGroup> { return call("createGroup", params); }
export function deleteWorldBookGroup(id: string, deleteEntries: boolean): Promise<{ removedGroup: WorldBookGroup; removedEntries: WorldBookEntry[] }> { return call("deleteGroup", { id, deleteEntries }); }
export function copyEntryToGroup(entryId: string, targetGroupId: string): Promise<{ newEntry: WorldBookEntry }> { return call("copyEntry", { entryId, targetGroupId }); }
export function updateWorldBookGroup(params: WorldBookGroupMutationParams): Promise<WorldBookGroup> { return call("updateGroup", params); }
