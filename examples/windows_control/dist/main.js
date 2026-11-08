"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onApplicationCreate = onApplicationCreate;
const index_ui_js_1 = __importDefault(require("./ui/windows_setup/index.ui.js"));
function registerToolPkg() {
    ToolPkg.registerUiModule({
        id: "windows_setup",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "Windows 一键配置",
            en: "Windows Quick Setup",
        },
    });
    ToolPkg.registerAppLifecycleHook({
        id: "windows_app_create",
        event: "application_on_create",
        function: onApplicationCreate,
    });
    return true;
}
function onApplicationCreate() {
    return { ok: true };
}
