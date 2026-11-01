'use strict';

const { assert, assertEq, test, runTests } = require('../../../../../../../../../lib/harness');

const File = Java.type('java.io.File');
const FileOutputStream = Java.type('java.io.FileOutputStream');
const OutputStreamWriter = Java.type('java.io.OutputStreamWriter');
const Charset = Java.type('java.nio.charset.Charset');
const BitmapFactory = Java.type('android.graphics.BitmapFactory');

const UTF8 = Charset.forName('UTF-8');
const BASE_DIR = '/sdcard/Download/Operit/browser_tool_suite';
const PAGE_PATH = BASE_DIR + '/browser_tool_suite.html';
const PAGE_URL = 'file://' + PAGE_PATH;
const UPLOAD_PATH = BASE_DIR + '/upload_payload.txt';
const DELAYED_TEXT = 'READY_MARK_' + Date.now();

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
  return file;
}

function fileState(path) {
  const file = new File(String(path));
  return {
    path: String(file.getAbsolutePath()),
    exists: file.exists(),
    length: file.exists() ? Number(file.length()) : 0,
  };
}

async function callBrowserTool(name, params) {
  return await toolCall('default', name, params || {});
}

function countTabs(output) {
  const matches = String(output || '').match(/^- \[\d+\]/gm);
  return matches ? matches.length : 0;
}

function extractRef(snapshotOutput, marker) {
  const lines = String(snapshotOutput || '').split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (line.indexOf(marker) >= 0) {
      const match = line.match(/\[ref=([^\]]+)\]/);
      if (match) {
        return match[1];
      }
    }
  }
  throw new Error('ref not found for marker: ' + marker + '\nSnapshot:\n' + snapshotOutput);
}

function extractSavedPath(output) {
  const match = String(output || '').match(/Saved screenshot to ([^\r\n]+)/);
  return match ? match[1].trim() : null;
}

function bitmapSize(path) {
  const bitmap = BitmapFactory.decodeFile(String(path));
  assert(bitmap != null, 'screenshot should decode as a bitmap');
  try {
    return { width: Number(bitmap.getWidth()), height: Number(bitmap.getHeight()) };
  } finally {
    bitmap.recycle();
  }
}

async function tryBrowserTool(name, params) {
  try {
    return await callBrowserTool(name, params);
  } catch (_ignored) {
    return null;
  }
}

async function resetBrowserState() {
  await tryBrowserTool('browser_handle_dialog', { accept: true });
  await tryBrowserTool('browser_file_upload', {});
  await tryBrowserTool('browser_close_all', {});
}

async function ensureFixtureOpen() {
  const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
  assert(
    String(output).indexOf(PAGE_URL) >= 0 || String(output).indexOf('Browser Tool Test') >= 0,
    'fixture page should load'
  );
  return output;
}

async function ensureFixtureRefs(refs) {
  if (refs.clickBtn && refs.fileTrigger) {
    return refs;
  }
  await ensureFixtureOpen();
  const output = await callBrowserTool('browser_snapshot', { depth: 6 });
  refs.clickBtn = extractRef(output, 'Click Action');
  refs.logBtn = extractRef(output, 'Log Action');
  refs.alertBtn = extractRef(output, 'Alert Action');
  refs.hoverBox = extractRef(output, 'Hover Action');
  refs.dragSrc = extractRef(output, 'Drag Source');
  refs.dragDst = extractRef(output, 'Drag Target');
  refs.typeInput = extractRef(output, 'Type Input');
  refs.fillInput = extractRef(output, 'Fill Input');
  refs.checkInput = extractRef(output, 'Check Input');
  refs.selectInput = extractRef(output, 'Select Input');
  refs.fileTrigger = extractRef(output, 'File Trigger');
  return refs;
}

