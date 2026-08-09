import type { ComposeDslContext, ComposeNode } from "../../../../types/compose-dsl";
import {
  ensureOpenCodeWebServer,
  type OpenCodeWebServerProgressEvent,
  type OpenCodeWebServerResult,
} from "../../shared/opencode_web_runtime.js";

type EnsureServerResult = OpenCodeWebServerResult;

function parseToolResult<T>(value: unknown): T | null {
  if (!value) {
    return null;
  }
  if (typeof value === "string") {
    try {
      return JSON.parse(value) as T;
    } catch (_error) {
      return null;
    }
  }
  return value as T;
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const { UI } = ctx;
  const colors = ctx.MaterialTheme.colorScheme;

  const [initialized, setInitialized] = ctx.useState("initialized", false);
  const [loading, setLoading] = ctx.useState("loading", false);
  const [serverUrl, setServerUrl] = ctx.useState("serverUrl", "");
  const [errorText, setErrorText] = ctx.useState("errorText", "");
  const [reloadToken, setReloadToken] = ctx.useState("reloadToken", "0");
  const [pageLoading, setPageLoading] = ctx.useState("pageLoading", false);
  const [pageProgress, setPageProgress] = ctx.useState("pageProgress", 0);
  const [deployProgress, setDeployProgress] = ctx.useState("deployProgress", 0);
  const [statusDetail, setStatusDetail] = ctx.useState(
    "statusDetail",
    "正在准备 OpenCode 侧边栏"
  );

  function clampProgress(value: number): number {
    if (!Number.isFinite(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, Math.round(value)));
  }

  function normalizeStatusLine(raw: unknown): string {
    const text = String(raw ?? "")
      .replace(/\s+/g, " ")
      .trim();
    return text || "正在处理中";
  }

  function buildOverallProgress(): number {
    if (pageLoading) {
      return clampProgress(88 + pageProgress * 0.12);
    }
    return clampProgress(deployProgress);
  }

  async function ensureServer(): Promise<void> {
    setLoading(true);
    setErrorText("");
    setPageLoading(false);
    setPageProgress(0);
    setDeployProgress(4);
    setStatusDetail("检查 pnpm / OpenCode 运行时并拉起网页");
    try {
      const result = parseToolResult<EnsureServerResult>(
        await ensureOpenCodeWebServer({
          on_progress: (event: OpenCodeWebServerProgressEvent) => {
            setStatusDetail(normalizeStatusLine(event?.message));
            if (typeof event?.progress === "number") {
              setDeployProgress(clampProgress(event.progress));
            }
          },
        })
      );
      if (!result?.success || !result?.url) {
        const message = result?.message || "OpenCode Web 启动失败";
        setErrorText(message);
        setStatusDetail(message);
        return;
      }
      setServerUrl(result.url);
      setReloadToken(String(Date.now()));
      setStatusDetail("正在连接 OpenCode 页面");
      setDeployProgress(88);
      setPageLoading(true);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setErrorText(message);
      setPageLoading(false);
      setStatusDetail(message);
    } finally {
      setLoading(false);
    }
  }

  const progressValue = buildOverallProgress();
  const overlayDetail = errorText ? errorText : statusDetail;
  const isOverlayVisible = Boolean(errorText) || loading || pageLoading || !serverUrl;

  const loadingOverlay = UI.Box(
    {
      fillMaxSize: true,
      zIndex: 1,
      backgroundBrush: {
        type: "verticalGradient",
        colors: [
          colors.surface.copy({ alpha: 0.96 }),
          colors.surface.copy({ alpha: 0.96 }),
        ],
      },
    },
    UI.Column(
      {
        fillMaxSize: true,
        paddingHorizontal: 24,
        spacing: 14,
        horizontalAlignment: "center",
        verticalArrangement: "center",
      },
      [
        UI.Box(
          {
            width: 88,
            height: 88,
            contentAlignment: "center",
          },
          !errorText
            ? UI.Icon({
                name: "Code",
                size: 34,
                tint: colors.primary,
                spin: true,
                spinDurationMs: 850,
              })
            : UI.Icon({
                name: "error",
                size: 32,
                tint: colors.error,
              })
        ),
        UI.Box(
          {
            width: 300,
            contentAlignment: "center",
          },
          !errorText
            ? UI.Column(
                {
                  width: 220,
                  spacing: 8,
                  horizontalAlignment: "center",
                },
                [
                  UI.Text({
                    text: `${progressValue}%`,
                    style: "labelMedium",
                    color: colors.primary,
                    maxLines: 1,
                    overflow: "ellipsis",
                  }),
                  UI.LinearProgressIndicator({
                    width: 220,
                    progress: progressValue / 100,
                  }),
                ]
              )
            : UI.Spacer({ width: 0, height: 0 })
        ),
        UI.Box(
          {
            width: 300,
            contentAlignment: "center",
          },
          UI.Text({
            text: overlayDetail,
            style: "bodyMedium",
            color: errorText ? colors.error : colors.onSurfaceVariant,
            maxLines: 1,
            overflow: "ellipsis",
          })
        ),
      ]
    )
  );

  const content = serverUrl
    ? UI.Box(
        {
          fillMaxSize: true,
        },
        [
          UI.WebView({
            key: `opencode_webview_${reloadToken}`,
            fillMaxSize: true,
            url: serverUrl,
            javaScriptEnabled: true,
            domStorageEnabled: true,
            allowFileAccess: true,
            allowContentAccess: true,
            supportZoom: false,
            useWideViewPort: true,
            loadWithOverviewMode: true,
            onPageStarted: () => {
              setPageLoading(true);
              setPageProgress(0);
              setDeployProgress(88);
              setStatusDetail("页面已打开，正在请求 OpenCode 资源");
            },
            onProgressChanged: (event) => {
              const nextProgress = clampProgress(Number(event?.progress ?? 0));
              setPageProgress(nextProgress);
              setStatusDetail(
                nextProgress > 0 ? "OpenCode 页面资源加载中" : "正在建立页面连接"
              );
            },
            onPageFinished: () => {
              setPageProgress(100);
              setDeployProgress(100);
              setStatusDetail("OpenCode 页面已就绪");
              setPageLoading(false);
            },
            onReceivedError: () => {},
          }),
          isOverlayVisible ? loadingOverlay : UI.Spacer({ height: 0 }),
        ]
      )
    : loadingOverlay;

  return UI.Box(
    {
      fillMaxSize: true,
      onLoad: async () => {
        if (!initialized || !serverUrl) {
          setInitialized(true);
          await ensureServer();
        }
      },
    },
    content
  );
}
