"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.withContext = withContext;
const QQBotRuntime = __importStar(require("./qqbot_runtime"));
const QQBotAutoReply = __importStar(require("./qqbot_auto_reply"));
const QQBOT_CONTEXT_RUN_IPC_CHANNEL = "qqbot.context.run";
const qqbotContextFunctions = {};
function previewJson(value, maxLength = 800) {
    try {
        const text = JSON.stringify(value);
        if (typeof text !== "string") {
            return "";
        }
        return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text;
    }
    catch (error) {
        const errorText = error instanceof Error ? error.message : "preview failed";
        console.error(`[qqbot_ipc] preview json failed: ${errorText}`);
        return "[unserializable]";
    }
}
function validateContextEnvName(name) {
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) {
        throw new Error(`withContext env name is not a valid identifier: ${name}`);
    }
}
function buildContextRunnerFactorySource(functionSource, envNames, functionNames) {
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
function normalizeContextRunnerSource(functionSource) {
    const functionNames = Object.keys(qqbotContextFunctions)
        .map((name) => name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
        .join("|");
    if (!functionNames) {
        return functionSource;
    }
    return functionSource
        .replace(new RegExp(`\\(\\s*0\\s*,\\s*[A-Za-z_$][A-Za-z0-9_$]*\\.(${functionNames})\\s*\\)`, "g"), "$1")
        .replace(new RegExp(`\\b[A-Za-z_$][A-Za-z0-9_$]*\\.(${functionNames})\\b`, "g"), "$1");
}
async function executeContextRunner(payload) {
    const envs = payload.envs;
    try {
        const functionSource = normalizeContextRunnerSource(payload.functionSource);
        const factorySource = buildContextRunnerFactorySource(functionSource, Object.keys(envs), Object.keys(qqbotContextFunctions));
        const createRunner = eval(factorySource);
        const runner = createRunner(envs, qqbotContextFunctions);
        if (typeof runner !== "function") {
            throw new Error("withContext runner source did not evaluate to a function");
        }
        return await runner();
    }
    catch (error) {
        const errorText = error instanceof Error ? error.message : "withContext runner failed";
        console.error(`[qqbot_ipc] withContext target execution failed: error=${errorText}, envs=${previewJson(envs)}`);
        throw error;
    }
}
function registerQQBotContextModule(moduleExports) {
    Object.keys(moduleExports).forEach((name) => {
        validateContextEnvName(name);
        const value = moduleExports[name];
        const candidate = value;
        if (typeof candidate === "function") {
            qqbotContextFunctions[name] = candidate;
        }
    });
}
let qqbotContextRunnerRegistered = false;
function registerQQBotContextRunner() {
    if (qqbotContextRunnerRegistered) {
        return;
    }
    qqbotContextRunnerRegistered = true;
    ToolPkg.ipc.on(QQBOT_CONTEXT_RUN_IPC_CHANNEL, async (payload) => await executeContextRunner(payload));
}
registerQQBotContextRunner();
registerQQBotContextModule(QQBotRuntime);
registerQQBotContextModule(QQBotAutoReply);
async function runWithContext(kind, envs, runner, timeoutMs) {
    const payload = {
        functionSource: runner.toString(),
        envs
    };
    try {
        return await ToolPkg.ipc.call(QQBOT_CONTEXT_RUN_IPC_CHANNEL, payload, {
            targetRuntime: kind,
            ...(timeoutMs != null ? { timeoutMs } : {})
        });
    }
    catch (error) {
        const errorText = error instanceof Error ? error.message : "withContext call failed";
        console.error(`[qqbot_ipc] withContext call failed: kind=${kind}, error=${errorText}, envs=${previewJson(envs)}`);
        throw error;
    }
}
function withContext(kind, envs, runner, timeoutMs) {
    if (!runner) {
        throw new Error("withContext requires runner");
    }
    return runWithContext(kind, envs, runner, timeoutMs);
}
__exportStar(require("./qqbot_runtime"), exports);
__exportStar(require("./qqbot_auto_reply"), exports);
