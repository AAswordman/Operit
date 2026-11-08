'use strict';

const File = Java.type('java.io.File');

function ensureDir(path) {
  const dir = new File(String(path));
  if (!dir.exists()) {
    dir.mkdirs();
  }
  return dir;
}

function fileState(path) {
  const file = new File(String(path));
  return {
    path: String(file.getAbsolutePath()),
    exists: file.exists(),
    length: file.exists() ? Number(file.length()) : 0,
  };
}

function summarize(value) {
  if (value == null) {
    return { type: String(value), value: value };
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
    return { type: 'string', text: value, parsed: parsed };
  }
  return {
    type: typeof value,
    keys: typeof value === 'object' ? Object.keys(value) : null,
    value: value,
  };
}

async function callTool(name, params) {
  const startedAt = Date.now();
  try {
    const value = await toolCall('default', name, params || {});
    return {
      ok: true,
      durationMs: Date.now() - startedAt,
      value: summarize(value),
    };
  } catch (e) {
    return {
      ok: false,
      durationMs: Date.now() - startedAt,
      error: String(e && e.message ? e.message : e),
      data: e && typeof e === 'object' && 'data' in e ? summarize(e.data) : null,
    };
  }
}

async function run() {
  const rootDir = '/sdcard/Download/Operit/js_test_ffmpeg_probe_' + Date.now();
  ensureDir(rootDir);

  const simpleOutput = rootDir + '/simple_color.mp4';
  const complexOutput = rootDir + '/complex_filter.mp4';

  const info = await callTool('ffmpeg_info', {});
  const simple = await callTool('ffmpeg_execute', {
    command: '-y -f lavfi -i color=c=blue:s=64x64:d=0.2 -an -c:v mpeg4 "' + simpleOutput + '"',
  });
  const complex = await callTool('ffmpeg_execute', {
    command: '-y -f lavfi -i color=c=red:s=64x64:d=0.2 -filter_complex "[0:v]scale=32:32[outv]" -map "[outv]" -an -c:v mpeg4 "' + complexOutput + '"',
  });

  const simpleFile = fileState(simpleOutput);
  const complexFile = fileState(complexOutput);

  return {
    success: info.ok && simple.ok && simpleFile.exists && simpleFile.length > 0 && complex.ok && complexFile.exists && complexFile.length > 0,
    mode: 'default-tool-ffmpeg-probe',
    rootDir: rootDir,
    info: info,
    simple: simple,
    simpleFile: simpleFile,
    complex: complex,
    complexFile: complexFile,
  };
}

module.exports = { run };
