import demoScreen from "./ui/demo.ui.js";
import smokeWindowScreen from "./ui/smoke_window.ui.js";

const DEMO_WINDOW_ROUTE = "toolpkg:com.operit.floating_window_demo:ui:smoke_window";

export function registerToolPkg(): boolean {
  // 悬浮窗内容路由：floatingWindows.open({ route }) 引用的是这里注册的 UiRoute。
  ToolPkg.registerUiRoute({
    id: "smoke_window",
    route: DEMO_WINDOW_ROUTE,
    runtime: "compose_dsl",
    screen: smokeWindowScreen,
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
    screen: demoScreen,
    params: {},
    title: {
      zh: "悬浮窗 API 控制",
      en: "Floating Window API Demo",
    },
  });

  return true;
}
