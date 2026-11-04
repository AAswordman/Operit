const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const vm = require('node:vm');
const { test, after } = require('node:test');

const root = path.resolve(__dirname, '../..');
const exampleSource = fs.readFileSync(path.join(root, 'examples/code_runner.js'), 'utf8');
const bundledSource = fs.readFileSync(path.join(root, 'app/src/main/assets/packages/code_runner.js'), 'utf8');
const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'operit-code-runner-ci-'));
const parentDirectories = [];
let commandNumber = 0;
after(() => fs.rmSync(fixture, { recursive: true, force: true }));

function result(command, options, output = 'ok', exitCode = 0) {
  return { command, output, exitCode, executorKey: options.executorKey, timedOut: false };
}

// 只在仓库工作流运行短小的 Bash/Python/Node 用例；超时精确终止该测试进程组。
function executeBash(command, options) {
  return new Promise((resolve, reject) => {
    const marker = `OPERIT_CI_PARENT_${++commandNumber}_`;
    const instrumented = `${command}\noperit_ci_status=$?\nprintf '\\036${marker}%s\\037' "$PWD" >&2\nexit "$operit_ci_status"\n`;
    const child = spawn('bash', ['-s'], { cwd: fixture, detached: true });
    let output = '';
    let timedOut = false;
    child.stdout.on('data', data => { output += data.toString(); });
    child.stderr.on('data', data => { output += data.toString(); });
    const deadline = setTimeout(() => {
      timedOut = true;
      try { process.kill(-child.pid, 'SIGKILL'); } catch (error) {
        if (error.code !== 'ESRCH') reject(error);
      }
    }, Math.min(options.timeoutMs, 30000));
    child.on('error', error => { clearTimeout(deadline); reject(error); });
    child.on('close', code => {
      clearTimeout(deadline);
      const state = new RegExp(`\\x1e${marker}([^\\x1f]*)\\x1f`, 'g');
      output = output.replace(state, (_, directory) => {
        parentDirectories.push(directory);
        return '';
      });
      if (timedOut) {
        reject(Object.assign(new Error('CI command timed out'), { data: { output, timedOut: true } }));
      } else {
        resolve(result(command, options, output, code === null ? 128 : code));
      }
    });
    child.stdin.on('error', error => {
      if (error.code !== 'EPIPE') reject(error);
    });
    child.stdin.end(instrumented);
  });
}

function loadRunner(execute = executeBash) {
  const calls = [];
  const errors = [];
  const completions = [];
  const context = vm.createContext({
    exports: {},
    console: { log() {}, warn() {}, error: (...args) => errors.push(args) },
    complete: value => completions.push(value),
    Tools: {
      Files: { read: async file => ({ content: fs.readFileSync(file, 'utf8') }) },
      System: { terminal: { hiddenExec: async (command, options) => {
        calls.push({ command, options });
        return execute(command, options);
      } } },
    },
  });
  vm.runInContext(bundledSource, context, { timeout: 1000 });
  return { runner: vm.runInContext('codeRunner', context), exports: context.exports, calls, errors, completions };
}

function mockTerminal(intercept = () => undefined) {
  let directories = 0;
  return async (command, options) => {
    const intercepted = await intercept(command, options);
    if (intercepted !== undefined) return intercepted;
    if (command.includes('mktemp -d ')) {
      return result(command, options, `/tmp/operit-code-runner.${String(++directories).padStart(8, '0')}\n`);
    }
    return result(command, options);
  };
}

const isWrite = command => command.includes("printf '%s'") && command.includes('/main.py');
const isCleanup = command => command.includes('rm -rf -- ');
const isPythonExecution = command => command.includes('~/.code_runner/py/bin/python ') && !command.includes('if [ ! -x ');

test('packaged and example JavaScript copies stay identical', () => {
  assert.equal(bundledSource, exampleSource);
});

test('ES5 inline and file entrypoints keep using the script sandbox', async () => {
  const loaded = loadRunner();
  assert.equal(await loaded.runner.run_javascript_es5({ script: 'return 42;' }), 'Return value: 42');
  const source = path.join(fixture, 'sandbox.js');
  fs.writeFileSync(source, 'return 43;');
  assert.equal(await loaded.runner.run_javascript_file({ file_path: source }), 'Return value: 43');
  assert.equal(loaded.calls.length, 0);
});

