/* METADATA
{
  name: code_runner
  display_name: {
    zh: "代码运行器"
    en: "Code Runner"
  }
  description: { zh: "提供多语言代码执行能力，支持JavaScript、Python、Ruby、Go、Rust、C和C++脚本的运行。可直接执行代码字符串或运行外部文件，适用于快速测试、自动化脚本和教学演示。", en: "Multi-language code execution. Supports running JavaScript, Python, Ruby, Go, Rust, C and C++ scripts. You can execute code strings directly or run external files, useful for quick tests, automation, and demos." }
  enabledByDefault: true

  category: "Development"
  // Multiple tools in this package
  tools: [
    {
      name: run_javascript_es5
      description: { zh: "运行自定义 JavaScript (ES5) 脚本。会捕获 console.log 的输出以及最终的返回值。", en: "Run custom JavaScript (ES5). Captures console.log output and the final return value." }
      // This tool takes parameters
      parameters: [
        {
          name: script
          description: { zh: "要执行的 JavaScript 脚本内容", en: "JavaScript script content to execute." }
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_file
      description: { zh: "运行 JavaScript (ES5) 文件。会捕获 console.log 的输出以及最终的返回值。", en: "Run a JavaScript (ES5) file. Captures console.log output and the final return value." }
      parameters: [
        {
          name: file_path
          description: { zh: "JavaScript 文件路径", en: "Path to the JavaScript file." }
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_node
      description: { zh: "使用 Node.js 运行 JavaScript 脚本。返回 stdout/stderr 输出。", en: "Run JavaScript using Node.js. Returns stdout/stderr output." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 JavaScript 脚本内容", en: "JavaScript script content to execute." }
          type: string
          required: true
        },
        {
          name: node_flags
          description: { zh: "Node.js 解释器选项，默认为空。可自定义如 --trace-warnings、--no-warnings 等", en: "Node.js interpreter flags (default: empty). Examples: --trace-warnings, --no-warnings." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_javascript_node_file
      description: { zh: "使用 Node.js 运行 JavaScript 文件。返回 stdout/stderr 输出。", en: "Run a JavaScript file using Node.js. Returns stdout/stderr output." }
      parameters: [
        {
          name: file_path
          description: { zh: "JavaScript 文件路径", en: "Path to the JavaScript file." }
          type: string
          required: true
        },
        {
          name: node_flags
          description: { zh: "Node.js 解释器选项，默认为空。可自定义如 --trace-warnings、--no-warnings 等", en: "Node.js interpreter flags (default: empty). Examples: --trace-warnings, --no-warnings." }
          type: string
          required: false
        }
      ]
    },
    {
      name: install_node_packages
      description: { zh: "在持久 Node 工作目录($HOME/.code_runner/node)中安装 pnpm 包", en: "Install packages with pnpm in the persistent Node workspace ($HOME/.code_runner/node)." }
      parameters: [
        {
          name: packages
          description: { zh: "要安装的包名（用 | 分隔），例如 axios|lodash|@types/node", en: "Package names to install, separated by | (e.g. axios|lodash|@types/node)." }
          type: string
          required: true
        },
        {
          name: save_dev
          description: { zh: "是否作为开发依赖安装（pnpm add -D）", en: "Whether to install as dev dependency (pnpm add -D)." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: install_python_packages
      description: { zh: "在持久虚拟环境(~/.code_runner/py)中安装 Python 包（使用 pip）", en: "Install Python packages in the persistent virtual environment (~/.code_runner/py) using pip." }
      parameters: [
        {
          name: packages
          description: { zh: "要安装的包名（用 | 分隔），例如 numpy|pydantic==2.*", en: "Package names to install, separated by | (e.g. numpy|pydantic==2.*)." }
          type: string
          required: true
        },
        {
          name: upgrade
          description: { zh: "是否升级已安装的包，等价于 pip -U", en: "Whether to upgrade already installed packages (equivalent to pip -U)." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: run_python
      description: { zh: "运行自定义 Python 脚本。会捕获 print 函数的输出。", en: "Run custom Python scripts. Captures output from print()." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Python 脚本内容", en: "Python script content to execute." }
          type: string
          required: true
        },
        {
          name: python_flags
          description: { zh: "Python 解释器选项，默认为空。可自定义如 -O（优化）、-u（无缓冲）等", en: "Python interpreter flags (default: empty). Examples: -O (optimize), -u (unbuffered)." }
          type: string
          required: false
        },
        {
          name: script_args
          description: { zh: "传递给 Python 脚本的参数，使用 | 分隔，例如 arg1|arg with space|--name=alice", en: "Arguments passed to the Python script, separated by | (e.g. arg1|arg with space|--name=alice)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_python_file
      description: { zh: "运行 Python 文件。会捕获 print 函数的输出。", en: "Run a Python file. Captures output from print()." }
      parameters: [
        {
          name: file_path
          description: { zh: "Python 文件路径", en: "Path to the Python file." }
          type: string
          required: true
        },
        {
          name: python_flags
          description: { zh: "Python 解释器选项，默认为空。可自定义如 -O（优化）、-u（无缓冲）等", en: "Python interpreter flags (default: empty). Examples: -O (optimize), -u (unbuffered)." }
          type: string
          required: false
        },
        {
          name: script_args
          description: { zh: "传递给 Python 文件的参数，使用 | 分隔，例如 arg1|arg with space|--name=alice", en: "Arguments passed to the Python file, separated by | (e.g. arg1|arg with space|--name=alice)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_ruby
      description: { zh: "运行自定义 Ruby 脚本", en: "Run custom Ruby scripts." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Ruby 脚本内容", en: "Ruby script content to execute." }
          type: string
          required: true
        },
        {
          name: ruby_flags
          description: { zh: "Ruby 解释器选项，默认为空。可自定义如 --jit（JIT 编译）等", en: "Ruby interpreter flags (default: empty). Example: --jit." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_ruby_file
      description: { zh: "运行 Ruby 文件", en: "Run a Ruby file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Ruby 文件路径", en: "Path to the Ruby file." }
          type: string
          required: true
        },
        {
          name: ruby_flags
          description: { zh: "Ruby 解释器选项，默认为空。可自定义如 --jit（JIT 编译）等", en: "Ruby interpreter flags (default: empty). Example: --jit." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_go
      description: { zh: "运行自定义 Go 代码", en: "Run custom Go code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Go 代码内容", en: "Go source code to execute." }
          type: string
          required: true
        },
        {
          name: build_flags
          description: { zh: "Go 编译选项，默认为空。可自定义如 -ldflags='-s -w'（减小二进制体积）等", en: "Go build flags (default: empty). Example: -ldflags='-s -w' (reduce binary size)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_go_file
      description: { zh: "运行 Go 文件", en: "Run a Go file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Go 文件路径", en: "Path to the Go file." }
          type: string
          required: true
        },
        {
          name: build_flags
          description: { zh: "Go 编译选项，默认为空。可自定义如 -ldflags='-s -w'（减小二进制体积）等", en: "Go build flags (default: empty). Example: -ldflags='-s -w' (reduce binary size)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_rust
      description: { zh: "运行自定义 Rust 代码", en: "Run custom Rust code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Rust 代码内容", en: "Rust source code to execute." }
          type: string
          required: true
        },
        {
          name: cargo_flags
          description: { zh: "Cargo 构建选项，默认为 --release。可自定义如 空字符串（调试模式）、--release --features xxx 等", en: "Cargo build flags (default: --release). Examples: empty string (debug), --release --features xxx." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_rust_file
      description: { zh: "运行 Rust 文件", en: "Run a Rust file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Rust 文件路径", en: "Path to the Rust file." }
          type: string
          required: true
        },
        {
          name: cargo_flags
          description: { zh: "Cargo 构建选项，默认为 --release。可自定义如 空字符串（调试模式）、--release --features xxx 等", en: "Cargo build flags (default: --release). Examples: empty string (debug), --release --features xxx." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_c
      description: { zh: "运行自定义 C 代码", en: "Run custom C code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 C 代码内容", en: "C source code to execute." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_c_file
      description: { zh: "运行 C 文件", en: "Run a C file." }
      parameters: [
        {
          name: file_path
          description: { zh: "C 文件路径", en: "Path to the C file." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_cpp
      description: { zh: "运行自定义 C++ 代码", en: "Run custom C++ code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 C++ 代码内容", en: "C++ source code to execute." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_cpp_file
      description: { zh: "运行 C++ 文件", en: "Run a C++ file." }
      parameters: [
        {
          name: file_path
          description: { zh: "C++ 文件路径", en: "Path to the C++ file." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    }
  ]
}*/
const codeRunner = (function () {
    const CARGO_MIRROR_ENV = 'export CARGO_REGISTRIES_CRATES_IO_REPLACE_WITH="ustc" && export CARGO_REGISTRIES_USTC_INDEX="https://mirrors.ustc.edu.cn/crates.io-index"';
    const CODE_RUNNER_HIDDEN_EXECUTOR_KEY = "code_runner_hidden_executor";
    const CODE_RUNNER_COMMAND_TIMEOUT_MS = 120000;
    const CODE_RUNNER_PREFLIGHT_TIMEOUT_MS = 10000;
    const CODE_RUNNER_CLEANUP_TIMEOUT_MS = 5000;
    const NODE_WORKSPACE_DIR = "$HOME/.code_runner/node";
    function buildIsolatedCommand(command) {
      // 结束标记避开完整命令中的独立行，脚本里的 EOF、引号和多行文本不会截断外层命令。
      const lines = command.split(/\r?\n/);
      let delimiter = "OPERIT_CODE_RUNNER_COMMAND";
      while (lines.includes(delimiter)) delimiter += "_";
      return ["(", 'cd "$HOME" || exit $?', `bash <<'${delimiter}'`, "(", command, ") </dev/null", delimiter, ")"].join("\n");
    }

    async function executeTerminalCommand(command, timeoutMs = CODE_RUNNER_COMMAND_TIMEOUT_MS) {
      // 保留已有隐藏执行器和环境缓存，目录与环境变量修改只发生在本次子 Shell 中。
      const result = await Tools.System.terminal.hiddenExec(buildIsolatedCommand(command), {
        executorKey: CODE_RUNNER_HIDDEN_EXECUTOR_KEY,
        timeoutMs,
      });
      if (result.timedOut === true) {
        throw Object.assign(new Error(`代码执行器命令已超时（${timeoutMs}ms）：\n${result.output}`), { data: result });
      }
      return result;
    }

    function requireCommandSuccess(result, message) {
      if (result.exitCode !== 0) throw new Error(`${message}:\n${result.output}`);
    }

    async function writeScript(filePath, script) {
      // printf 的格式固定，正文始终作为单独参数写入；写入失败不能继续执行旧文件或半截文件。
      const result = await executeTerminalCommand(`printf '%s' '${escapeForShell(script)}' > '${escapeForShell(filePath)}'`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      requireCommandSuccess(result, "写入代码文件失败");
    }

    async function requireExistingFile(filePath, label) {
      const result = await executeTerminalCommand(`test -f '${escapeForShell(filePath)}'`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      requireCommandSuccess(result, `${label}文件不存在或路径错误: ${filePath}`);
    }

    async function withTemporaryDirectory(operation, parentDirectory = "/tmp") {
      const created = await executeTerminalCommand(`mktemp -d "${parentDirectory}/operit-code-runner.XXXXXXXX"`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      requireCommandSuccess(created, "创建临时工作目录失败");
      const directory = created.output.trim();
      if (!/^\/(?:[^\r\n]*\/)?operit-code-runner\.[A-Za-z0-9]{8}$/.test(directory)) {
        throw new Error(`临时工作目录返回了无效路径: ${created.output}`);
      }
      try {
        return await operation(directory);
      } finally {
        // 清理单独限制五秒；清理异常保留日志，不覆盖本次代码的原始结果或异常。
        try {
          const cleanup = await executeTerminalCommand(`rm -rf -- '${escapeForShell(directory)}'`, CODE_RUNNER_CLEANUP_TIMEOUT_MS);
          requireCommandSuccess(cleanup, "清理临时工作目录失败");
        } catch (error) {
          console.error(`代码执行器临时目录清理失败: ${error.message}`);
        }
      }
    }

    async function testNativeRunner(label, script, runner) {
      try {
        const output = await runner({ script });
        if (!output.includes(`${label}运行正常`)) {
          return { success: false, message: `${label}执行器测试失败: ${output}` };
        }
        return { success: true, message: `${label}执行器测试成功` };
      } catch (error) {
        console.error(error);
        return { success: false, message: `${label}执行器测试失败: ${error.message}` };
      }
    }
    // 持久 Python 环境继续保留已安装依赖，每次执行只隔离临时代码文件。
    async function ensurePersistentVenv() {
      const venvDir = "~/.code_runner/py";
      const pythonBin = `${venvDir}/bin/python`;
      const pipBin = `${venvDir}/bin/pip`;
      // 检查和创建放在同一条命令中，避免多次调用之间插入另一轮初始化。
      const setup = await executeTerminalCommand(`if [ ! -x ${pythonBin} ]; then python3 -m venv ${venvDir}; fi`);
      requireCommandSuccess(setup, "创建持久 Python 环境失败");
      return { pythonBin, pipBin };
    }
    // 依赖仍安装到持久虚拟环境中，运行后的临时目录清理不会删除依赖。
    async function install_python_packages(params) {
        const raw = (params.packages || "").trim();
        const pkgs = raw
            .split("|")
            .map(s => s.trim())
            .filter(s => s.length > 0);
        if (!pkgs.length)
            throw new Error("请提供要安装的包列表（用 | 分隔）packages");
        const upgradeFlag = params.upgrade ? "-U" : "";
        const { pythonBin } = await ensurePersistentVenv();
        // 通过 Python 模块入口调用 pip，避免依赖单独的可执行文件。
        const r2 = await executeTerminalCommand(`${pythonBin} -m pip install ${upgradeFlag} ${pkgs.map(p => "'" + escapeForShell(p) + "'").join(" ")}`.trim());
        if (r2.exitCode !== 0) {
            throw new Error(`安装依赖失败：\n${r2.output}`);
        }
        return `Installed with pip:\n${r2.output}`.trim();
    }
    async function ensureNodeAvailable() {
      const result = await executeTerminalCommand("node --version", CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      requireCommandSuccess(result, "Node.js 不可用，请确保已安装 Node.js");
    }
    async function ensurePersistentNodeWorkspace() {
      await ensureNodeAvailable();
      const packageJson = '{"name":"code-runner-node-workspace","private":true}';
      const result = await executeTerminalCommand(`mkdir -p "${NODE_WORKSPACE_DIR}" && if [ ! -f "${NODE_WORKSPACE_DIR}/package.json" ]; then printf '%s' '${escapeForShell(packageJson)}' > "${NODE_WORKSPACE_DIR}/package.json"; fi`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      requireCommandSuccess(result, "准备 Node 工作目录失败");
      return { workspaceDir: NODE_WORKSPACE_DIR };
    }
    async function install_node_packages(params) {
        const raw = (params.packages || "").trim();
        const pkgs = raw
            .split("|")
            .map(s => s.trim())
            .filter(s => s.length > 0);
        if (!pkgs.length)
            throw new Error("请提供要安装的包列表（用 | 分隔）packages");
        const { workspaceDir } = await ensurePersistentNodeWorkspace();
        const saveFlag = params.save_dev ? "-D" : "--save";
        const packageArgs = pkgs.map(p => `'${escapeForShell(p)}'`).join(" ");
        const result = await executeTerminalCommand(`cd "${workspaceDir}" && pnpm add ${saveFlag} ${packageArgs}`);
        if (result.exitCode !== 0) {
            throw new Error(`安装 pnpm 依赖失败:\n${result.output}`);
        }
        return `Installed with pnpm in ${workspaceDir}:\n${result.output}`.trim();
    }
    // 单引号参数内只转义单引号，正文中的变量和命令替换保持原样。
    function escapeForShell(str) {
        return str.replace(/'/g, "'\\''");
    }
    function buildPipeSeparatedShellArgs(raw) {
        if (!raw || raw.trim() === "") {
            return "";
        }
        return raw
            .split("|")
            .map(part => part.trim())
            .filter(part => part.length > 0)
            .map(part => `'${escapeForShell(part)}'`)
            .join(" ");
    }
    // Helper function to execute JavaScript code and capture logs/return value
    async function executeJavaScript(script) {
        const logs = [];
        // Create a proxy for the console object
        const consoleProxy = {
            log: (...args) => {
                const formattedArgs = args.map(arg => {
                    if (arg === null)
                        return 'null';
                    if (arg === undefined)
                        return 'undefined';
                    if (typeof arg === 'object') {
                        try {
                            return JSON.stringify(arg, null, 2);
                        }
                        catch (e) {
                            return arg.toString();
                        }
                    }
                    return arg.toString();
                });
                logs.push(formattedArgs.join(' '));
            },
            warn: (...args) => {
                const formattedArgs = args.map(arg => {
                    if (arg === null)
                        return 'null';
                    if (arg === undefined)
                        return 'undefined';
                    if (typeof arg === 'object') {
                        try {
                            return JSON.stringify(arg, null, 2);
                        }
                        catch (e) {
                            return arg.toString();
                        }
                    }
                    return arg.toString();
                });
                logs.push(`WARN: ${formattedArgs.join(' ')}`);
            },
            error: (...args) => {
                const formattedArgs = args.map(arg => {
                    if (arg === null)
                        return 'null';
                    if (arg === undefined)
                        return 'undefined';
                    if (typeof arg === 'object') {
                        try {
                            return JSON.stringify(arg, null, 2);
                        }
                        catch (e) {
                            return arg.toString();
                        }
                    }
                    return arg.toString();
                });
                logs.push(`ERROR: ${formattedArgs.join(' ')}`);
            },
        };
        try {
            // Use the Function constructor to create a new function in the global scope.
            // Pass the console proxy to it. This is safer than eval.
            const func = new Function('console', `
        // Wrap in an IIFE to handle return statements properly and avoid variable leakage.
        return (function(){
          "use strict";
          ${script}
        })();
      `);
            const returnValue = func(consoleProxy);
            let output = logs.join('\n');
            if (returnValue !== undefined) {
                if (output) {
                    output += '\n';
                }
                let formattedReturnValue;
                if (returnValue === null) {
                    formattedReturnValue = 'null';
                }
                else if (typeof returnValue === 'object') {
                    try {
                        formattedReturnValue = JSON.stringify(returnValue, null, 2);
                    }
                    catch (e) {
                        formattedReturnValue = returnValue.toString();
                    }
                }
                else {
                    formattedReturnValue = returnValue.toString();
                }
                output += `Return value: ${formattedReturnValue}`;
            }
            return output || "(No output from console.log or return value)";
        }
        catch (e) {
            const errorOutput = logs.join('\n');
            if (errorOutput) {
                throw new Error(`Script execution failed: ${e.message}\n\nLogs before error:\n${errorOutput}`);
            }
            else {
                throw new Error(`Script execution failed: ${e.message}`);
            }
        }
    }

    async function main() {

        const results = {
            javascript: await testJavaScript(),
            python: await testPython(),
            ruby: await testRuby(),
            go: await testGo(),
            rust: await testRust(),
            c: await testC(),
            cpp: await testCpp()
        };
        // Format results for display
        let summary = "代码执行器功能测试结果：\n";
        for (const [lang, result] of Object.entries(results)) {
            summary += `${lang}: ${result.success ? '✅ 成功' : '❌ 失败'} - ${result.message}\n`;
        }
        return summary;
    }
    // 测试JavaScript执行功能
    async function testJavaScript() {
        try {
            // 测试简单的JS代码
            const script = "console.log('JavaScript 运行正常'); const testVar = 42; return '测试值: ' + testVar;";
            const result = await executeJavaScript(script);
            const expectedOutput = `JavaScript 运行正常\nReturn value: 测试值: 42`;
            if (result !== expectedOutput) {
                return { success: false, message: `JavaScript执行器测试失败: 期望 "${expectedOutput}", 实际 "${result}"` };
            }
            return { success: true, message: "JavaScript执行器测试成功" };
        }
        catch (error) {
            return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
        }
    }
    // 测试Python执行功能  
    async function testPython() {
      return testNativeRunner("Python", "print('Python运行正常')", run_python);
    }
    // 测试Ruby执行功能
    async function testRuby() {
      return testNativeRunner("Ruby", "puts 'Ruby运行正常'", run_ruby);
    }
    // 测试Go执行功能
    async function testGo() {
      return testNativeRunner("Go", "package main\nimport \"fmt\"\nfunc main() { fmt.Println(\"Go运行正常\") }", run_go);
    }
    // 检查并配置Rust环境
    async function ensureRustConfigured() {
      let checked = await executeTerminalCommand("rustc --version", CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
      if (checked.exitCode === 0) return { success: true, message: "Rust环境已配置" };
      if (checked.output.includes("no default is configured")) {
        const setup = await executeTerminalCommand('export RUSTUP_DIST_SERVER="https://mirrors.ustc.edu.cn/rust-static" && export RUSTUP_UPDATE_ROOT="https://mirrors.ustc.edu.cn/rust-static/rustup" && rustup default stable');
        if (setup.exitCode !== 0) return { success: false, message: `设置 Rust 工具链失败:\n${setup.output}` };
        checked = await executeTerminalCommand("rustc --version", CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
        if (checked.exitCode === 0) return { success: true, message: "Rust环境已配置" };
      }
      return { success: false, message: `Rust 环境检查失败:\n${checked.output}` };
    }
    // 测试Rust执行功能
    async function testRust() {
      return testNativeRunner("Rust", "fn main() { println!(\"Rust运行正常\"); }", run_rust);
    }
    // 测试C执行功能
    async function testC() {
      return testNativeRunner("C", "#include <stdio.h>\nint main() { puts(\"C运行正常\"); return 0; }", run_c);
    }
    // 测试C++执行功能
    async function testCpp() {
      return testNativeRunner("C++", "#include <iostream>\nint main() { std::cout << \"C++运行正常\" << std::endl; return 0; }", run_cpp);
    }
    async function run_javascript_es5(params) {
        const script = params.script;
        if (!script || script.trim() === "") {
            throw new Error("请提供要执行的脚本内容");
        }
        return executeJavaScript(script);
    }
    async function run_javascript_file(params) {
        const filePath = params.file_path;
        if (!filePath || filePath.trim() === "") {
            throw new Error("请提供要执行的 JavaScript 文件路径");
        }
        const fileResult = await Tools.Files.read(filePath);
        if (!fileResult || !fileResult.content) {
            throw new Error(`无法读取文件: ${filePath}`);
        }
        return executeJavaScript(fileResult.content);
    }
    async function run_javascript_node(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 JavaScript 脚本内容");
      const { workspaceDir } = await ensurePersistentNodeWorkspace();
      const nodeFlags = params.node_flags || "";
      // 临时目录位于持久 Node 工作区内，包解析仍能找到上级 node_modules。
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.js`;
        await writeScript(filePath, script);
        const result = await executeTerminalCommand(`cd "${workspaceDir}" && NODE_PATH="${workspaceDir}/node_modules" node ${nodeFlags} '${escapeForShell(filePath)}'`.trim());
        requireCommandSuccess(result, "JavaScript (Node.js) 脚本执行失败");
        return result.output.trim();
      }, workspaceDir);
    }
    async function run_javascript_node_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 JavaScript 文件路径");
      await requireExistingFile(filePath, "JavaScript");
      const { workspaceDir } = await ensurePersistentNodeWorkspace();
      const nodeFlags = params.node_flags || "";
      const result = await executeTerminalCommand(`cd "${workspaceDir}" && NODE_PATH="${workspaceDir}/node_modules" node ${nodeFlags} '${escapeForShell(filePath)}'`.trim());
      requireCommandSuccess(result, "JavaScript (Node.js) 文件执行失败");
      return result.output.trim();
    }
    async function run_python(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 Python 脚本内容");
      const { pythonBin } = await ensurePersistentVenv();
      const pythonFlags = params.python_flags || "";
      const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.py`;
        await writeScript(filePath, script);
        const result = await executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapeForShell(filePath)}' ${scriptArgs}`.trim());
        requireCommandSuccess(result, "Python 脚本执行失败");
        return result.output.trim();
      });
    }
    async function run_python_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 Python 文件路径");
      await requireExistingFile(filePath, "Python");
      const { pythonBin } = await ensurePersistentVenv();
      const pythonFlags = params.python_flags || "";
      const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
      const result = await executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapeForShell(filePath)}' ${scriptArgs}`.trim());
      requireCommandSuccess(result, "Python 文件执行失败");
      return result.output.trim();
    }
    async function run_ruby(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 Ruby 脚本内容");
      const rubyFlags = params.ruby_flags || "";
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.rb`;
        await writeScript(filePath, script);
        const result = await executeTerminalCommand(`ruby ${rubyFlags} '${escapeForShell(filePath)}'`);
        requireCommandSuccess(result, "Ruby 脚本执行失败");
        return result.output.trim();
      });
    }
    async function run_ruby_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 Ruby 文件路径");
      await requireExistingFile(filePath, "Ruby");
      const rubyFlags = params.ruby_flags || "";
      const result = await executeTerminalCommand(`ruby ${rubyFlags} '${escapeForShell(filePath)}'`);
      requireCommandSuccess(result, "Ruby 文件执行失败");
      return result.output.trim();
    }
    async function run_go(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 Go 代码内容");
      const buildFlags = params.build_flags || "";
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.go`;
        const executable = `${directory}/main`;
        await writeScript(filePath, script);
        const compiled = await executeTerminalCommand(`cd '${escapeForShell(directory)}' && go build ${buildFlags} -o main main.go`);
        requireCommandSuccess(compiled, "Go 代码编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "Go 代码执行失败");
        return result.output.trim();
      });
    }
    async function run_go_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 Go 文件路径");
      await requireExistingFile(filePath, "Go");
      const buildFlags = params.build_flags || "";
      return withTemporaryDirectory(async directory => {
        const executable = `${directory}/main`;
        const compiled = await executeTerminalCommand(`go build ${buildFlags} -o '${escapeForShell(executable)}' '${escapeForShell(filePath)}'`);
        requireCommandSuccess(compiled, "Go 文件编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "Go 文件执行失败");
        return result.output.trim();
      });
    }
    async function run_rust(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 Rust 代码内容");

      const rustConfig = await ensureRustConfigured();
      if (!rustConfig.success) throw new Error(rustConfig.message);
      const cargoFlags = params.cargo_flags || "--release";
      const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
      return withTemporaryDirectory(async directory => {
        const created = await executeTerminalCommand(`mkdir -p '${escapeForShell(directory)}/src'`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
        requireCommandSuccess(created, "创建 Rust 源码目录失败");
        const cargoToml = '[package]\nname = "temp_rust_script"\nversion = "0.1.0"\nedition = "2021"\n\n[dependencies]\n';
        await writeScript(`${directory}/Cargo.toml`, cargoToml);
        await writeScript(`${directory}/src/main.rs`, script);
        const compiled = await executeTerminalCommand(`cd '${escapeForShell(directory)}' && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`);
        requireCommandSuccess(compiled, "Rust 代码编译失败");
        const executable = `${directory}/target/${buildMode}/temp_rust_script`;
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`, 30000);
        requireCommandSuccess(result, "Rust 代码执行失败");
        return result.output.trim();
      });
    }
    async function run_rust_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 Rust 文件路径");
      await requireExistingFile(filePath, "Rust");

      const rustConfig = await ensureRustConfigured();
      if (!rustConfig.success) throw new Error(rustConfig.message);
      const cargoFlags = params.cargo_flags || "--release";
      const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
      return withTemporaryDirectory(async directory => {
        const created = await executeTerminalCommand(`mkdir -p '${escapeForShell(directory)}/src'`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
        requireCommandSuccess(created, "创建 Rust 源码目录失败");
        const cargoToml = '[package]\nname = "temp_rust_script"\nversion = "0.1.0"\nedition = "2021"\n\n[dependencies]\n';
        await writeScript(`${directory}/Cargo.toml`, cargoToml);
        const copied = await executeTerminalCommand(`cp -- '${escapeForShell(filePath)}' '${escapeForShell(directory)}/src/main.rs'`, CODE_RUNNER_PREFLIGHT_TIMEOUT_MS);
        requireCommandSuccess(copied, "复制 Rust 源文件失败");
        const compiled = await executeTerminalCommand(`cd '${escapeForShell(directory)}' && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`);
        requireCommandSuccess(compiled, "Rust 代码编译失败");
        const executable = `${directory}/target/${buildMode}/temp_rust_script`;
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`, 30000);
        requireCommandSuccess(result, "Rust 代码执行失败");
        return result.output.trim();
      });
    }
    async function run_c(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 C 代码内容");
      const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.c`;
        const executable = `${directory}/main`;
        await writeScript(filePath, script);
        const compiled = await executeTerminalCommand(`gcc ${compileFlags} '${escapeForShell(filePath)}' -o '${escapeForShell(executable)}'`);
        requireCommandSuccess(compiled, "C 代码编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "C 代码执行失败");
        return result.output.trim();
      });
    }
    async function run_c_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 C 文件路径");
      await requireExistingFile(filePath, "C");
      const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
      return withTemporaryDirectory(async directory => {
        const executable = `${directory}/main`;
        const compiled = await executeTerminalCommand(`gcc ${compileFlags} '${escapeForShell(filePath)}' -o '${escapeForShell(executable)}'`);
        requireCommandSuccess(compiled, "C 文件编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "C 文件执行失败");
        return result.output.trim();
      });
    }
    async function run_cpp(params) {
      const script = params.script;
      if (!script || script.trim() === "") throw new Error("请提供要执行的 C++ 代码内容");
      const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
      return withTemporaryDirectory(async directory => {
        const filePath = `${directory}/main.cpp`;
        const executable = `${directory}/main`;
        await writeScript(filePath, script);
        const compiled = await executeTerminalCommand(`g++ ${compileFlags} '${escapeForShell(filePath)}' -o '${escapeForShell(executable)}'`);
        requireCommandSuccess(compiled, "C++ 代码编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "C++ 代码执行失败");
        return result.output.trim();
      });
    }
    async function run_cpp_file(params) {
      const filePath = params.file_path;
      if (!filePath || filePath.trim() === "") throw new Error("请提供要执行的 C++ 文件路径");
      await requireExistingFile(filePath, "C++");
      const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
      return withTemporaryDirectory(async directory => {
        const executable = `${directory}/main`;
        const compiled = await executeTerminalCommand(`g++ ${compileFlags} '${escapeForShell(filePath)}' -o '${escapeForShell(executable)}'`);
        requireCommandSuccess(compiled, "C++ 文件编译失败");
        const result = await executeTerminalCommand(`'${escapeForShell(executable)}'`);
        requireCommandSuccess(result, "C++ 文件执行失败");
        return result.output.trim();
      });
    }
    function wrap(func) {
        return async (params) => {
            try {
                const result = await func(params);
                complete({
                    success: true,
                    data: result,
                });
            }
            catch (error) {
                console.error(error);
                complete({
                    success: false,
                    message: error.message,
                    error_stack: error.stack,
                    data: error.data,
                });
            }
        };
    }
    return {
        main,
        run_javascript_es5,
        run_javascript_file,
        run_javascript_node,
        run_javascript_node_file,
        install_node_packages,
        install_python_packages,
        run_python,
        run_python_file,
        run_ruby,
        run_ruby_file,
        run_go,
        run_go_file,
        run_rust,
        run_rust_file,
        run_c,
        run_c_file,
        run_cpp,
        run_cpp_file,
        wrap
    };
})();
// 逐个导出
exports.main = codeRunner.wrap(codeRunner.main);
exports.run_javascript_es5 = codeRunner.wrap(codeRunner.run_javascript_es5);
exports.run_javascript_file = codeRunner.wrap(codeRunner.run_javascript_file);
exports.run_javascript_node = codeRunner.wrap(codeRunner.run_javascript_node);
exports.run_javascript_node_file = codeRunner.wrap(codeRunner.run_javascript_node_file);
exports.install_node_packages = codeRunner.wrap(codeRunner.install_node_packages);
exports.install_python_packages = codeRunner.wrap(codeRunner.install_python_packages);
exports.run_python = codeRunner.wrap(codeRunner.run_python);
exports.run_python_file = codeRunner.wrap(codeRunner.run_python_file);
exports.run_ruby = codeRunner.wrap(codeRunner.run_ruby);
exports.run_ruby_file = codeRunner.wrap(codeRunner.run_ruby_file);
exports.run_go = codeRunner.wrap(codeRunner.run_go);
exports.run_go_file = codeRunner.wrap(codeRunner.run_go_file);
exports.run_rust = codeRunner.wrap(codeRunner.run_rust);
exports.run_rust_file = codeRunner.wrap(codeRunner.run_rust_file);
exports.run_c = codeRunner.wrap(codeRunner.run_c);
exports.run_c_file = codeRunner.wrap(codeRunner.run_c_file);
exports.run_cpp = codeRunner.wrap(codeRunner.run_cpp);
exports.run_cpp_file = codeRunner.wrap(codeRunner.run_cpp_file);
