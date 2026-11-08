'use strict';

const File = Java.type('java.io.File');
const FileOutputStream = Java.type('java.io.FileOutputStream');
const OutputStreamWriter = Java.type('java.io.OutputStreamWriter');
const Charset = Java.type('java.nio.charset.Charset');

const UTF8 = Charset.forName('UTF-8');
const BASE_DIR = '/sdcard/Download/Operit/browser_tool_suite';
const PAGE_PATH = BASE_DIR + '/browser_tool_probe.html';
const PAGE_URL = 'file://' + PAGE_PATH;

function ensureDir(path) {
  const dir = new File(String(path));
  if (!dir.exists()) {
    dir.mkdirs();
  }
  return dir;
}

function writeText(path, text) {
  const file = new File(String(path));
  const parent = file.getParentFile();
  if (parent != null) {
    ensureDir(parent.getAbsolutePath());
  }
  const writer = new OutputStreamWriter(new FileOutputStream(file, false), UTF8);
  try {
    writer.write(String(text));
  } finally {
    writer.close();
  }
}

async function callTool(name, params) {
  try {
    const value = await toolCall('default', name, params || {});
    return { ok: true, value };
  } catch (e) {
    return {
      ok: false,
      error: String(e && e.message ? e.message : e),
      data: e && typeof e === 'object' && 'data' in e ? e.data : null,
    };
  }
}

function countTabs(output) {
  const matches = String(output || '').match(/^- \[\d+\]/gm);
  return matches ? matches.length : 0;
}

async function tryTool(name, params) {
  try {
    return await toolCall('default', name, params || {});
  } catch (_ignored) {
    return null;
  }
}

async function resetBrowserState() {
  await tryTool('browser_handle_dialog', { accept: true });
  await tryTool('browser_file_upload', {});
  await tryTool('browser_close_all', {});
}

function html() {
  return [
    '<!doctype html>',
    '<html><head><meta charset="utf-8" /><title>Browser Probe</title></head>',
    '<body>',
    '<button id="clickBtn">Click Action</button>',
    '<input id="typeInput" aria-label="Type Input" type="text" />',
    '<select id="selectInput" aria-label="Select Input"><option value="a">Alpha</option><option value="b">Beta</option></select>',
    '</body></html>',
  ].join('\n');
}

exports.run = async function run(params) {
  ensureDir(BASE_DIR);
  writeText(PAGE_PATH, html());
  await resetBrowserState();

  const mode = params && params.mode ? String(params.mode) : 'snapshot';
  const results = [];

  results.push({ step: 'navigate', out: await callTool('browser_navigate', { url: PAGE_URL }) });

  if (mode === 'snapshot') {
    results.push({ step: 'snapshot', out: await callTool('browser_snapshot', { depth: 6 }) });
  } else if (mode === 'resize') {
    results.push({ step: 'resize', out: await callTool('browser_resize', { width: 720, height: 1280 }) });
  } else if (mode === 'tabs') {
    results.push({ step: 'tabs-list', out: await callTool('browser_tabs', { action: 'list' }) });
    results.push({ step: 'tabs-create', out: await callTool('browser_tabs', { action: 'create' }) });
    results.push({ step: 'tabs-select0', out: await callTool('browser_tabs', { action: 'select', index: 0 }) });
  } else if (mode === 'run_code') {
    results.push({ step: 'run_code', out: await callTool('browser_run_code', { code: 'async (page) => { return await page.title(); }' }) });
  }

  return {
    success: true,
    mode,
    results,
  };
};
