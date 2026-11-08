"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getWorldBookDir = getWorldBookDir;
exports.getWorldBookFile = getWorldBookFile;
exports.getWorldBookGroupsFile = getWorldBookGroupsFile;
exports.withWorldBookStorage = withWorldBookStorage;
exports.readWorldBookJson = readWorldBookJson;
exports.writeWorldBookJson = writeWorldBookJson;
exports.ensureWorldBookStorage = ensureWorldBookStorage;
exports.readWorldBookEntries = readWorldBookEntries;
exports.writeWorldBookEntries = writeWorldBookEntries;
exports.readWorldBookGroups = readWorldBookGroups;
exports.writeWorldBookGroups = writeWorldBookGroups;
const LEGACY_WORLD_BOOK_DIR = "/sdcard/Download/Operit/worldbook";
const LEGACY_WORLD_BOOK_FILE = `${LEGACY_WORLD_BOOK_DIR}/entries.json`;
let initialization;
let storageQueue = Promise.resolve();
function getWorldBookDir() {
    return ToolPkg.getConfigDir();
}
function getWorldBookFile() {
    return `${getWorldBookDir()}/entries.json`;
}
function getWorldBookGroupsFile() {
    return `${getWorldBookDir()}/groups.json`;
}
/** All mutations run in the package main runtime, including requests from the UI. */
async function withWorldBookStorage(operation) {
    const previous = storageQueue;
    let release;
    storageQueue = new Promise((resolve) => { release = resolve; });
    await previous;
    try {
        await ensureWorldBookStorage();
        return await operation();
    }
    finally {
        release();
    }
}
async function readWorldBookJson(path) {
    try {
        const file = await Tools.Files.read(path);
        if (typeof file.content !== "string" || file.content.trim().length === 0) {
            throw new Error("文件为空或未返回内容");
        }
        const parsed = JSON.parse(file.content);
        return parsed;
    }
    catch (error) {
        console.error("World book data read failed", path, error);
        throw new Error(`世界书数据读取失败，已停止本次操作以避免覆盖原数据：${path}`);
    }
}
function requireArray(value, path) {
    if (!Array.isArray(value)) {
        console.error("World book data is not an array", path);
        throw new Error(`世界书文件格式无效，已停止本次操作：${path}`);
    }
    return value;
}
/** Write and verify a sibling file, then use POSIX rename; never copy over the live file. */
async function writeWorldBookJson(path, value) {
    const content = JSON.stringify(value, null, 2);
    const temporary = `${path}.pending-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
    try {
        await Tools.Files.write(temporary, content, false);
        const verified = await Tools.Files.read(temporary);
        if (verified.content !== content) {
            throw new Error("临时文件校验失败");
        }
        // The general move tool may copy/truncate when rename fails, so do not use it here.
        const descriptor = Java.callStatic("android.system.Os", "open", temporary, 0, 0);
        try {
            Java.callStatic("android.system.Os", "fsync", descriptor);
        }
        finally {
            Java.callStatic("android.system.Os", "close", descriptor);
        }
        Java.callStatic("android.system.Os", "rename", temporary, path);
    }
    catch (error) {
        console.error("World book atomic save failed", path, error);
        throw new Error(`世界书保存失败：${path}`);
    }
    finally {
        try {
            const exists = await Tools.Files.exists(temporary);
            if (exists.exists)
                await Tools.Files.deleteFile(temporary);
        }
        catch (error) {
            console.error("Could not remove world book temporary file", temporary, error);
        }
    }
}
async function initializeStorage() {
    await Tools.Files.mkdir(getWorldBookDir(), true);
    const entriesFile = getWorldBookFile();
    if (!(await Tools.Files.exists(entriesFile)).exists) {
        const legacyExists = await Tools.Files.exists(LEGACY_WORLD_BOOK_FILE);
        if (legacyExists.exists) {
            const entries = requireArray(await readWorldBookJson(LEGACY_WORLD_BOOK_FILE), LEGACY_WORLD_BOOK_FILE);
            await writeWorldBookJson(entriesFile, entries);
            // Keep the legacy source, including unrelated files. Migration never recursively deletes it.
            requireArray(await readWorldBookJson(entriesFile), entriesFile);
        }
        else {
            await writeWorldBookJson(entriesFile, []);
        }
    }
    const groupsFile = getWorldBookGroupsFile();
    if (!(await Tools.Files.exists(groupsFile)).exists) {
        await writeWorldBookJson(groupsFile, []);
    }
    const variablesFile = `${getWorldBookDir()}/variables.json`;
    if (!(await Tools.Files.exists(variablesFile)).exists) {
        await writeWorldBookJson(variablesFile, { global_variables: {}, character_variables: {}, chats: {} });
    }
}
function ensureWorldBookStorage() {
    if (!initialization) {
        initialization = initializeStorage().catch((error) => {
            // A failed initialization remains an error; a later explicit operation may try initialization again.
            initialization = undefined;
            console.error("World book initialization failed", error);
            throw error;
        });
    }
    return initialization;
}
async function readWorldBookEntries() {
    await ensureWorldBookStorage();
    return requireArray(await readWorldBookJson(getWorldBookFile()), getWorldBookFile());
}
async function writeWorldBookEntries(entries) {
    await ensureWorldBookStorage();
    await writeWorldBookJson(getWorldBookFile(), entries);
}
async function readWorldBookGroups() {
    await ensureWorldBookStorage();
    return requireArray(await readWorldBookJson(getWorldBookGroupsFile()), getWorldBookGroupsFile());
}
async function writeWorldBookGroups(groups) {
    await ensureWorldBookStorage();
    await writeWorldBookJson(getWorldBookGroupsFile(), groups);
}
