'use strict';

const File = Java.type('java.io.File');

function ensureDir(path) {
  const dir = new File(String(path));
  if (!dir.exists()) {
    dir.mkdirs();
  }
  return dir;
}

function nextId(prefix) {
  return String(prefix) + '_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
}

function safeSerialize(value) {
  try {
    return JSON.stringify(value);
  } catch (_ignored) {
    return String(value);
  }
}

function summarizePayload(value) {
  if (value == null) {
    return {
      type: String(value),
      text: String(value),
    };
  }

  return {
    type: typeof value,
    keys: typeof value === 'object' ? Object.keys(value) : null,
    text: safeSerialize(value),
  };
}

function fileState(path) {
  const file = new File(String(path));
  return {
    exists: file.exists(),
    length: file.exists() ? Number(file.length()) : 0,
    path: String(file.getAbsolutePath()),
  };
}

function rawAsyncToolCall(toolType, toolName, params) {
  return new Promise(function (resolve) {
    const callbackId = nextId('__operit_stress');
    globalThis[callbackId] = function (result, isError) {
      try {
        resolve({
          callbackId: callbackId,
          isError: !!isError,
          payload: summarizePayload(result),
          raw: result,
        });
      } finally {
        try {
          delete globalThis[callbackId];
        } catch (_ignored) {
          globalThis[callbackId] = undefined;
        }
      }
    };

    NativeInterface.callToolAsync(
      callbackId,
      toolType,
      toolName,
      JSON.stringify(params || {})
    );
  });
}

async function highLevelToolCall(toolType, toolName, params) {
  try {
    const value = await toolCall(toolType, toolName, params);
    return {
      ok: true,
      payload: summarizePayload(value),
      raw: value,
    };
  } catch (e) {
    return {
      ok: false,
      error: String(e && e.message ? e.message : e),
      data: e && typeof e === 'object' && 'data' in e ? summarizePayload(e.data) : null,
    };
  }
}

const CDN_BASE = 'https://cdn.jsdelivr.net/gh/3316891527/Operit@main';
const TYPE_FILES = [
  'android.d.ts',
  'chat.d.ts',
  'compose-dsl.d.ts',
  'compose-dsl.material3.generated.d.ts',
  'core.d.ts',
  'cryptojs.d.ts',
  'ffmpeg.d.ts',
  'files.d.ts',
  'index.d.ts',
  'java-bridge.d.ts',
  'jimp.d.ts',
  'memory.d.ts',
  'network.d.ts',
  'okhttp.d.ts',
  'pako.d.ts',
  'quickjs-runtime.d.ts',
  'results.d.ts',
  'software_settings.d.ts',
  'system.d.ts',
  'tasker.d.ts',
  'tool-types.d.ts',
  'toolpkg.d.ts',
  'ui.d.ts',
  'workflow.d.ts',
];

const DOWNLOADS = [
  {
    name: 'SKILL.md',
    url: CDN_BASE + '/docs/SCRIPT_DEV_SKILL.md',
  },
  {
    name: 'SCRIPT_DEV_GUIDE.md',
    url: CDN_BASE + '/docs/SCRIPT_DEV_GUIDE.md',
  },
  {
    name: 'TOOLPKG_FORMAT_GUIDE.md',
    url: CDN_BASE + '/docs/TOOLPKG_FORMAT_GUIDE.md',
  },
].concat(
  TYPE_FILES.map(function (fileName) {
    return {
      name: fileName,
      url: CDN_BASE + '/examples/types/' + fileName,
    };
  })
);

const rootDir = '/sdcard/Download/Operit/js_test_download_stress_' + Date.now();
ensureDir(rootDir);

const startedAt = Date.now();
const rawResults = await Promise.all(
  DOWNLOADS.map(async function (item) {
    const destination = rootDir + '/' + item.name;
    const params = {
      url: item.url,
      destination: destination,
      environment: 'android',
    };
    const rawAsync = await rawAsyncToolCall('default', 'download_file', params);
    return {
      name: item.name,
      url: item.url,
      destination: destination,
      elapsedMs: Date.now() - startedAt,
      rawAsync: rawAsync,
      file: fileState(destination),
    };
  })
);

const malformedRaw = rawResults.filter(function (item) {
  const payload = item.rawAsync && item.rawAsync.raw;
  return !payload || typeof payload !== 'object' || !('success' in payload);
});

const rawFailures = rawResults.filter(function (item) {
  return item.rawAsync && item.rawAsync.isError;
});

const sampledHighLevel = [];
for (let index = 0; index < rawFailures.length && index < 5; index += 1) {
  const item = rawFailures[index];
  sampledHighLevel.push({
    name: item.name,
    highLevel: await highLevelToolCall('default', 'download_file', {
      url: item.url,
      destination: item.destination + '.retry',
      environment: 'android',
    }),
  });
}

return {
  success: true,
  mode: 'script-mode-download-file-stress-probe',
  rootDir: rootDir,
  totalMs: Date.now() - startedAt,
  requestedCount: DOWNLOADS.length,
  rawFailureCount: rawFailures.length,
  malformedRawCount: malformedRaw.length,
  rawFailures: rawFailures.map(function (item) {
    return {
      name: item.name,
      url: item.url,
      elapsedMs: item.elapsedMs,
      payload: item.rawAsync.payload,
      file: item.file,
    };
  }),
  malformedRaw: malformedRaw.map(function (item) {
    return {
      name: item.name,
      url: item.url,
      payload: item.rawAsync ? item.rawAsync.payload : null,
    };
  }),
  sampledHighLevel: sampledHighLevel,
};
