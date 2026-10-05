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
  const NODE_WORKSPACE_DIR = "$HOME/.code_runner/node";

  // Use a persistent hidden executor so commands share one login context without shell wrapping.
  async function executeTerminalCommand(command: string, timeoutMs: number = CODE_RUNNER_COMMAND_TIMEOUT_MS): Promise<import("./types/results").HiddenTerminalCommandResultData> {
    const options: { executorKey: string; timeoutMs?: number } = {
      executorKey: CODE_RUNNER_HIDDEN_EXECUTOR_KEY
    };
    if (timeoutMs !== undefined) {
      options.timeoutMs = timeoutMs;
    }
    return await Tools.System.terminal.hiddenExec(command, options);
  }

  // Ensure a persistent Python venv under ~/.code_runner/py and return python/pip paths
  async function ensurePersistentVenv(): Promise<{ pythonBin: string; pipBin: string }> {
    const venvDir = "~/.code_runner/py";
    const pythonBin = `${venvDir}/bin/python`;
    const pipBin = `${venvDir}/bin/pip`;

    const exists = await executeTerminalCommand(`[ -x ${pythonBin} ] && echo OK || echo NO`);
    if (exists.output.includes("OK")) {
      return { pythonBin, pipBin };
    }

    const setup = await executeTerminalCommand(`python3 -m venv ${venvDir}`);
    if (setup.exitCode !== 0 || hasError(setup.output)) {
      throw new Error(`创建持久 venv 失败：\n${setup.output}`);
    }
    return { pythonBin, pipBin };
  }

  // Install Python packages into the persistent venv using pip
  async function install_python_packages(params: { packages: string; upgrade?: boolean }) {
    const raw = (params.packages || "").trim();
    const pkgs = raw
      .split("|")
      .map(s => s.trim())
      .filter(s => s.length > 0);
    if (!pkgs.length) throw new Error("请提供要安装的包列表（用 | 分隔）packages");

    const upgradeFlag = params.upgrade ? "-U" : "";
    const { pythonBin } = await ensurePersistentVenv();

    // Fallback: use python -m pip to avoid missing pip executable shims
    const r2 = await executeTerminalCommand(`${pythonBin} -m pip install ${upgradeFlag} ${pkgs.join(" ")}`.trim());
    if (r2.exitCode !== 0 || hasError(r2.output)) {
      throw new Error(`安装依赖失败：\n${r2.output}`);
    }
    return `Installed with pip:\n${r2.output}`.trim();
  }

  async function ensureNodeAvailable() {
    const nodeCheckResult = await executeTerminalCommand("node --version");
    if (nodeCheckResult.exitCode !== 0 || hasError(nodeCheckResult.output)) {
      throw new Error("Node.js 不可用，请确保已安装 Node.js");
    }
  }

  async function ensurePersistentNodeWorkspace(): Promise<{ workspaceDir: string }> {
    await ensureNodeAvailable();

    const createDirResult = await executeTerminalCommand(`mkdir -p ${NODE_WORKSPACE_DIR}`);
    if (createDirResult.exitCode !== 0 || hasError(createDirResult.output)) {
      throw new Error(`创建 Node 工作目录失败:\n${createDirResult.output}`);
    }

    const hasPackageJson = await executeTerminalCommand(`[ -f ${NODE_WORKSPACE_DIR}/package.json ] && echo OK || echo NO`);
    if (!hasPackageJson.output.includes("OK")) {
      const initResult = await executeTerminalCommand(
        `cat <<'EOF' > ${NODE_WORKSPACE_DIR}/package.json
{
  "name": "code-runner-node-workspace",
  "private": true
}
EOF`
      );
      if (initResult.exitCode !== 0 || hasError(initResult.output)) {
        throw new Error(`初始化 Node 工作目录失败:\n${initResult.output}`);
      }
    }

    return { workspaceDir: NODE_WORKSPACE_DIR };
  }

  async function install_node_packages(params: { packages: string; save_dev?: boolean }) {
    const raw = (params.packages || "").trim();
    const pkgs = raw
      .split("|")
      .map(s => s.trim())
      .filter(s => s.length > 0);
    if (!pkgs.length) throw new Error("请提供要安装的包列表（用 | 分隔）packages");

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const saveFlag = params.save_dev ? "-D" : "--save";
    const packageArgs = pkgs.map(p => `'${escapeForShell(p)}'`).join(" ");
    const result = await executeTerminalCommand(`cd ${workspaceDir} && pnpm add ${saveFlag} ${packageArgs}`);
    if (result.exitCode !== 0 || hasError(result.output)) {
      throw new Error(`安装 pnpm 依赖失败:\n${result.output}`);
    }
    return `Installed with pnpm in ${workspaceDir}:\n${result.output}`.trim();
  }

  // Helper function to safely escape strings for shell commands
  function escapeForShell(str: string): string {
    return str.replace(/'/g, "'\\''");
  }

  function buildPipeSeparatedShellArgs(raw?: string): string {
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
  async function executeJavaScript(script: string): Promise<string> {
    const logs: string[] = [];

    // Create a proxy for the console object
    const consoleProxy = {
      log: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
          }
          return arg.toString();
        });
        logs.push(formattedArgs.join(' '));
      },
      warn: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
          }
          return arg.toString();
        });
        logs.push(`WARN: ${formattedArgs.join(' ')}`);
      },
      error: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
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
        } else if (typeof returnValue === 'object') {
          try {
            formattedReturnValue = JSON.stringify(returnValue, null, 2);
          } catch (e) {
            formattedReturnValue = returnValue.toString();
          }
        } else {
          formattedReturnValue = returnValue.toString();
        }
        output += `Return value: ${formattedReturnValue}`;
      }

      return output || "(No output from console.log or return value)";

    } catch (e: any) {
      const errorOutput = logs.join('\n');
      if (errorOutput) {
        throw new Error(`Script execution failed: ${e.message}\n\nLogs before error:\n${errorOutput}`);
      } else {
        throw new Error(`Script execution failed: ${e.message}`);
      }
    }
  }

  // Helper function to check for errors in command output when exit code is unreliable
  function hasError(output: string): boolean {
    const errorPatterns = [
      "command not found",
      "No such file or directory",
      "error:",
      "Error:",
      "failed",
      "Failed",
      "unable",
      "Unable"
    ];
    const lowercasedOutput = output.toLowerCase();
    return errorPatterns.some(pattern => lowercasedOutput.includes(pattern));
  }

  async function main() {
    // Ensure /tmp exists
    await executeTerminalCommand("mkdir -p /tmp");

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
      const expectedOutput = `JavaScript 运行正常\nReturn value: "测试值: 42"`;

      if (result !== expectedOutput) {
        return { success: false, message: `JavaScript执行器测试失败: 期望 "${expectedOutput}", 实际 "${result}"` };
      }
      return { success: true, message: "JavaScript执行器测试成功" };
    } catch (error) {
      return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
    }
  }

  // 测试Python执行功能  
  async function testPython() {
    try {
      // 检查Python是否可用
      const pythonCheckResult = await executeTerminalCommand("python3 --version");
      if (pythonCheckResult.exitCode !== 0 || hasError(pythonCheckResult.output)) {
        return { success: false, message: "Python不可用，请确保已安装Python" };
      }

      // 测试简单的Python代码
      const script = "print('Python运行正常')";
      const tempPyFile = "/tmp/test_python.py";
      await executeTerminalCommand(`cat <<'EOF' > ${tempPyFile}\n${script}\nEOF`);
      const runResult = await executeTerminalCommand(`python3 ${tempPyFile}`);
      await executeTerminalCommand(`rm -f ${tempPyFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Python运行正常")) {
        return { success: false, message: `Python执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Python执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Python执行器测试失败: ${error.message}` };
    }
  }

  // 测试Ruby执行功能
  async function testRuby() {
    try {
      // 检查Ruby是否可用
      const rubyCheckResult = await executeTerminalCommand("ruby --version");
      if (rubyCheckResult.exitCode !== 0 || hasError(rubyCheckResult.output)) {
        return { success: false, message: "Ruby不可用，请确保已安装Ruby" };
      }

      // 测试简单的Ruby代码
      const script = "puts 'Ruby运行正常'";
      const tempRbFile = "/tmp/test_ruby.rb";
      await executeTerminalCommand(`cat <<'EOF' > ${tempRbFile}\n${script}\nEOF`);
      const runResult = await executeTerminalCommand(`ruby ${tempRbFile}`);
      await executeTerminalCommand(`rm -f ${tempRbFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Ruby运行正常")) {
        return { success: false, message: `Ruby执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Ruby执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Ruby执行器测试失败: ${error.message}` };
    }
  }

  // 测试Go执行功能
  async function testGo() {
    try {
      // 检查Go是否可用
      const goCheckResult = await executeTerminalCommand("go version");
      if (goCheckResult.exitCode !== 0 || hasError(goCheckResult.output)) {
        return { success: false, message: "Go不可用，请确保已安装Go" };
      }

      // 测试简单的Go代码
      const script = `
package main
import "fmt"
func main() {
  fmt.Println("Go运行正常")
}`;
      const tempGoDir = "/tmp/test_go_project";
      const tempGoFile = `${tempGoDir}/main.go`;
      const tempGoExec = `${tempGoDir}/main`;
      await executeTerminalCommand(`mkdir -p ${tempGoDir}`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempGoFile}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempGoDir} && go build -o main main.go`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -rf ${tempGoDir}`);
        return { success: false, message: `Go 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeTerminalCommand(tempGoExec);
      await executeTerminalCommand(`rm -rf ${tempGoDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Go运行正常")) {
        return { success: false, message: `Go 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Go执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Go执行器测试失败: ${error.message}` };
    }
  }

  // 检查并配置Rust环境
  async function ensureRustConfigured(): Promise<{ success: boolean; message: string }> {
    // 在有效目录中运行，避免 "Could not locate working directory" 错误
    let rustCheckResult = await executeTerminalCommand("cd /tmp && rustc --version");

    if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
      return { success: true, message: "Rust环境已配置" };
    }

    // 如果未配置默认工具链，则尝试设置
    if (rustCheckResult.output.includes("no default is configured")) {
      const setupResult = await executeTerminalCommand('export RUSTUP_DIST_SERVER="https://mirrors.ustc.edu.cn/rust-static" && export RUSTUP_UPDATE_ROOT="https://mirrors.ustc.edu.cn/rust-static/rustup" && rustup default stable');
      if (setupResult.exitCode !== 0 || hasError(setupResult.output)) {
        return { success: false, message: `运行 'rustup default stable' 失败: ${setupResult.output}` };
      }

      // 再次检查
      rustCheckResult = await executeTerminalCommand("cd /tmp && rustc --version");
      if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
        return { success: true, message: "Rust环境已自动配置" };
      }
    }

    return { success: false, message: `Rust环境检查失败: ${rustCheckResult.output}` };
  }

  // 测试Rust执行功能
  async function testRust() {
    try {
      const rustConfig = await ensureRustConfigured();
      if (!rustConfig.success) {
        return { success: false, message: rustConfig.message };
      }

      // 测试简单的Rust代码
      const script = `
fn main() {
  println!("Rust运行正常");
}`;
      const tempRustDir = "/tmp/test_rust_project";
      const tempRustSrcDir = `${tempRustDir}/src`;
      const tempRustFile = `${tempRustSrcDir}/main.rs`;
      const cargoToml = `
[package]
name = "test_rust"
version = "0.1.0"
edition = "2021"
[dependencies]
`;
      await executeTerminalCommand(`mkdir -p ${tempRustSrcDir}`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempRustDir}/Cargo.toml\n${cargoToml}\nEOF`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempRustFile}\n${script}\nEOF`);

      // 在有效目录中运行 cargo
      const compileResult = await executeTerminalCommand(`cd ${tempRustDir} && ${CARGO_MIRROR_ENV} && cargo build --release`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -rf ${tempRustDir}`);
        return { success: false, message: `Rust 编译失败: ${compileResult.output}` };
      }

      const execPath = `${tempRustDir}/target/release/test_rust`;
      const runResult = await executeTerminalCommand(execPath);
      await executeTerminalCommand(`rm -rf ${tempRustDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Rust运行正常")) {
        return { success: false, message: `Rust 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Rust执行器测试成功" };
    } catch (error) {
      return { success: false, message: `Rust执行器测试失败: ${error.message}` };
    }
  }

  // 测试C执行功能
  async function testC() {
    try {
      // 检查gcc是否可用
      const gccCheckResult = await executeTerminalCommand("gcc --version");
      if (gccCheckResult.exitCode !== 0 || hasError(gccCheckResult.output)) {
        return { success: false, message: "GCC不可用，请确保已安装gcc" };
      }

      // 测试简单的C代码
      const script = `
#include <stdio.h>
int main() {
  printf("C运行正常\\n");
  return 0;
}`;
      const tempCFile = "/tmp/test_c.c";
      const tempCExec = "/tmp/test_c";
      await executeTerminalCommand(`cat <<'EOF' > ${tempCFile}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`gcc -O3 -march=native -fopenmp ${tempCFile} -o ${tempCExec}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -f ${tempCFile} ${tempCExec}`);
        return { success: false, message: `C 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeTerminalCommand(tempCExec);
      await executeTerminalCommand(`rm -f ${tempCFile} ${tempCExec}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("C运行正常")) {
        return { success: false, message: `C 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "C执行器测试成功" };
    } catch (error) {
      return { success: false, message: `C执行器测试失败: ${error.message}` };
    }
  }

  // 测试C++执行功能
  async function testCpp() {
    try {
      // 检查g++是否可用
      const gppCheckResult = await executeTerminalCommand("g++ --version");
      if (gppCheckResult.exitCode !== 0 || hasError(gppCheckResult.output)) {
        return { success: false, message: "G++不可用，请确保已安装g++" };
      }

      // 测试简单的C++代码
      const script = `
#include <iostream>
int main() {
  std::cout << "C++运行正常" << std::endl;
  return 0;
}`;
      const tempCppFile = "/tmp/test_cpp.cpp";
      const tempCppExec = "/tmp/test_cpp";
      await executeTerminalCommand(`cat <<'EOF' > ${tempCppFile}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`g++ -O3 -march=native -fopenmp ${tempCppFile} -o ${tempCppExec}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeTerminalCommand(`rm -f ${tempCppFile} ${tempCppExec}`);
        return { success: false, message: `C++ 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeTerminalCommand(tempCppExec);
      await executeTerminalCommand(`rm -f ${tempCppFile} ${tempCppExec}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("C++运行正常")) {
        return { success: false, message: `C++ 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "C++执行器测试成功" };
    } catch (error) {
      return { success: false, message: `C++执行器测试失败: ${error.message}` };
    }
  }

  async function run_javascript_es5(params: { script: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的脚本内容");
    }
    return executeJavaScript(script);
  }

  async function run_javascript_file(params: { file_path: string }) {
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

  async function run_javascript_node(params: { script: string; node_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 JavaScript 脚本内容");
    }

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const nodeFlags = params.node_flags || "";
    const tempFileName = `temp_script_node_${Date.now()}.js`;
    const tempFilePath = `${workspaceDir}/${tempFileName}`;
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);
      const result = await executeTerminalCommand(`cd ${workspaceDir} && NODE_PATH=${workspaceDir}/node_modules node ${nodeFlags} ${tempFileName}`.trim());
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`JavaScript (Node.js) 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_javascript_node_file(params: { file_path: string; node_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 JavaScript 文件路径");
    }

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`JavaScript 文件不存在或路径错误: ${filePath}`);
    }

    const nodeFlags = params.node_flags || "";
    const result = await executeTerminalCommand(`cd ${workspaceDir} && NODE_PATH=${workspaceDir}/node_modules node ${nodeFlags} '${escapedPath}'`.trim());
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`JavaScript (Node.js) 文件执行失败:\n${result.output}`);
    }
  }

  async function run_python(params: { script: string; python_flags?: string; script_args?: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Python 脚本内容");
    }

    // Use persistent venv interpreter
    const { pythonBin } = await ensurePersistentVenv();
    const pythonFlags = params.python_flags || "";
    const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
    const tempFilePath = "/tmp/temp_script.py";
    const escapedTempFilePath = escapeForShell(tempFilePath);
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);
      const result = await executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapedTempFilePath}' ${scriptArgs}`.trim());
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Python 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_python_file(params: { file_path: string; python_flags?: string; script_args?: string }) {
    const filePath = params.file_path;

    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Python 文件路径");
    }

    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Python 文件不存在或路径错误: ${filePath}`);
    }

    // Use persistent venv interpreter
    const { pythonBin } = await ensurePersistentVenv();
    const pythonFlags = params.python_flags || "";
    const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
    const result = await executeTerminalCommand(`${pythonBin} ${pythonFlags} '${escapedPath}' ${scriptArgs}`.trim());
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`Python 文件执行失败:\n${result.output}`);
    }
  }

  async function run_ruby(params: { script: string; ruby_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Ruby 脚本内容");
    }

    const rubyFlags = params.ruby_flags || "";
    const tempFilePath = "/tmp/temp_script.rb";
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);
      const result = await executeTerminalCommand(`ruby ${rubyFlags} ${tempFilePath}`);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Ruby 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_ruby_file(params: { file_path: string; ruby_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Ruby 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Ruby 文件不存在或路径错误: ${filePath}`);
    }

    const rubyFlags = params.ruby_flags || "";
    const result = await executeTerminalCommand(`ruby ${rubyFlags} ${filePath}`);
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`Ruby 文件执行失败:\n${result.output}`);
    }
  }

  async function run_go(params: { script: string; build_flags?: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Go 代码内容");
    }

    const buildFlags = params.build_flags || "";
    const tempDirPath = "/tmp/temp_go";
    const tempFilePath = `${tempDirPath}/main.go`;

    try {
      await executeTerminalCommand(`mkdir -p ${tempDirPath}`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && go build ${buildFlags} -o main main.go`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(`${tempDirPath}/main`);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_go_file(params: { file_path: string; build_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Go 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Go 文件不存在或路径错误: ${filePath}`);
    }

    const buildFlags = params.build_flags || "";
    const tempExecPath = "/tmp/temp_go_exec";
    try {
      const compileResult = await executeTerminalCommand(`go build ${buildFlags} -o ${tempExecPath} ${filePath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_rust(params: { script: string; cargo_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Rust 代码内容");
    }

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const cargoFlags = params.cargo_flags || "--release";
    const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
    const tempDirPath = "/tmp/temp_rust_project";
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/Cargo.toml\n${cargoToml}\nEOF`);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/src/main.rs\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 代码编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/${buildMode}/temp_rust_script`;
      const result = await executeTerminalCommand(execPath, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_rust_file(params: { file_path: string; cargo_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Rust 文件路径");
    }
    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Rust 文件不存在或路径错误: ${filePath}`);
    }

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const cargoFlags = params.cargo_flags || "--release";
    const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
    const tempDirPath = "/tmp/temp_rust_project";
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeTerminalCommand(`mkdir -p ${tempDirPath}/src`, 10000);
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/Cargo.toml\n${cargoToml}\nEOF`);

      const readResult = await executeTerminalCommand(`cat ${filePath}`);
      if (readResult.exitCode !== 0 || hasError(readResult.output)) {
        throw new Error(`无法读取文件: ${filePath}`);
      }
      const fileContent = readResult.output;
      await executeTerminalCommand(`cat <<'EOF' > ${tempDirPath}/src/main.rs\n${fileContent}\nEOF`);

      const compileResult = await executeTerminalCommand(`cd ${tempDirPath} && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 文件编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/${buildMode}/temp_rust_script`;
      const result = await executeTerminalCommand(execPath, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 项目执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_c(params: { script: string; compile_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 C 代码内容");
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempFilePath = "/tmp/temp_script.c";
    const tempExecPath = "/tmp/temp_script_c_exec";
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`gcc ${compileFlags} ${tempFilePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath} ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_c_file(params: { file_path: string; compile_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 C 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`C 文件不存在或路径错误: ${filePath}`);
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempExecPath = "/tmp/temp_c_exec";
    try {
      const compileResult = await executeTerminalCommand(`gcc ${compileFlags} ${filePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_cpp(params: { script: string; compile_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 C++ 代码内容");
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempFilePath = "/tmp/temp_script.cpp";
    const tempExecPath = "/tmp/temp_script_cpp_exec";
    try {
      await executeTerminalCommand(`cat <<'EOF' > ${tempFilePath}\n${script}\nEOF`);

      const compileResult = await executeTerminalCommand(`g++ ${compileFlags} ${tempFilePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C++ 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C++ 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempFilePath} ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_cpp_file(params: { file_path: string; compile_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 C++ 文件路径");
    }

    const fileExistsResult = await executeTerminalCommand(`test -f ${filePath}`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`C++ 文件不存在或路径错误: ${filePath}`);
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempExecPath = "/tmp/temp_cpp_exec";
    try {
      const compileResult = await executeTerminalCommand(`g++ ${compileFlags} ${filePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C++ 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(tempExecPath);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C++ 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  function wrap(func: (params: any) => Promise<any>) {
    return async (params: any) => {
      try {
        const result = await func(params);
        complete({
          success: true,
          data: result,
        });
      } catch (error: any) {
        complete({
          success: false,
          message: error.message,
          error_stack: error.stack,
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
