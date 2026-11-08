"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listWorldBookEntries = listWorldBookEntries;
exports.getWorldBookEntry = getWorldBookEntry;
exports.createWorldBookEntry = createWorldBookEntry;
exports.importWorldBookEntries = importWorldBookEntries;
exports.updateWorldBookEntry = updateWorldBookEntry;
exports.deleteWorldBookEntry = deleteWorldBookEntry;
exports.toggleWorldBookEntry = toggleWorldBookEntry;
exports.listWorldBookCharacterCards = listWorldBookCharacterCards;
exports.listWorldBookGroups = listWorldBookGroups;
exports.createWorldBookGroup = createWorldBookGroup;
exports.deleteWorldBookGroup = deleteWorldBookGroup;
exports.copyEntryToGroup = copyEntryToGroup;
exports.updateWorldBookGroup = updateWorldBookGroup;
async function call(name, payload) {
    const reply = await ToolPkg.ipc.call("worldbook.storage." + name, payload, { targetRuntime: "main" });
    if (reply.ok === false)
        throw reply.error;
    return reply.value;
}
function listWorldBookEntries() { return call("listEntries", null); }
function getWorldBookEntry(id) { return call("getEntry", id); }
function createWorldBookEntry(params) { return call("createEntry", params); }
function importWorldBookEntries(params) { return call("importEntries", params); }
function updateWorldBookEntry(params) { return call("updateEntry", params); }
function deleteWorldBookEntry(id) { return call("deleteEntry", id); }
function toggleWorldBookEntry(id) { return call("toggleEntry", id); }
function listWorldBookCharacterCards() { return call("listCharacterCards", null); }
function listWorldBookGroups() { return call("listGroups", null); }
function createWorldBookGroup(params) { return call("createGroup", params); }
function deleteWorldBookGroup(id, deleteEntries) { return call("deleteGroup", { id, deleteEntries }); }
function copyEntryToGroup(entryId, targetGroupId) { return call("copyEntry", { entryId, targetGroupId }); }
function updateWorldBookGroup(params) { return call("updateGroup", params); }