test('Python preserves EOF lines, frame markers, quotes and shell text', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  const marker = path.join(fixture, 'must-not-exist');
  const payload = ['begin', 'EOF', 'OPERIT_CODE_RUNNER_COMMAND', 'OPERIT_CODE_RUNNER_COMMAND_',
    `$(touch ${marker})`, '$HOME', "中文 ' quotes and `backticks`", 'end'].join('\n');
  const script = `text = r"""${payload}"""\nprint(text)`;
  assert.equal(await loaded.runner.run_python({ script }), payload);
  assert.equal(fs.existsSync(marker), false);
});

test('concurrent Python calls keep separate files through cleanup', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  await loaded.runner.run_python({ script: "print('warmup')" });
  const makeScript = label => `from pathlib import Path\nimport time\ntime.sleep(0.1)\nassert ${JSON.stringify(label)} in Path(__file__).read_text()\nprint(${JSON.stringify(label)})\nprint(__file__)`;
  const values = await Promise.all(['FIRST', 'SECOND'].map(label => loaded.runner.run_python({ script: makeScript(label) })));
  const files = values.map(value => value.split('\n')[1]);
  assert.equal(values[0].split('\n')[0], 'FIRST');
  assert.equal(values[1].split('\n')[0], 'SECOND');
  assert.notEqual(files[0], files[1]);
  for (const file of files) assert.equal(fs.existsSync(path.dirname(file)), false);
});

test('successful output containing error keywords is returned unchanged', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  const text = 'failed Error: unable command not found No such file or directory';
  assert.equal(await loaded.runner.run_python({ script: `print(${JSON.stringify(text)})` }), text);
});

test('Python failure still cleans its private directory', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  await assert.rejects(loaded.runner.run_python({ script: "print(__file__, flush=True)\nraise RuntimeError('expected failure')" }), error => {
    const source = error.message.match(/(\/[^\n]+\/operit-code-runner\.[A-Za-z0-9]{8}\/main\.py)/);
    assert.ok(source);
    assert.equal(fs.existsSync(path.dirname(source[1])), false);
    return /expected failure/.test(error.message);
  });
});

test('existing Python file paths and pipe separated arguments are quoted', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  const file = path.join(fixture, "quoted sample's file.py");
  const source = 'import json,sys\nprint(json.dumps(sys.argv[1:], ensure_ascii=False))\n';
  fs.writeFileSync(file, source);
  const args = ['with spaces', "quote's", '$HOME', '中文'];
  const output = await loaded.runner.run_python_file({ file_path: file, python_flags: '-u', script_args: args.join('|') });
  assert.deepEqual(JSON.parse(output), args);
  assert.equal(fs.readFileSync(file, 'utf8'), source);
});

test('noninteractive execution receives EOF on standard input', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  assert.equal(await loaded.runner.run_python({ script: "import sys\nprint(sys.stdin.read() == '')" }), 'True');
});

test('an unmatched flag quote fails at child shell EOF and still cleans up', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  await assert.rejects(loaded.runner.run_python({ script: "print('unreachable')", python_flags: "'" }), /Python 脚本执行失败/);
  assert.ok(loaded.calls.some(call => isCleanup(call.command) && call.options.timeoutMs === 5000));
});

test('Node temporary scripts keep resolving persistent workspace dependencies', { timeout: 60000 }, async () => {
  const loaded = loadRunner();
  const filename = await loaded.runner.run_javascript_node({ script: 'console.log(__filename);' });
  const workspace = path.dirname(path.dirname(filename));
  const modules = path.join(workspace, 'node_modules');
  fs.mkdirSync(modules, { recursive: true });
  const module = fs.mkdtempSync(path.join(modules, 'operit_code_runner_ci_'));
  try {
    fs.writeFileSync(path.join(module, 'index.js'), "module.exports = 'dependency resolved';\n");
    const output = await loaded.runner.run_javascript_node({ script: `console.log(require(${JSON.stringify(path.basename(module))}));` });
    assert.equal(output, 'dependency resolved');
  } finally {
    fs.rmSync(module, { recursive: true, force: true });
  }
  assert.equal(fs.existsSync(path.dirname(filename)), false);
});

test('shell state stays in the child instead of the hidden parent context', () => {
  assert.ok(parentDirectories.length > 0);
  assert.ok(parentDirectories.every(directory => directory === fixture));
});

