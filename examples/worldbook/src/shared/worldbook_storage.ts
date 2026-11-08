const LEGACY_WORLD_BOOK_DIR = "/sdcard/Download/Operit/worldbook";
const LEGACY_WORLD_BOOK_FILE = `${LEGACY_WORLD_BOOK_DIR}/entries.json`;
let initialization: Promise<void> | undefined;
let storageQueue: Promise<void> = Promise.resolve();

export function getWorldBookDir(): string {
  return ToolPkg.getConfigDir();
}
export function getWorldBookFile(): string {
  return `${getWorldBookDir()}/entries.json`;
}
export function getWorldBookGroupsFile(): string {
  return `${getWorldBookDir()}/groups.json`;
}

/** All mutations run in the package main runtime, including requests from the UI. */
export async function withWorldBookStorage<T>(operation: () => Promise<T>): Promise<T> {
  const previous = storageQueue;
  let release!: () => void;
  storageQueue = new Promise<void>((resolve) => { release = resolve; });
  await previous;
  try {
    await ensureWorldBookStorage();
    return await operation();
  } finally {
    release();
  }
}

export async function readWorldBookJson(path: string): Promise<unknown> {
  try {
    const file = await Tools.Files.read(path);
    if (typeof file.content !== "string" || file.content.trim().length === 0) {
      throw new Error("文件为空或未返回内容");
    }
    const parsed: unknown = JSON.parse(file.content);
    return parsed;
  } catch (error) {
    console.error("World book data read failed", path, error);
    throw new Error(`世界书数据读取失败，已停止本次操作以避免覆盖原数据：${path}`);
  }
}

function requireArray<T>(value: unknown, path: string): T[] {
  if (!Array.isArray(value)) {
    console.error("World book data is not an array", path);
    throw new Error(`世界书文件格式无效，已停止本次操作：${path}`);
  }
  return value as T[];
}

/** Write and verify a sibling file, then use POSIX rename; never copy over the live file. */
export async function writeWorldBookJson(path: string, value: unknown): Promise<void> {
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
    } finally {
      Java.callStatic("android.system.Os", "close", descriptor);
    }
    Java.callStatic("android.system.Os", "rename", temporary, path);
  } catch (error) {
    console.error("World book atomic save failed", path, error);
    throw new Error(`世界书保存失败：${path}`);
  } finally {
    try {
      const exists = await Tools.Files.exists(temporary);
      if (exists.exists) await Tools.Files.deleteFile(temporary);
    } catch (error) {
      console.error("Could not remove world book temporary file", temporary, error);
    }
  }
}

async function initializeStorage(): Promise<void> {
  await Tools.Files.mkdir(getWorldBookDir(), true);
  const entriesFile = getWorldBookFile();
  if (!(await Tools.Files.exists(entriesFile)).exists) {
    const legacyExists = await Tools.Files.exists(LEGACY_WORLD_BOOK_FILE);
    if (legacyExists.exists) {
      const entries = requireArray<unknown>(await readWorldBookJson(LEGACY_WORLD_BOOK_FILE), LEGACY_WORLD_BOOK_FILE);
      await writeWorldBookJson(entriesFile, entries);
      // Keep the legacy source, including unrelated files. Migration never recursively deletes it.
      requireArray<unknown>(await readWorldBookJson(entriesFile), entriesFile);
    } else {
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

export function ensureWorldBookStorage(): Promise<void> {
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

export async function readWorldBookEntries<T>(): Promise<T[]> {
  await ensureWorldBookStorage();
  return requireArray<T>(await readWorldBookJson(getWorldBookFile()), getWorldBookFile());
}
export async function writeWorldBookEntries(entries: unknown[]): Promise<void> {
  await ensureWorldBookStorage();
  await writeWorldBookJson(getWorldBookFile(), entries);
}
export async function readWorldBookGroups<T>(): Promise<T[]> {
  await ensureWorldBookStorage();
  return requireArray<T>(await readWorldBookJson(getWorldBookGroupsFile()), getWorldBookGroupsFile());
}
export async function writeWorldBookGroups(groups: unknown[]): Promise<void> {
  await ensureWorldBookStorage();
  await writeWorldBookJson(getWorldBookGroupsFile(), groups);
}
