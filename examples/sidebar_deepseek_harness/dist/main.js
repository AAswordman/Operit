"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
const index_ui_js_1 = __importDefault(require("./ui/deepseek_harness_dashboard/index.ui.js"));
const DEEPSEEK_HARNESS_ROUTE = "toolpkg:com.operit.sidebar_deepseek_harness:ui:deepseek_harness_dashboard";
function registerToolPkg() {
    ToolPkg.registerUiRoute({
        id: "deepseek_harness_dashboard",
        route: DEEPSEEK_HARNESS_ROUTE,
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        keepAlive: true,
        title: {
            zh: "DeepSeek Harness",
            en: "DeepSeek Harness",
        },
    });
    ToolPkg.registerNavigationEntry({
        id: "deepseek_harness_dashboard_sidebar",
        route: DEEPSEEK_HARNESS_ROUTE,
        surface: "main_sidebar_plugins",
        title: {
            zh: "DeepSeek Harness",
            en: "DeepSeek Harness",
        },
        icon: "Code",
        order: 131,
    });
    return true;
}
