import * as QQBotRuntime from "./qqbot_runtime";
import * as QQBotAutoReply from "./qqbot_auto_reply";

const QQBOT_CONTEXT_RUN_IPC_CHANNEL = "qqbot.context.run";

type QQBotContextRunner<TResult extends object> = () => TResult | Promise<TResult>;
type QQBotContextFunction = (...args: readonly object[]) => object | Promise<object>;
type QQBotContextModuleValue = QQBotContextFunction | object | string | number | boolean | null | undefined;
type QQBotContextModule = Record<string, QQBotContextModuleValue>;

type QQBotContextRunPayload = {
  functionSource: string;
  envs: object;
};

const qqbotContextFunctions: Record<string, QQBotContextFunction> = {};

function previewJson(
  value: object | string | number | boolean | null | undefined,
  maxLength = 800
): string {
  try {
    const text = JSON.stringify(value);
    if (typeof text !== "string") {
      return "";
    }
    return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "preview failed";
    console.error(`[qqbot_ipc] preview json failed: ${errorText}`);
    return "[unserializable]";
  }
}

function validateContextEnvName(name: string): void {
  if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) {
    throw new Error(`withContext env name is not a valid identifier: ${name}`);
  }
}

function buildContextRunnerFactorySource(
  functionSource: string,
  envNames: string[],
  functionNames: string[]
): string {
  const envBindings = envNames
    .map((name) => {
      validateContextEnvName(name);
      return `const ${name} = __qqbotContextEnvs[${JSON.stringify(name)}];`;
    })
    .join("\n");
  const functionBindings = functionNames
    .map((name) => {
      validateContextEnvName(name);
      return `const ${name} = __qqbotContextFunctions[${JSON.stringify(name)}];`;
    })
    .join("\n");
  return `(function(__qqbotContextEnvs, __qqbotContextFunctions) {
${envBindings}
${functionBindings}
return (${functionSource});
})`;
}

function normalizeContextRunnerSource(functionSource: string): string {
  const functionNames = Object.keys(qqbotContextFunctions)
    .map((name) => name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("|");
  if (!functionNames) {
    return functionSource;
  }
  return functionSource
    .replace(
      new RegExp(`\\(\\s*0\\s*,\\s*[A-Za-z_$][A-Za-z0-9_$]*\\.(${functionNames})\\s*\\)`, "g"),
      "$1"
    )
    .replace(
      new RegExp(`\\b[A-Za-z_$][A-Za-z0-9_$]*\\.(${functionNames})\\b`, "g"),
      "$1"
    );
}

async function executeContextRunner(payload: QQBotContextRunPayload): Promise<object> {
  const envs = payload.envs;
  try {
    const functionSource = normalizeContextRunnerSource(payload.functionSource);
    const factorySource = buildContextRunnerFactorySource(
      functionSource,
      Object.keys(envs),
      Object.keys(qqbotContextFunctions)
    );
    const createRunner = eval(factorySource) as (
      envs: object,
      functions: Record<string, QQBotContextFunction>
    ) => QQBotContextRunner<object>;
    const runner = createRunner(envs, qqbotContextFunctions);
    if (typeof runner !== "function") {
      throw new Error("withContext runner source did not evaluate to a function");
    }
    return await runner();
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "withContext runner failed";
    console.error(
      `[qqbot_ipc] withContext target execution failed: error=${errorText}, envs=${previewJson(envs)}`
    );
    throw error;
  }
}

function registerQQBotContextModule(moduleExports: QQBotContextModule): void {
  Object.keys(moduleExports).forEach((name) => {
    validateContextEnvName(name);
    const value = moduleExports[name];
    const candidate = value as QQBotContextFunction;
    if (typeof candidate === "function") {
      qqbotContextFunctions[name] = candidate;
    }
  });
}

let qqbotContextRunnerRegistered = false;

function registerQQBotContextRunner(): void {
  if (qqbotContextRunnerRegistered) {
    return;
  }
  qqbotContextRunnerRegistered = true;
  ToolPkg.ipc.on<QQBotContextRunPayload, object>(
    QQBOT_CONTEXT_RUN_IPC_CHANNEL,
    async (payload) => await executeContextRunner(payload)
  );
}

registerQQBotContextRunner();
registerQQBotContextModule(QQBotRuntime as QQBotContextModule);
registerQQBotContextModule(QQBotAutoReply as QQBotContextModule);

async function runWithContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  runner: QQBotContextRunner<TResult>,
  timeoutMs?: number
): Promise<TResult> {
  const payload: QQBotContextRunPayload = {
    functionSource: runner.toString(),
    envs
  };
  try {
    return await ToolPkg.ipc.call<QQBotContextRunPayload, TResult>(
      QQBOT_CONTEXT_RUN_IPC_CHANNEL,
      payload,
      {
        targetRuntime: kind,
        ...(timeoutMs != null ? { timeoutMs } : {})
      }
    );
  } catch (error) {
    const errorText = error instanceof Error ? error.message : "withContext call failed";
    console.error(
      `[qqbot_ipc] withContext call failed: kind=${kind}, error=${errorText}, envs=${previewJson(envs)}`
    );
    throw error;
  }
}

export function withContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  runner: QQBotContextRunner<TResult>,
  timeoutMs?: number
): Promise<TResult>;

export function withContext<TResult extends object>(
  kind: ToolPkg.RuntimeKind,
  envs: object,
  runner?: QQBotContextRunner<TResult>,
  timeoutMs?: number
): Promise<TResult> {
  if (!runner) {
    throw new Error("withContext requires runner");
  }
  return runWithContext(kind, envs, runner, timeoutMs);
}

export * from "./qqbot_runtime";
export * from "./qqbot_auto_reply";
