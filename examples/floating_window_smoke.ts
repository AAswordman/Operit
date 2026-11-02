/* METADATA
{
  "name": "floating_window_smoke",
  "display_name": {
    "zh": "悬浮窗 API 冒烟",
    "en": "Floating Window API Smoke"
  },
  "description": {
    "zh": "演示 ToolPkg.ui.floatingWindows 全生命周期：capabilities、权限、open、update、show/hide、list、close。需要 api_version 1.1.0。",
    "en": "Exercise the full ToolPkg.ui.floatingWindows lifecycle: capabilities, permission, open, update, show/hide, list, close. Requires api_version 1.1.0."
  },
  "env": [],
  "category": "UI",
  "tools": [{
    "name": "floating_window_smoke",
    "description": {
      "zh": "打开一个演示悬浮窗，依次执行 update/hide/show/list 后关闭，返回每步结果。",
      "en": "Open a demo floating window, run update/hide/show/list, then close it; returns each step's outcome."
    },
    "parameters": []
  }]
 } */

/**
 * ToolPkg.ui.floatingWindows 冒烟示例（@since ToolPkg API 1.1.0）。
 *
 * 窗口内容来自本包 registerUiRoute 注册的 Compose DSL 路由；
 * state 变化经 update 浅合并后驱动重渲染。
 */

async function floating_window_smoke(): Promise<string> {
    const windows = ToolPkg.ui.floatingWindows;

    const caps = await windows.capabilities();
    if (!caps.permissionGranted) {
        await windows.requestPermission();
        return 'overlay permission requested; re-run after granting to continue the smoke test';
    }

    const opened = await windows.open({
        id: 'smoke',
        route: 'screens/smoke',
        state: { count: 0 },
        options: { width: 320, height: 240, draggable: true },
    });

    const updated = await windows.update('smoke', { x: 100 }, { count: 1 });

    await windows.hide('smoke');
    const hidden = (await windows.list()).find((window) => window.id === 'smoke');

    await windows.show('smoke');
    const shown = (await windows.list()).find((window) => window.id === 'smoke');

    await windows.close('smoke');
    const remaining = await windows.list();

    return JSON.stringify({
        capabilities: caps,
        opened: { id: opened.id, visible: opened.visible },
        updated: { x: updated.options.x, visible: updated.visible },
        hiddenVisible: hidden ? hidden.visible : null,
        shownVisible: shown ? shown.visible : null,
        remainingAfterClose: remaining.length,
    });
}
