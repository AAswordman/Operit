"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
const demo_ui_js_1 = __importDefault(require("./ui/demo.ui.js"));
const smoke_window_ui_js_1 = __importDefault(require("./ui/smoke_window.ui.js"));
const DEMO_WINDOW_ROUTE = "toolpkg:com.operit.floating_window_demo:ui:smoke_window";
function registerToolPkg() {
    // 悬浮窗内容路由：floatingWindows.open({ route }) 引用的是这里注册的 UiRoute。
    ToolPkg.registerUiRoute({
        id: "smoke_window",
        route: DEMO_WINDOW_ROUTE,
        runtime: "compose_dsl",
        screen: smoke_window_ui_js_1.default,
        params: {},
        title: {
            zh: "悬浮窗内容页",
            en: "Floating Window Content",
        },
    });
    // 工具箱控制页：open/update/hide/show/list/close 的手动入口。
    ToolPkg.registerToolboxUiModule({
        id: "floating_window_demo",
        runtime: "compose_dsl",
        screen: demo_ui_js_1.default,
        params: {},
        title: {
            zh: "悬浮窗 API 控制",
            en: "Floating Window API Demo",
        },
    });
    return true;
}