function makeSuiteHtml() {
  return [
    '<!doctype html>',
    '<html>',
    '<head>',
    '  <meta charset="utf-8" />',
    '  <title>Browser Tool Test</title>',
    '  <style>',
    '    body { font-family: sans-serif; padding: 16px; }',
    '    .box { width: 120px; height: 48px; border: 1px solid #333; margin: 8px 0; display: flex; align-items: center; justify-content: center; }',
    '  </style>',
    '</head>',
    '<body data-clicked="0" data-hovered="no" data-dropped="" data-lastkey="" data-upload="" data-dialog="idle" data-delayed="">',
    '  <button id="clickBtn">Click Action</button>',
    '  <button id="logBtn">Log Action</button>',
    '  <button id="alertBtn">Alert Action</button>',
    '  <button id="hoverBox">Hover Action</button>',
    '  <button id="dragSrc" draggable="true">Drag Source</button>',
    '  <button id="dragDst">Drag Target</button>',
    '  <input id="typeInput" aria-label="Type Input" type="text" />',
    '  <input id="fillInput" aria-label="Fill Input" type="text" />',
    '  <input id="checkInput" aria-label="Check Input" type="checkbox" />',
    '  <select id="selectInput" aria-label="Select Input">',
    '    <option value="alpha">Alpha</option>',
    '    <option value="beta">Beta</option>',
    '    <option value="gamma">Gamma</option>',
    '  </select>',
    '  <button id="fileTrigger">File Trigger</button>',
    '  <input id="fileInput" aria-label="File Input" type="file" style="position:absolute;left:-9999px;top:-9999px;" />',
    '  <div id="fileState">no-file</div>',
    '  <div id="delayed">idle</div>',
    '  <script>',
    '    (function () {',
    '      const body = document.body;',
    '      const clickBtn = document.getElementById("clickBtn");',
    '      const logBtn = document.getElementById("logBtn");',
    '      const alertBtn = document.getElementById("alertBtn");',
    '      const hoverBox = document.getElementById("hoverBox");',
    '      const dragSrc = document.getElementById("dragSrc");',
    '      const dragDst = document.getElementById("dragDst");',
    '      const fileTrigger = document.getElementById("fileTrigger");',
    '      const fileInput = document.getElementById("fileInput");',
    '      const fileState = document.getElementById("fileState");',
    '      const delayed = document.getElementById("delayed");',
    '      clickBtn.addEventListener("click", function () {',
    '        body.dataset.clicked = String(Number(body.dataset.clicked || "0") + 1);',
    '      });',
    '      logBtn.addEventListener("click", function () {',
    '        console.info("browser-tool-log-info");',
    '        console.warn("browser-tool-log-warn");',
    '      });',
    '      alertBtn.addEventListener("click", function () {',
    '        body.dataset.dialog = "opened";',
    '        alert("Browser dialog");',
    '        body.dataset.dialog = "handled";',
    '      });',
    '      hoverBox.addEventListener("mouseover", function () {',
    '        body.dataset.hovered = "yes";',
    '      });',
    '      dragSrc.addEventListener("dragstart", function (event) {',
    '        if (event.dataTransfer) {',
    '          event.dataTransfer.setData("text/plain", "drag-source");',
    '        }',
    '      });',
    '      dragDst.addEventListener("dragover", function (event) {',
    '        event.preventDefault();',
    '      });',
    '      dragDst.addEventListener("drop", function (event) {',
    '        event.preventDefault();',
    '        const value = event.dataTransfer ? event.dataTransfer.getData("text/plain") : "missing";',
    '        body.dataset.dropped = value || "empty";',
    '      });',
    '      document.addEventListener("keydown", function (event) {',
    '        body.dataset.lastkey = String(event.key || "");',
    '      });',
    '      fileTrigger.addEventListener("click", function () {',
    '        fileInput.click();',
    '      });',
      '      fileInput.addEventListener("change", function () {',
    '        const names = Array.prototype.map.call(fileInput.files || [], function (file) { return file.name; }).join(",");',
    '        body.dataset.upload = names;',
    '        fileState.textContent = names || "empty";',
    '      });',
    '      window.scheduleDelayedText = function (text, delayMs) {',
    '        delayed.textContent = "waiting";',
    '        body.dataset.delayed = "waiting";',
    '        setTimeout(function () {',
    '          delayed.textContent = text;',
    '          body.dataset.delayed = text;',
    '        }, delayMs);',
    '        return "scheduled";',
    '      };',
    '    })();',
    '  </script>',
    '</body>',
    '</html>',
  ].join('\n');
}

