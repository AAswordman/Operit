import packageUI from "./linux_ssh_setup/index.ui.js";

export function registerToolPkg() {
  ToolPkg.registerUiModule({
    id: "linux_ssh_setup",
    runtime: "compose_dsl",
    screen: packageUI,
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

export function onApplicationCreate() {
  return { ok: true };
}
