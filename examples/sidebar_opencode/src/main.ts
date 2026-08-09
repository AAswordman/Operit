import opencodeDashboardScreen from "./ui/opencode_dashboard/index.ui.js";

const OPENCODE_ROUTE = "toolpkg:com.operit.sidebar_opencode:ui:opencode_dashboard";

export function registerToolPkg(): boolean {
  ToolPkg.registerUiRoute({
    id: "opencode_dashboard",
    route: OPENCODE_ROUTE,
    runtime: "compose_dsl",
    screen: opencodeDashboardScreen,
    params: {},
    keepAlive: false,
    title: {
      zh: "OpenCode",
      en: "OpenCode",
    },
  });

  ToolPkg.registerNavigationEntry({
    id: "opencode_dashboard_sidebar",
    route: OPENCODE_ROUTE,
    surface: "main_sidebar_plugins",
    title: {
      zh: "OpenCode",
      en: "OpenCode",
    },
    icon: Icons.Code,
    order: 130,
  });

  return true;
}
