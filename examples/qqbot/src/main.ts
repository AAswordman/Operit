import qqbotSettingsScreen from "./ui/qqbot_settings/index.ui.js";
import "./shared/qqbot_ipc";
import {
  onQQBotListenerApplicationCreate as qqbotListenerApplicationCreate,
  onQQBotListenerApplicationForeground as qqbotListenerApplicationForeground
} from "./shared/qqbot_runtime";
import {
  onQQBotAutoReplyApplicationCreate as qqbotAutoReplyApplicationCreate,
  onQQBotAutoReplyApplicationForeground as qqbotAutoReplyApplicationForeground,
  onQQBotAutoReplyApplicationTerminate as qqbotAutoReplyApplicationTerminate
} from "./shared/qqbot_auto_reply";

function logQQBotStartup(message: string): void {
  console.log(`[qqbot] ${message}`);
}

export function registerToolPkg() {
  logQQBotStartup("registerToolPkg start");

  ToolPkg.registerUiModule({
    id: "qqbot_settings",
    runtime: "compose_dsl",
    screen: qqbotSettingsScreen,
    params: {},
    title: {
      zh: "QQ Bot 设置",
      en: "QQ Bot Settings",
    },
  });

  ToolPkg.registerAppLifecycleHook({
    id: "qqbot_listener_app_create",
    event: "application_on_create",
    function: qqbotListenerApplicationCreate,
  });

  ToolPkg.registerAppLifecycleHook({
    id: "qqbot_listener_app_foreground",
    event: "application_on_foreground",
    function: qqbotListenerApplicationForeground,
  });

  ToolPkg.registerAppLifecycleHook({
    id: "qqbot_auto_reply_app_create",
    event: "application_on_create",
    function: qqbotAutoReplyApplicationCreate,
  });

  ToolPkg.registerAppLifecycleHook({
    id: "qqbot_auto_reply_app_foreground",
    event: "application_on_foreground",
    function: qqbotAutoReplyApplicationForeground,
  });

  ToolPkg.registerAppLifecycleHook({
    id: "qqbot_auto_reply_app_terminate",
    event: "application_on_terminate",
    function: qqbotAutoReplyApplicationTerminate,
  });

  logQQBotStartup("registerToolPkg hooks registered");
  logQQBotStartup("registerToolPkg done");

  return true;
}
