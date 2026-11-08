"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onApplicationCreate = onApplicationCreate;
const index_ui_js_1 = __importDefault(require("./ui/remote_operit_setup/index.ui.js"));
function registerToolPkg() {
    ToolPkg.registerUiModule({
        id: "remote_operit_setup",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "远程 Operit 配置",
            en: "Remote Operit Setup",
        },
    });
    ToolPkg.registerAppLifecycleHook({
        id: "remote_operit_app_create",
        event: "application_on_create",
        function: onApplicationCreate,
    });
    return true;
}
function onApplicationCreate() {
    return { ok: true };
}
