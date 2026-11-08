"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { installMockRuntime, loadWorldBookModules } = require("../tests-dist/support/mock_runtime.js");
function setup() {
  const runtime = installMockRuntime();
  return { runtime, ...loadWorldBookModules() };
}
function entry(name) { return { name, content: `content ${name}`, keywords: "test" }; }
function pending(runtime) {
  return fs.readdirSync(runtime.packageConfigDir).filter((name) => name.includes(".pending-"));
}
for (const invalid of ["", "[broken", "{}", "null"]) {
  test(`create and import preserve unreadable entries: ${JSON.stringify(invalid)}`, async () => {
    const { runtime, storage, service } = setup();
    await storage.ensureWorldBookStorage();
    runtime.writePackageFile("entries.json", invalid);
    await assert.rejects(service.createWorldBookEntry(entry("new")), /世界书/);
    await assert.rejects(service.importWorldBookEntries({ content: JSON.stringify([entry("import")]) }), /世界书/);
    assert.equal(runtime.readPackageFile("entries.json"), invalid);
  });
}
test("transient read failure stops creation and the queue accepts the next explicit operation", async () => {
  const { runtime, storage, service } = setup();
  await service.createWorldBookEntry(entry("original"));
  const original = runtime.readPackageFile("entries.json");
  const read = Tools.Files.read;
  Tools.Files.read = async (target) => {
    if (target === storage.getWorldBookFile()) throw new Error("injected read failure");
    return read(target);
  };
  try {
    await assert.rejects(service.createWorldBookEntry(entry("blocked")), /读取失败/);
    assert.equal(runtime.readPackageFile("entries.json"), original);
  } finally { Tools.Files.read = read; }
  await service.createWorldBookEntry(entry("accepted"));
  assert.equal((await service.listWorldBookEntries()).length, 2);
});
for (const stage of ["write", "verify", "fsync", "rename"]) {
  test(`${stage} failure preserves the live file and removes this operation's temporary file`, async () => {
    const { runtime, service } = setup();
    await service.createWorldBookEntry(entry("original"));
    const original = runtime.readPackageFile("entries.json");
    const write = Tools.Files.write, read = Tools.Files.read, callStatic = Java.callStatic;
    Tools.Files.write = async (target, content, ...args) => {
      if (stage === "write" && target.includes("entries.json.pending-")) {
        await write(target, content.slice(0, 5), ...args);
        throw new Error("injected interrupted write");
      }
      return write(target, content, ...args);
    };
    Tools.Files.read = async (target) => {
      const result = await read(target);
      return stage === "verify" && target.includes(".pending-") ? { ...result, content: "mismatch" } : result;
    };
    Java.callStatic = (className, method, ...args) => {
      if (method === stage) throw new Error(`injected ${stage} failure`);
      return callStatic(className, method, ...args);
    };
    try {
      await assert.rejects(service.createWorldBookEntry(entry("blocked")), /保存失败/);
      assert.equal(runtime.readPackageFile("entries.json"), original);
      assert.deepEqual(pending(runtime), []);
    } finally {
      Tools.Files.write = write; Tools.Files.read = read; Java.callStatic = callStatic;
    }
    await service.createWorldBookEntry(entry("after failure"));
    assert.equal((await service.listWorldBookEntries()).length, 2);
  });
}
test("UI IPC creates interleaved with tool creates and imports retain every entry", async () => {
  const { service, client } = setup();
  const operations = [];
  for (let i = 0; i < 18; i++) {
    operations.push(client.createWorldBookEntry(entry(`ui-${i}`)));
    operations.push(service.createWorldBookEntry(entry(`tool-${i}`)));
    operations.push(service.importWorldBookEntries({ content: JSON.stringify([entry(`import-${i}`)]) }));
  }
  await Promise.all(operations);
  const entries = await client.listWorldBookEntries();
  assert.equal(entries.length, 54);
  assert.equal(new Set(entries.map((item) => item.id)).size, 54);
  assert.equal(new Set(entries.map((item) => item.name)).size, 54);
});
test("an IPC storage error reaches the UI without changing the original file", async () => {
  const { runtime, storage, client } = setup();
  await storage.ensureWorldBookStorage();
  runtime.writePackageFile("entries.json", "broken");
  await assert.rejects(client.createWorldBookEntry(entry("ui")), (error) => {
    assert.equal(error.code, "WORLD_BOOK_STORAGE_ERROR");
    assert.match(error.message, /读取失败/);
    return true;
  });
  assert.equal(runtime.readPackageFile("entries.json"), "broken");
});
test("a corrupt groups file blocks deletion and copying before entries are changed", async () => {
  const { runtime, service } = setup();
  const originalEntry = await service.createWorldBookEntry(entry("original"));
  const original = runtime.readPackageFile("entries.json");
  runtime.writePackageFile("groups.json", "not json");
  await assert.rejects(service.deleteWorldBookEntry(originalEntry.id), /读取失败/);
  await assert.rejects(service.copyEntryToGroup(originalEntry.id, "missing"), /读取失败/);
  assert.equal(runtime.readPackageFile("entries.json"), original);
  assert.equal(runtime.readPackageFile("groups.json"), "not json");
});
test("deleting a group preserves entries referenced by another group", async () => {
  const { service } = setup();
  const shared = await service.createWorldBookEntry(entry("shared"));
  const exclusive = await service.createWorldBookEntry(entry("exclusive"));
  const first = await service.createWorldBookGroup({ name: "first", entry_ids: [shared.id, exclusive.id], character_card_ids: [] });
  const second = await service.createWorldBookGroup({ name: "second", entry_ids: [shared.id], character_card_ids: [] });
  const result = await service.deleteWorldBookGroup(first.id, true);
  assert.deepEqual(result.removedEntries.map((item) => item.id), [exclusive.id]);
  assert.deepEqual((await service.listWorldBookEntries()).map((item) => item.id), [shared.id]);
  const groups = await service.listWorldBookGroups();
  assert.equal(groups[0].id, second.id);
  assert.deepEqual(groups[0].entry_ids, [shared.id]);
});
for (const invalid of ["", "broken", "{}", '{"global_variables":{},"character_variables":{},"chats":{"c":{}}}']) {
  test(`invalid variables remain unchanged: ${JSON.stringify(invalid)}`, async () => {
    const { runtime, storage, variables } = setup();
    await storage.ensureWorldBookStorage();
    runtime.writePackageFile("variables.json", invalid);
    await assert.rejects(variables.syncWorldBookVariableContext("chat_1", [], "card_1"), /世界书/);
    assert.equal(runtime.readPackageFile("variables.json"), invalid);
  });
}
test("concurrent variable synchronization keeps both chat states", async () => {
  const { runtime, variables } = setup();
  await Promise.all([
    variables.syncWorldBookVariableContext("chat_a", [], "card_1"),
    variables.syncWorldBookVariableContext("chat_b", [], "card_1")
  ]);
  assert.deepEqual(Object.keys(JSON.parse(runtime.readPackageFile("variables.json")).chats).sort(), ["chat_a", "chat_b"]);
});
test("chat history failure preserves the previous variable store", async () => {
  const { runtime, storage, variables } = setup();
  await storage.ensureWorldBookStorage();
  const original = runtime.readPackageFile("variables.json"), getMessages = Tools.Chat.getMessages;
  Tools.Chat.getMessages = async () => { throw new Error("history unavailable"); };
  try {
    await assert.rejects(variables.syncWorldBookVariableContext("chat_1", [], "card_1"), /history unavailable/);
    assert.equal(runtime.readPackageFile("variables.json"), original);
  } finally { Tools.Chat.getMessages = getMessages; }
});
function redirectLegacy(runtime) {
  const legacy = path.join(runtime.configDir, "legacy");
  fs.mkdirSync(legacy, { recursive: true });
  const prefix = "/sdcard/Download/Operit/worldbook";
  for (const method of ["exists", "read", "deleteFile"]) {
    const original = Tools.Files[method];
    Tools.Files[method] = (target, ...args) => original(target.startsWith(prefix) ? legacy + target.slice(prefix.length) : target, ...args);
  }
  return legacy;
}
test("concurrent initialization migrates once and retains all legacy files", async () => {
  const { runtime, storage } = setup(), legacy = redirectLegacy(runtime);
  const data = JSON.stringify([{ id: "legacy", ...entry("legacy") }]);
  fs.writeFileSync(path.join(legacy, "entries.json"), data);
  fs.writeFileSync(path.join(legacy, "notes.txt"), "keep");
  let writes = 0;
  const write = Tools.Files.write;
  Tools.Files.write = (target, ...args) => {
    if (target.includes("entries.json.pending-")) writes++;
    return write(target, ...args);
  };
  await Promise.all(Array.from({ length: 12 }, () => storage.ensureWorldBookStorage()));
  assert.equal(writes, 1);
  assert.equal(JSON.parse(runtime.readPackageFile("entries.json"))[0].id, "legacy");
  assert.equal(fs.readFileSync(path.join(legacy, "entries.json"), "utf8"), data);
  assert.equal(fs.readFileSync(path.join(legacy, "notes.txt"), "utf8"), "keep");
});
test("corrupt legacy data blocks initialization without creating an empty entries file", async () => {
  const { runtime, storage } = setup(), legacy = redirectLegacy(runtime);
  fs.writeFileSync(path.join(legacy, "entries.json"), "broken");
  await assert.rejects(storage.ensureWorldBookStorage(), /读取失败/);
  assert.equal(fs.existsSync(path.join(runtime.packageConfigDir, "entries.json")), false);
  assert.equal(fs.readFileSync(path.join(legacy, "entries.json"), "utf8"), "broken");
});