async function evaluateExpression(expression, ref) {
  return await callBrowserTool('browser_evaluate', {
    function: expression,
    ref: ref,
  });
}

exports.run = async function run() {
  const params = arguments.length > 0 ? (arguments[0] || {}) : {};
  ensureDir(BASE_DIR);
  writeText(PAGE_PATH, makeSuiteHtml());
  writeText(UPLOAD_PATH, 'browser upload payload');
  await resetBrowserState();

  const refs = {};

  const tests = [
    test('browser_tabs list is callable', async () => {
      const output = await callBrowserTool('browser_tabs', { action: 'list' });
      assert(String(output).length > 0, 'tabs list should return output');
    }),
    test('browser_navigate loads example.com for network capture', async () => {
      const output = await callBrowserTool('browser_navigate', { url: 'https://example.com' });
      assert(String(output).indexOf('example.com') >= 0, 'navigate should mention example.com');
    }),
    test('browser_network_requests returns captured requests', async () => {
      const output = await callBrowserTool('browser_network_requests', {
        requestBody: false,
        requestHeaders: false,
        static: false,
      });
      assert(/example\.com|https?:\/\//.test(String(output)), 'network requests should contain example.com');
    }),
    test('browser_navigate loads local fixture page', async () => {
      const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
      assert(String(output).indexOf('Browser Tool Test') >= 0 || String(output).indexOf(PAGE_URL) >= 0, 'navigate should load local page');
    }),
    test('browser_navigate_back returns to previous page', async () => {
      const output = await callBrowserTool('browser_navigate_back', {});
      assert(String(output).indexOf('Navigated back.') >= 0 || String(output).indexOf('example.com') >= 0, 'navigate_back should succeed');
    }),
    test('browser_navigate reloads local fixture page after back', async () => {
      const output = await callBrowserTool('browser_navigate', { url: PAGE_URL });
      assert(String(output).indexOf(PAGE_URL) >= 0 || String(output).indexOf('Browser Tool Test') >= 0, 'fixture page should load again');
    }),
    test('browser_snapshot exposes refs for interactive elements', async () => {
      await ensureFixtureRefs(refs);
      assert(refs.clickBtn && refs.fileTrigger, 'expected fixture refs to be captured');
    }),
    test('browser_click triggers DOM click handlers', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.clickBtn });
      const value = await evaluateExpression('() => document.body.dataset.clicked');
      assert(String(value).indexOf('1') >= 0, 'click should increment clicked counter');
    }),
    test('browser_hover triggers mouseover state', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_hover', { ref: refs.hoverBox });
      const value = await evaluateExpression('() => document.body.dataset.hovered');
      assert(String(value).indexOf('yes') >= 0, 'hover should update state');
    }),
    test('browser_drag transfers data to drop zone', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_drag', { startRef: refs.dragSrc, endRef: refs.dragDst });
      const value = await evaluateExpression('() => document.body.dataset.dropped');
      assert(String(value).indexOf('drag-source') >= 0, 'drag should update dropped state');
    }),
    test('browser_type fills text input and submit keeps state', async () => {
      await ensureFixtureRefs(refs);
      await evaluateExpression('() => { const el = document.getElementById("typeInput"); el.value = ""; el.focus(); return "ready"; }');
      await callBrowserTool('browser_type', { ref: refs.typeInput, text: 'typed-value', submit: false, slowly: false });
      const value = await evaluateExpression('() => document.getElementById("typeInput").value');
      assert(String(value).indexOf('typed-value') >= 0, 'type should fill text input');
    }),
    test('browser_press_key dispatches keyboard event', async () => {
      await ensureFixtureRefs(refs);
      await evaluateExpression('() => { document.getElementById("typeInput").focus(); return "focused"; }');
      await callBrowserTool('browser_press_key', { key: 'Enter' });
      const value = await evaluateExpression('() => document.body.dataset.lastkey');
      assert(String(value).indexOf('Enter') >= 0, 'press_key should record Enter');
    }),
    test('browser_fill_form updates text checkbox and select', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_fill_form', {
        fields: [
          { name: 'Fill Input', ref: refs.fillInput, type: 'textbox', value: 'filled-text' },
          { name: 'Check Input', ref: refs.checkInput, type: 'checkbox', value: 'true' },
          { name: 'Select Input', ref: refs.selectInput, type: 'combobox', value: 'Beta' },
        ],
      });
      const payload = await evaluateExpression('() => JSON.stringify({ fill: document.getElementById("fillInput").value, checked: document.getElementById("checkInput").checked, selected: document.getElementById("selectInput").value })');
      assert(String(payload).indexOf('filled-text') >= 0, 'fill_form should set input value');
      assert(String(payload).indexOf('true') >= 0, 'fill_form should set checkbox');
      assert(String(payload).indexOf('beta') >= 0, 'fill_form should select beta');
    }),
    test('browser_select_option changes selected option', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_select_option', { ref: refs.selectInput, values: ['gamma'] });
      const value = await evaluateExpression('() => document.getElementById("selectInput").value');
      assert(String(value).indexOf('gamma') >= 0, 'select_option should choose gamma');
    }),
    test('browser_evaluate works with ref argument', async () => {
      await ensureFixtureRefs(refs);
      const value = await callBrowserTool('browser_evaluate', {
        ref: refs.clickBtn,
        function: '(el) => el.textContent',
      });
      assert(String(value).indexOf('Click Action') >= 0, 'evaluate should return button text');
    }),
    test('browser_console_messages returns recent console output', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.logBtn });
      const value = await callBrowserTool('browser_console_messages', { level: 'info' });
      assert(String(value).indexOf('browser-tool-log-info') >= 0, 'console messages should include info log');
    }),
    test('browser_wait_for resolves when delayed text appears', async () => {
      await ensureFixtureOpen();
      await evaluateExpression('() => window.scheduleDelayedText(' + JSON.stringify(DELAYED_TEXT) + ', 150)');
      const value = await callBrowserTool('browser_wait_for', { text: DELAYED_TEXT });
      assert(String(value).indexOf(DELAYED_TEXT) >= 0, 'wait_for should mention delayed text');
    }),
    test('browser_handle_dialog accepts pending alert', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.alertBtn });
      const handled = await callBrowserTool('browser_handle_dialog', { accept: true });
      assert(String(handled).indexOf('Handled alert dialog.') >= 0 || String(handled).indexOf('Handled alert.') >= 0, 'handle_dialog should succeed');
      const state = await evaluateExpression('() => document.body.dataset.dialog');
      assert(String(state).indexOf('handled') >= 0, 'dialog state should be handled');
    }),
    test('browser_file_upload resolves file chooser and updates file list', async () => {
      await ensureFixtureRefs(refs);
      await callBrowserTool('browser_click', { ref: refs.fileTrigger });
      await callBrowserTool('browser_file_upload', { paths: [UPLOAD_PATH] });
      const value = await evaluateExpression('() => document.body.dataset.upload');
      assert(String(value).indexOf('upload_payload.txt') >= 0, 'uploaded file name should be visible');
    }),
    test('browser_resize changes the page layout viewport', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_resize', { width: 720, height: 1280 });
      assert(String(value).indexOf('720x1280') >= 0, 'resize should report requested viewport');
      const actualViewport = await evaluateExpression('() => window.innerWidth + "x" + window.innerHeight');
      assert(String(actualViewport).indexOf('720x1280') >= 0, 'page layout viewport should match requested size');
    }),
    test('browser_resize rejects unsafe viewport sizes without mutating the page', async () => {
      let rejected = false;
      try {
        await callBrowserTool('browser_resize', { width: 100000, height: 100000 });
      } catch (_expected) {
        rejected = true;
      }
      assert(rejected, 'unsafe viewport should be rejected');
      const actualViewport = await evaluateExpression('() => window.innerWidth + "x" + window.innerHeight');
      assert(String(actualViewport).indexOf('720x1280') >= 0, 'rejected resize should keep the prior viewport');
    }),
    test('browser_take_screenshot writes an image file', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_take_screenshot', { type: 'png' });
      const savedPath = extractSavedPath(value);
      assert(savedPath, 'screenshot output should contain saved path');
      const state = fileState(savedPath);
      assert(state.exists && state.length > 0, 'screenshot file should exist');
      const size = bitmapSize(savedPath);
      assert(size.width === 720 && size.height === 1280, 'viewport screenshot should use CSS pixel dimensions');
    }),
    test('browser_take_screenshot fullPage uses CSS pixel dimensions', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_take_screenshot', { type: 'png', fullPage: true });
      const savedPath = extractSavedPath(value);
      assert(savedPath, 'full-page screenshot output should contain saved path');
      const size = bitmapSize(savedPath);
      assert(size.width === 720 && size.height >= 1280, 'full-page screenshot should not be device-density scaled');
    }),
    test('browser_run_code still works on current page', async () => {
      await ensureFixtureOpen();
      const value = await callBrowserTool('browser_run_code', {
        code: 'async (page) => { return await page.title(); }',
      });
      assert(String(value).indexOf('Browser Tool Test') >= 0, 'run_code should return current page title');
    }),
    test('browser_tabs can create and select tabs', async () => {
      await ensureFixtureOpen();
      const created = await callBrowserTool('browser_tabs', { action: 'create' });
      assert(String(created).indexOf('Created tab') >= 0, 'tabs new should create a tab');
      const selected = await callBrowserTool('browser_tabs', { action: 'select', index: 0 });
      assert(String(selected).indexOf('Selected tab 0.') >= 0, 'tabs select should switch back to first tab');
    }),
    test('browser_close closes the current tab cleanly', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_tabs', { action: 'select', index: 1 });
      const closed = await callBrowserTool('browser_close', {});
      assert(/Closed/.test(String(closed)), 'browser_close should close current tab');
      const listed = await callBrowserTool('browser_tabs', { action: 'list' });
      assertEq(countTabs(listed), 1, 'only one tab should remain after close');
    }),
    test('browser_close_all closes all tabs cleanly', async () => {
      await ensureFixtureOpen();
      await callBrowserTool('browser_tabs', { action: 'create' });
      await callBrowserTool('browser_tabs', { action: 'create' });
      const closed = await callBrowserTool('browser_close_all', {});
      assert(String(closed).indexOf('Closed all browser tabs.') >= 0, 'browser_close_all should close every tab');
      const listed = await callBrowserTool('browser_tabs', { action: 'list' });
      assert(String(listed).indexOf('No open tabs.') >= 0, 'no tabs should remain after browser_close_all');
    }),
  ];

  const onlyName = params && params.only ? String(params.only) : '';
  const startAt = params && params.startAt ? String(params.startAt) : '';
  let selectedTests = tests;
  if (onlyName) {
    selectedTests = tests.filter((item) => item.name === onlyName);
    assert(selectedTests.length > 0, 'test not found: ' + onlyName);
  } else if (startAt) {
    const startIndex = tests.findIndex((item) => item.name === startAt);
    assert(startIndex >= 0, 'startAt test not found: ' + startAt);
    selectedTests = tests.slice(startIndex);
  }

  const result = await runTests(selectedTests);

  return {
    success: result.success,
    passed: result.passed,
    failed: result.failed,
    durationMs: result.durationMs,
    failures: result.failures,
    selectedCount: selectedTests.length,
    artifacts: {
      page: fileState(PAGE_PATH),
      upload: fileState(UPLOAD_PATH),
    },
  };
};
