import packageUI from "./ui/remote_operit_setup/index.ui.js";

export function registerToolPkg() {
  ToolPkg.registerUiModule({
    id: "remote_operit_setup",
    runtime: "compose_dsl",
    screen: packageUI,
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

export function onApplicationCreate() {
  return { ok: true };
}
