import type {
  ComposeDslContext,
  ComposeNode,
} from "../../../types/compose-dsl";

/**
 * 工具箱控制页：驱动悬浮窗全生命周期。
 * capabilities / open / update / hide / show / close / list 各一个按钮，
 * 最后一次操作结果显示在页底。
 */
export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;
  const [status, setStatus] = ctx.useState("status", "尚未操作");
  const [sent, setSent] = ctx.useState("sentCount", 0);

  const report = (label: string) => (result: unknown) => {
    setStatus(`${label}: ${JSON.stringify(result)}`);
  };

  const fail = (label: string) => (error: unknown) => {
    setStatus(`${label} 失败: ${error instanceof Error ? error.message : String(error)}`);
  };

  const windows = () => ToolPkg.ui.floatingWindows;
  const windowId = "demo_window";
  const windowRoute = "toolpkg:com.operit.floating_window_demo:ui:smoke_window";

  const action = (label: string, run: () => Promise<unknown>): void => {
    run().then(report(label)).catch(fail(label));
  };

  return UI.Column(
    {
      fillMaxSize: true,
      padding: 16,
      spacing: 8,
    },
    [
      UI.Text({
        text: "悬浮窗 API 控制",
        style: "titleLarge",
        color: colors.onSurface,
      }),
      UI.Button({
        text: "capabilities()",
        onClick: () => action("capabilities", () => windows().capabilities()),
      }),
      UI.Button({
        text: "open()",
        onClick: () =>
          action("open", () =>
            windows().open({
              id: windowId,
              route: windowRoute,
              state: { count: 0 },
              options: { width: 320, height: 240, draggable: true },
            }),
          ),
      }),
      UI.Button({
        text: "update() 计数 +1",
        onClick: () => {
          const next = sent + 1;
          setSent(next);
          action("update", () => windows().update(windowId, { x: 80 }, { count: next }));
        },
      }),
      UI.Button({
        text: "hide()",
        onClick: () => action("hide", () => windows().hide(windowId)),
      }),
      UI.Button({
        text: "show()",
        onClick: () => action("show", () => windows().show(windowId)),
      }),
      UI.Button({
        text: "list()",
        onClick: () => action("list", () => windows().list()),
      }),
      UI.Button({
        text: "close()",
        onClick: () => action("close", () => windows().close(windowId)),
      }),
      UI.Text({
        text: `结果：${status}`,
        style: "bodySmall",
        color: colors.onSurfaceVariant,
      }),
    ],
  );
}