test('all terminal language entrypoints use distinct temporary artifacts', async () => {
  const loaded = loadRunner(mockTerminal());
  const languages = ['python', 'javascript_node', 'ruby', 'go', 'rust', 'c', 'cpp'];
  for (const language of languages) {
    assert.equal(await loaded.runner[`run_${language}`]({ script: 'source' }), 'ok');
    assert.equal(await loaded.runner[`run_${language}_file`]({ file_path: "/tmp/user's source file" }), 'ok');
  }
  const allocations = loaded.calls.filter(call => call.command.includes('mktemp -d '));
  const cleanups = loaded.calls.filter(call => isCleanup(call.command));
  assert.equal(allocations.length, 11);
  assert.equal(cleanups.length, 11);
  assert.equal(new Set(cleanups.map(call => call.command)).size, 11);
  assert.ok(cleanups.every(call => call.options.timeoutMs === 5000));
  assert.ok(cleanups.every(call => !call.command.includes("user's source file")));
});

test('write failure prevents running an interpreter and still cleans up', async () => {
  const loaded = loadRunner(mockTerminal((command, options) => isWrite(command) ? result(command, options, 'write failed', 1) : undefined));
  await assert.rejects(loaded.runner.run_python({ script: 'source' }), /写入代码文件失败/);
  assert.equal(loaded.calls.some(call => isPythonExecution(call.command)), false);
  assert.ok(loaded.calls.some(call => isCleanup(call.command)));
});

test('invalid temporary directory output is rejected before operation or cleanup', async () => {
  const invalid = ['', '/', '/tmp', 'relative/path', '/tmp/operit-code-runner.short',
    '/tmp/operit-code-runner.12345678/..', '/tmp/operit-code-runner.12345678\n/unexpected'];
  for (const directory of invalid) {
    const loaded = loadRunner(mockTerminal((command, options) => command.includes('mktemp -d ') ? result(command, options, directory) : undefined));
    await assert.rejects(loaded.runner.run_python({ script: 'source' }), /无效路径/);
    assert.equal(loaded.calls.some(call => isWrite(call.command) || isCleanup(call.command)), false);
  }
});

test('temporary directory allocation failure prevents operation and cleanup', async () => {
  const loaded = loadRunner(mockTerminal((command, options) => command.includes('mktemp -d ') ? result(command, options, 'permission denied', 1) : undefined));
  await assert.rejects(loaded.runner.run_python({ script: 'source' }), /创建临时工作目录失败/);
  assert.equal(loaded.calls.some(call => isWrite(call.command) || isCleanup(call.command)), false);
});

test('cleanup timeout has a short deadline and preserves successful output', async () => {
  const loaded = loadRunner(mockTerminal(command => {
    if (isCleanup(command)) throw new Error('cleanup timed out');
  }));
  assert.equal(await loaded.runner.run_python({ script: 'source' }), 'ok');
  assert.ok(loaded.calls.some(call => isCleanup(call.command) && call.options.timeoutMs === 5000));
  assert.ok(loaded.errors.some(args => String(args[0]).includes('清理失败')));
});

test('nonzero cleanup exit preserves successful output and records the error', async () => {
  const loaded = loadRunner(mockTerminal((command, options) => isCleanup(command) ? result(command, options, 'permission denied', 1) : undefined));
  assert.equal(await loaded.runner.run_python({ script: 'source' }), 'ok');
  assert.ok(loaded.errors.some(args => String(args[0]).includes('清理失败')));
});

test('cleanup failure cannot replace the original execution error', async () => {
  const execution = new Error('original execution error');
  const loaded = loadRunner(mockTerminal(command => {
    if (isPythonExecution(command)) throw execution;
    if (isCleanup(command)) throw new Error('cleanup error');
  }));
  await assert.rejects(loaded.runner.run_python({ script: 'source' }), error => error === execution);
});

test('a timeout result is never accepted as a successful execution', async () => {
  const loaded = loadRunner(mockTerminal((command, options) => isPythonExecution(command) ? { ...result(command, options), timedOut: true } : undefined));
  await assert.rejects(loaded.runner.run_python({ script: 'source' }), error => {
    assert.match(error.message, /已超时/);
    assert.equal(error.data.timedOut, true);
    assert.equal(error.data.output, 'ok');
    return true;
  });
  assert.ok(loaded.calls.some(call => isCleanup(call.command)));
});

test('exported tool errors preserve SDK timeout data and partial output', async () => {
  const failure = Object.assign(new Error('execution timed out'), { data: { timedOut: true, output: 'partial output' } });
  const loaded = loadRunner(mockTerminal(command => {
    if (isPythonExecution(command)) throw failure;
  }));
  await loaded.exports.run_python({ script: 'source' });
  assert.equal(loaded.completions.length, 1);
  assert.equal(loaded.completions[0].success, false);
  assert.equal(loaded.completions[0].message, failure.message);
  assert.equal(loaded.completions[0].data, failure.data);
});
