"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
const index_ui_js_1 = __importDefault(require("./ui/qqbot_settings/index.ui.js"));
require("./shared/qqbot_ipc");
const qqbot_runtime_1 = require("./shared/qqbot_runtime");
const qqbot_auto_reply_1 = require("./shared/qqbot_auto_reply");
function logQQBotStartup(message) {
    console.log(`[qqbot] ${message}`);
}
function registerToolPkg() {
    logQQBotStartup("registerToolPkg start");
    ToolPkg.registerUiModule({
        id: "qqbot_settings",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "QQ Bot 设置",
            en: "QQ Bot Settings",
        },
    });
    ToolPkg.registerAppLifecycleHook({
        id: "qqbot_listener_app_create",
        event: "application_on_create",
        function: qqbot_runtime_1.onQQBotListenerApplicationCreate,
    });
    ToolPkg.registerAppLifecycleHook({
        id: "qqbot_listener_app_foreground",
        event: "application_on_foreground",
        function: qqbot_runtime_1.onQQBotListenerApplicationForeground,
    });
    ToolPkg.registerAppLifecycleHook({
        id: "qqbot_auto_reply_app_create",
        event: "application_on_create",
        function: qqbot_auto_reply_1.onQQBotAutoReplyApplicationCreate,
    });
    ToolPkg.registerAppLifecycleHook({
        id: "qqbot_auto_reply_app_foreground",
        event: "application_on_foreground",
        function: qqbot_auto_reply_1.onQQBotAutoReplyApplicationForeground,
    });
    ToolPkg.registerAppLifecycleHook({
        id: "qqbot_auto_reply_app_terminate",
        event: "application_on_terminate",
        function: qqbot_auto_reply_1.onQQBotAutoReplyApplicationTerminate,
    });
    logQQBotStartup("registerToolPkg hooks registered");
    logQQBotStartup("registerToolPkg done");
    return true;
}
