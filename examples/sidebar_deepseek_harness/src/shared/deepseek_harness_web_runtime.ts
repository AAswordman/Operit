const DEFAULT_PORT = 3080;
const LOOPBACK_HOST = "127.0.0.1";
const DSH_PACKAGE_NAME = "@deepseek-ai/dsh";
const DSH_VERSION = "0.1.0-rc.6";
const TERMINAL_SESSION_NAME = "sidebar_deepseek_harness_web_server";
const LINUX_RUNTIME_DIR = "/root/sidebar_deepseek_harness";
const DSH_HOME_DIR = `${LINUX_RUNTIME_DIR}/dsh-home`;
const LINUX_LOG_PATH = `${LINUX_RUNTIME_DIR}/deepseek-harness-web.log`;
const LINUX_PID_PATH = `${LINUX_RUNTIME_DIR}/deepseek-harness-web.pid`;
const LINUX_PNPM_HOME = "/root/.local/share/pnpm";

export interface DeepSeekHarnessWebServerProgressEvent {
  message: string;
  progress: number;
}

export interface DeepSeekHarnessWebServerResult {
  success: boolean;
  status: "running" | "started" | "failed";
  message: string;
  url: string;
  port: number;
  runtimeDir: string;
  dshHomeDir: string;
  logPath: string;
  sessionId?: string;
  installExitCode?: number;
  installOutput?: string;
  diagnostic?: string;
}

export interface EnsureDeepSeekHarnessWebServerParams {
  forceRestart?: boolean;
  onProgress?: (event: DeepSeekHarnessWebServerProgressEvent) => void;
}

interface HealthCheckResult {
  ok: boolean;
  message: string;
}

function shellQuote(value: string): string {
  return `'${value.replace(/'/g, `'"'"'`)}'`;
}

function bashCommand(script: string): string {
  return `bash -lc ${shellQuote(script)}`;
}

function buildServerUrl(): string {
  return `http://${LOOPBACK_HOST}:${DEFAULT_PORT}`;
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}

function reportProgress(
  onProgress: EnsureDeepSeekHarnessWebServerParams["onProgress"],
  message: string,
  progress: number
): void {
  if (onProgress === undefined) {
    return;
  }
  onProgress({ message, progress });
}

function buildRuntimeEnvironment(): string {
  return [
    `export HOME=${shellQuote("/root")}`,
    `export PNPM_HOME=${shellQuote(LINUX_PNPM_HOME)}`,
    'export PATH="$PNPM_HOME:$PATH"',
    `export DSH_HOME=${shellQuote(DSH_HOME_DIR)}`,
    'export BROWSER=/bin/true',
    `mkdir -p ${shellQuote(LINUX_RUNTIME_DIR)}`,
    `mkdir -p ${shellQuote(DSH_HOME_DIR)}`,
    `mkdir -p ${shellQuote(LINUX_PNPM_HOME)}`,
  ].join("\n");
}

async function getTerminalSessionId(): Promise<string> {
  const session = await Tools.System.terminal.create(TERMINAL_SESSION_NAME);
  const sessionId = session.sessionId.trim();
  if (!sessionId) {
    throw new Error("DeepSeek Harness terminal session was not created.");
  }
  return sessionId;
}

async function executeRuntimeCommand(
  command: string,
  timeoutMs: number
) {
  const sessionId = await getTerminalSessionId();
  return Tools.System.terminal.exec(sessionId, command, timeoutMs);
}

async function executeRuntimeCommandStreaming(
  command: string,
  timeoutMs: number,
  onProgress: EnsureDeepSeekHarnessWebServerParams["onProgress"]
) {
  const sessionId = await getTerminalSessionId();
  return Tools.System.terminal.execStreaming(sessionId, command, {
    timeoutMs,
    onIntermediateResult: (event) => {
      if (event.type !== "chunk" || event.chunk === null || event.chunk === undefined) {
        return;
      }
      const message = event.chunk.replace(/\r/g, "").trim();
      if (message) {
        reportProgress(onProgress, message, 55);
      }
    },
  });
}

async function readHealth(): Promise<HealthCheckResult> {
  try {
    const response = await Tools.Net.httpGet(buildServerUrl());
    if (response.statusCode >= 200 && response.statusCode < 400) {
      return { ok: true, message: "DeepSeek Harness Web is ready." };
    }
    return {
      ok: false,
      message: `DeepSeek Harness returned HTTP ${response.statusCode}.`,
    };
  } catch (error) {
    console.error("DeepSeek Harness health check failed", error);
    return { ok: false, message: "DeepSeek Harness is not reachable on the local runtime." };
  }
}

async function waitForHealth(
  onProgress: EnsureDeepSeekHarnessWebServerParams["onProgress"]
): Promise<HealthCheckResult> {
  let latest = await readHealth();
  for (let attempt = 0; attempt < 30 && !latest.ok; attempt += 1) {
    reportProgress(onProgress, "Waiting for DeepSeek Harness Web", 88);
    await sleep(1000);
    latest = await readHealth();
  }
  return latest;
}

