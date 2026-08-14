import deepSeekHarnessDashboard from "./ui/deepseek_harness_dashboard/index.ui.js";

const DEEPSEEK_HARNESS_ROUTE =
  "toolpkg:com.operit.sidebar_deepseek_harness:ui:deepseek_harness_dashboard";

export function registerToolPkg(): boolean {
  ToolPkg.registerUiRoute({
    id: "deepseek_harness_dashboard",
    route: DEEPSEEK_HARNESS_ROUTE,
    runtime: "compose_dsl",
    screen: deepSeekHarnessDashboard,
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
    icon: Icons.Code,
    order: 131,
  });

  return true;
}
