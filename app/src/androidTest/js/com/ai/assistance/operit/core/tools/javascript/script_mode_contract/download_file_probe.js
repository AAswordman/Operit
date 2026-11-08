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
  } catch (e) {
    return String(value);
  }
}

function summarizePayload(value) {
  if (value == null) {
    return {
      type: String(value),
      value: value,
    };
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    let parsed = null;
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
      try {
        parsed = JSON.parse(trimmed);
      } catch (_ignored) {
        parsed = null;
      }
    }
    return {
      type: 'string',
      text: value,
      parsed: parsed,
    };
  }

  return {
    type: typeof value,
    keys: typeof value === 'object' ? Object.keys(value) : null,
    text: safeSerialize(value),
    value: value,
  };
}

function rawAsyncToolCall(toolType, toolName, params) {
  return new Promise(function (resolve) {
    const callbackId = nextId('__operit_probe');
    globalThis[callbackId] = function (result, isError) {
      try {
        resolve({
          callbackId: callbackId,
          isError: !!isError,
          payload: summarizePayload(result),
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
      value: summarizePayload(value),
    };
  } catch (e) {
    return {
      ok: false,
      error: String(e && e.message ? e.message : e),
      data: e && typeof e === 'object' && 'data' in e ? summarizePayload(e.data) : null,
    };
  }
}

function fileState(path) {
  const file = new File(String(path));
  return {
    exists: file.exists(),
    length: file.exists() ? Number(file.length()) : 0,
    path: String(file.getAbsolutePath()),
  };
}

async function runDownloadProbeCase(baseDir, name, url) {
  const destination = String(baseDir) + '/' + name;
  const params = {
    url: url,
    destination: destination,
    environment: 'android',
  };

  const syncRaw = String(NativeInterface.callTool('default', 'download_file', JSON.stringify(params)));
  const syncSummary = summarizePayload(syncRaw);

  const rawAsync = await rawAsyncToolCall('default', 'download_file', params);
  const highLevel = await highLevelToolCall('default', 'download_file', params);

  return {
    name: name,
    url: url,
    destination: destination,
    sync: syncSummary,
    rawAsync: rawAsync,
    highLevel: highLevel,
    file: fileState(destination),
  };
}

async function runParallelRawAsync(cases) {
  const startedAt = Date.now();
  const results = await Promise.all(
    cases.map(function (item) {
      return rawAsyncToolCall('default', 'download_file', {
        url: item.url,
        destination: item.destination,
        environment: 'android',
      }).then(function (result) {
        return {
          name: item.name,
          url: item.url,
          destination: item.destination,
          elapsedMs: Date.now() - startedAt,
          rawAsync: result,
          file: fileState(item.destination),
        };
      });
    })
  );

  return {
    totalMs: Date.now() - startedAt,
    results: results,
  };
}

const rootDir = '/sdcard/Download/Operit/js_test_download_probe_' + Date.now();
ensureDir(rootDir);

const successCases = [
  {
    name: 'type_android.d.ts',
    url: 'https://cdn.jsdelivr.net/gh/3316891527/Operit@main/examples/types/android.d.ts',
  },
  {
    name: 'type_core.d.ts',
    url: 'https://cdn.jsdelivr.net/gh/3316891527/Operit@main/examples/types/core.d.ts',
  },
  {
    name: 'type_results.d.ts',
    url: 'https://cdn.jsdelivr.net/gh/3316891527/Operit@main/examples/types/results.d.ts',
  },
];

const failureCase = {
  name: 'missing_file.d.ts',
  url: 'https://cdn.jsdelivr.net/gh/3316891527/Operit@main/examples/types/__definitely_missing_file__.d.ts',
};

const sequential = [];
for (const item of successCases) {
  sequential.push(await runDownloadProbeCase(rootDir, item.name, item.url));
}
sequential.push(await runDownloadProbeCase(rootDir, failureCase.name, failureCase.url));

const parallelCases = successCases.concat([failureCase]).map(function (item) {
  return {
    name: 'parallel_' + item.name,
    url: item.url,
    destination: rootDir + '/parallel_' + item.name,
  };
});

const parallelRawAsync = await runParallelRawAsync(parallelCases);

return {
  success: true,
  mode: 'script-mode-download-file-probe',
  rootDir: rootDir,
  sequential: sequential,
  parallelRawAsync: parallelRawAsync,
};
