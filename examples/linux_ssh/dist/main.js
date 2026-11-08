"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onApplicationCreate = onApplicationCreate;
const index_ui_js_1 = __importDefault(require("./linux_ssh_setup/index.ui.js"));
function registerToolPkg() {
    ToolPkg.registerUiModule({
        id: "linux_ssh_setup",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "Linux SSH 管理",
            en: "Linux SSH Manager",
        },
    });
    ToolPkg.registerAppLifecycleHook({
        id: "linux_ssh_app_create",
        event: "application_on_create",
        function: onApplicationCreate,
    });
    return true;
}
function onApplicationCreate() {
    return { ok: true };
}