async function stopRuntime(): Promise<void> {
  const command = bashCommand([
    buildRuntimeEnvironment(),
    `if [ -f ${shellQuote(LINUX_PID_PATH)} ]; then`,
    `  pid="$(cat ${shellQuote(LINUX_PID_PATH)})"`,
    "  if [ -n \"$pid\" ] && kill -0 \"$pid\" >/dev/null 2>&1; then",
    "    kill \"$pid\" >/dev/null 2>&1",
    "  fi",
    `  rm -f ${shellQuote(LINUX_PID_PATH)}`,
    "fi",
  ].join("\n"));
  await executeRuntimeCommand(command, 10000);
}

async function installRuntime(
  onProgress: EnsureDeepSeekHarnessWebServerParams["onProgress"]
) {
  const command = bashCommand([
    buildRuntimeEnvironment(),
    "if ! command -v node >/dev/null 2>&1; then",
    "  echo 'Node.js is required in the Linux runtime.' >&2",
    "  exit 11",
    "fi",
    "if ! command -v pnpm >/dev/null 2>&1; then",
    "  echo 'pnpm is required in the Linux runtime.' >&2",
    "  exit 12",
    "fi",
    `cd ${shellQuote(LINUX_RUNTIME_DIR)}`,
    "if [ ! -f package.json ]; then",
    "  pnpm init",
    "fi",
    "if [ ! -x node_modules/.bin/dsh ]; then",
    `  pnpm add --prod --save-exact ${DSH_PACKAGE_NAME}@${DSH_VERSION} --reporter=append-only`,
    "fi",
    "if [ ! -x node_modules/.bin/dsh ]; then",
    "  echo 'DeepSeek Harness CLI was not installed.' >&2",
    "  exit 13",
    "fi",
    "installed_version=\"$(node -p \"require('./node_modules/@deepseek-ai/dsh/package.json').version\")\"",
    `if [ \"$installed_version\" != ${shellQuote(DSH_VERSION)} ]; then`,
    `  echo "Expected ${DSH_PACKAGE_NAME}@${DSH_VERSION}, found $installed_version." >&2`,
    "  exit 14",
    "fi",
    "./node_modules/.bin/dsh --version",
  ].join("\n"));
  return executeRuntimeCommandStreaming(command, 300000, onProgress);
}

async function startRuntime(): Promise<string> {
  const sessionId = await getTerminalSessionId();
  const command = bashCommand([
    buildRuntimeEnvironment(),
    `cd ${shellQuote(LINUX_RUNTIME_DIR)}`,
    `if [ -f ${shellQuote(LINUX_PID_PATH)} ]; then`,
    `  pid="$(cat ${shellQuote(LINUX_PID_PATH)})"`,
    "  if [ -n \"$pid\" ] && kill -0 \"$pid\" >/dev/null 2>&1; then",
    "    kill \"$pid\" >/dev/null 2>&1",
    "  fi",
    `  rm -f ${shellQuote(LINUX_PID_PATH)}`,
    "fi",
    `nohup ./node_modules/.bin/dsh web --host ${LOOPBACK_HOST} --port ${DEFAULT_PORT} --trusted-host ${LOOPBACK_HOST}:${DEFAULT_PORT} >> ${shellQuote(LINUX_LOG_PATH)} 2>&1 &`,
    `echo $! > ${shellQuote(LINUX_PID_PATH)}`,
  ].join("\n"));
  await Tools.System.terminal.exec(sessionId, command, 10000);
  return sessionId;
}

function buildResult(
  success: boolean,
  status: DeepSeekHarnessWebServerResult["status"],
  message: string
): DeepSeekHarnessWebServerResult {
  return {
    success,
    status,
    message,
    url: buildServerUrl(),
    port: DEFAULT_PORT,
    runtimeDir: LINUX_RUNTIME_DIR,
    dshHomeDir: DSH_HOME_DIR,
    logPath: LINUX_LOG_PATH,
  };
}

export async function ensureDeepSeekHarnessWebServer(
  params: EnsureDeepSeekHarnessWebServerParams = {}
): Promise<DeepSeekHarnessWebServerResult> {
  reportProgress(params.onProgress, "Checking DeepSeek Harness Web", 10);
  const existingHealth = await readHealth();
  if (existingHealth.ok && !params.forceRestart) {
    return buildResult(true, "running", existingHealth.message);
  }

  if (params.forceRestart) {
    reportProgress(params.onProgress, "Stopping DeepSeek Harness Web", 20);
    await stopRuntime();
  }

  reportProgress(params.onProgress, "Installing fixed DeepSeek Harness runtime", 35);
  const installResult = await installRuntime(params.onProgress);
  if (installResult.exitCode !== 0 || installResult.timedOut === true) {
    const result = buildResult(false, "failed", "DeepSeek Harness runtime installation failed.");
    result.installExitCode = installResult.exitCode;
    result.installOutput = installResult.output;
    return result;
  }

  reportProgress(params.onProgress, "Starting DeepSeek Harness Web", 80);
  const sessionId = await startRuntime();
  const health = await waitForHealth(params.onProgress);
  if (!health.ok) {
    const result = buildResult(false, "failed", "DeepSeek Harness Web did not become ready.");
    result.sessionId = sessionId;
    result.diagnostic = health.message;
    return result;
  }

  const result = buildResult(true, "started", health.message);
  result.sessionId = sessionId;
  return result;
}
