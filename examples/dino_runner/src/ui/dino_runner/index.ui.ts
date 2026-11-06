import type {
  ComposeDslContext,
  ComposeNode,
  ComposeWebViewNavigationDecision,
  ComposeWebViewNavigationRequest,
  ComposeWebViewJavascriptInterface,
  ComposeWebViewResourceDecision,
  ComposeWebViewResourceRequest,
} from "../../../../types/compose-dsl";
import {
  ARCADE_BONUS_TOKEN_BASE64,
  ARCADE_HOST_INTERFACE_NAME,
  ARCADE_RESOURCE_SPECS,
  ARCADE_ROUTES,
  buildArcadeNoticeHtml,
  buildScoreCsv,
  resolveArcadePathname,
} from "./page.js";

type JsonRecord = Record<string, unknown>;

function toText(value: unknown): string {
  return String(value ?? "").trim();
}

function asRecord(value: unknown): JsonRecord {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }
  return value as JsonRecord;
}

function buildEasterEggLine(score: number): string {
  if (score >= 1200) {
    return "你已经跑到网络边界了，再往前就是运营商的心跳。";
  }
  if (score >= 800) {
    return "小恐龙跑得越久，你的网速越差（bushi）。";
  }
  if (score >= 400) {
    return "据说分数破四百以后，Wi-Fi 会开始偷偷紧张。";
  }
  return "断网不可怕，可怕的是小恐龙比你先适应了离线生活。";
}

function buildFileResponse(
  mimeType: string,
  filePath: string,
  headers?: Record<string, string>
): ComposeWebViewResourceDecision {
  return {
    action: "respond" as const,
    response: {
      mimeType,
      encoding: "UTF-8",
      statusCode: 200,
      reasonPhrase: "OK",
      headers: {
        "Cache-Control": "no-store",
        ...(headers || {}),
      },
      filePath,
    },
  };
}

function buildTextResponse(
  mimeType: string,
  text: string,
  headers?: Record<string, string>,
  statusCode = 200,
  reasonPhrase = "OK"
): ComposeWebViewResourceDecision {
  return {
    action: "respond" as const,
    response: {
      mimeType,
      encoding: "UTF-8",
      statusCode,
      reasonPhrase,
      headers: {
        "Cache-Control": "no-store",
        ...(headers || {}),
      },
      text,
    },
  };
}

function buildBase64Response(
  mimeType: string,
  base64: string,
  headers?: Record<string, string>
): ComposeWebViewResourceDecision {
  return {
    action: "respond" as const,
    response: {
      mimeType,
      encoding: "UTF-8",
      statusCode: 200,
      reasonPhrase: "OK",
      headers: {
        "Cache-Control": "no-store",
        ...(headers || {}),
      },
      base64,
    },
  };
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const { UI } = ctx;
  const controller = ctx.createWebViewController("dino_runner_arcade_browser");

  const [initialized, setInitialized] = ctx.useState("initialized", false);
  const [resourcesReady, setResourcesReady] = ctx.useState("resourcesReady", false);
  const [resourceError, setResourceError] = ctx.useState("resourceError", "");
  const [appHtmlPath, setAppHtmlPath] = ctx.useState("appHtmlPath", "");
  const [themeCssPath, setThemeCssPath] = ctx.useState("themeCssPath", "");
  const [consoleJsPath, setConsoleJsPath] = ctx.useState("consoleJsPath", "");
  const [spritePngPath, setSpritePngPath] = ctx.useState("spritePngPath", "");
  const [overlayScore, setOverlayScore] = ctx.useState("overlayScore", 0);
  const easterEggLine = buildEasterEggLine(overlayScore);

  async function runGameScript<T = unknown>(script: string): Promise<T | null | undefined> {
    return controller.evaluateJavascript<T>(script);
  }

  async function handlePrimaryButton(): Promise<void> {
    const result = await runGameScript<JsonRecord>(
      "window.__arcadeConsole && window.__arcadeConsole.hostPrimaryAction ? window.__arcadeConsole.hostPrimaryAction() : null"
    );
    const state = asRecord(asRecord(result).state);
    const score = Number(state.score || 0);
    if (Number.isFinite(score)) {
      setOverlayScore(score);
    }
  }

  async function handleResetButton(): Promise<void> {
    const result = await runGameScript<JsonRecord>(
      "window.__arcadeConsole && window.__arcadeConsole.hostResetBoard ? window.__arcadeConsole.hostResetBoard() : null"
    );
    const state = asRecord(asRecord(result).state);
    const score = Number(state.score || 0);
    if (Number.isFinite(score)) {
      setOverlayScore(score);
    }
  }

  function registerJavascriptInterface(): void {
    controller.removeJavascriptInterface(ARCADE_HOST_INTERFACE_NAME);

    const arcadeHost: ComposeWebViewJavascriptInterface = {
      pageLog: (payload: unknown) => {
        return {
          received: true,
          payload,
          hostTime: new Date().toISOString(),
        };
      },

      updateOverlayScore: (payload: unknown) => {
        const record = asRecord(payload);
        const score = Number(record.score || 0);
        setOverlayScore(Number.isFinite(score) ? score : 0);
        return {
          applied: true,
          score: Number.isFinite(score) ? score : 0,
          syncedAt: new Date().toISOString(),
        };
      },
    };

    controller.addJavascriptInterface(ARCADE_HOST_INTERFACE_NAME, arcadeHost);
  }

  async function bootArcadeBrowser(): Promise<void> {
    if (initialized) {
      return;
    }
    setInitialized(true);

    const [appHtml, themeCss, consoleJs, spritePng] = await Promise.all([
      ToolPkg.readResource(
        ARCADE_RESOURCE_SPECS.appHtml.key,
        ARCADE_RESOURCE_SPECS.appHtml.outputName
      ),
      ToolPkg.readResource(
        ARCADE_RESOURCE_SPECS.themeCss.key,
        ARCADE_RESOURCE_SPECS.themeCss.outputName
      ),
      ToolPkg.readResource(
        ARCADE_RESOURCE_SPECS.consoleJs.key,
        ARCADE_RESOURCE_SPECS.consoleJs.outputName
      ),
      ToolPkg.readResource(
        ARCADE_RESOURCE_SPECS.spritePng.key,
        ARCADE_RESOURCE_SPECS.spritePng.outputName
      ),
    ]);

    const resolvedAppHtml = toText(appHtml);
    const resolvedThemeCss = toText(themeCss);
    const resolvedConsoleJs = toText(consoleJs);
    const resolvedSpritePng = toText(spritePng);

    if (
      !resolvedAppHtml ||
      !resolvedThemeCss ||
      !resolvedConsoleJs ||
      !resolvedSpritePng
    ) {
      setResourceError("小游戏网页资源没有完整装载成功。");
      return;
    }

    setAppHtmlPath(resolvedAppHtml);
    setThemeCssPath(resolvedThemeCss);
    setConsoleJsPath(resolvedConsoleJs);
    setSpritePngPath(resolvedSpritePng);
    registerJavascriptInterface();
    setResourcesReady(true);
  }

  function buildNotFoundResponse(url: string): ComposeWebViewResourceDecision {
    return buildTextResponse(
      "text/html",
      buildArcadeNoticeHtml(
        "这个关卡不存在",
        `当前地址没有命中资源表，所以由宿主直接合成了一张 404 页面: ${url}`
      ),
      undefined,
      404,
      "Page Not Found"
    );
  }

  function handleNavigation(
    request: ComposeWebViewNavigationRequest
  ): ComposeWebViewNavigationDecision {
    const url = toText(request?.url);

    if (url === ARCADE_ROUTES.legacyStage) {
      return {
        action: "rewrite" as const,
        url: `${ARCADE_ROUTES.stage1}?from=legacy`,
        headers: {
          "X-Arcade-Rewrite": "legacy-stage",
        },
      };
    }

    if (url === ARCADE_ROUTES.lockedDoor) {
      return { action: "cancel" as const };
    }

    if (url === ARCADE_ROUTES.externalHub || url.startsWith("mailto:")) {
      return {
        action: "external" as const,
        url,
      };
    }

    return { action: "allow" as const };
  }

  function handleResourceRequest(
    request: ComposeWebViewResourceRequest
  ): ComposeWebViewResourceDecision {
    const url = toText(request?.url);
    const pathname = resolveArcadePathname(url);

    if (!pathname) {
      return { action: "allow" as const };
    }

    if (pathname === "/assets/blocked-ads.js") {
      return { action: "block" as const };
    }

    if (pathname === "/assets/theme.css") {
      return buildFileResponse("text/css", themeCssPath);
    }

    if (pathname === "/assets/arcade_theme.css") {
      return buildFileResponse("text/css", themeCssPath);
    }

    if (pathname === "/assets/live-poster.jpg") {
      return {
        action: "rewrite" as const,
        url: "https://picsum.photos/seed/arcade-browser/320/180",
      };
    }

    if (pathname === "/assets/arcade_console.js") {
      return buildFileResponse("application/javascript", consoleJsPath);
    }

    if (pathname === "/assets/chrome_dino_sprite.png") {
      return buildFileResponse("image/png", spritePngPath);
    }

    if (pathname === "/assets/bonus-token.svg") {
      return buildBase64Response("image/svg+xml", ARCADE_BONUS_TOKEN_BASE64);
    }

    if (pathname === "/downloads/scores.csv") {
      return buildTextResponse("text/csv", buildScoreCsv(), {
        "Content-Disposition": 'attachment; filename="arcade-scores.csv"',
      });
    }

    if (
      pathname === "/" ||
      pathname === "/arcade/lobby" ||
      pathname === "/arcade/stage-1" ||
      pathname === "/arcade/boss-rush"
    ) {
      return buildFileResponse("text/html", appHtmlPath);
    }

    if (pathname === "/offline/gallery") {
      return buildTextResponse(
        "text/html",
        buildArcadeNoticeHtml(
          "离线画廊正在换展",
          "这个入口故意保留为宿主合成页，用来展示普通关卡与 respond(text) 可以同时共存。"
        )
      );
    }

    return buildNotFoundResponse(url);
  }

  return UI.Box(
    {
      fillMaxSize: true,
      backgroundColor: "#F7F7F7",
      onLoad: bootArcadeBrowser,
    },
    [
      resourcesReady
        ? UI.WebView({
            fillMaxSize: true,
            controller,
            key: "dino_runner_arcade_webview",
            url: ARCADE_ROUTES.lobby,
            nestedScrollInterop: true,
            javaScriptEnabled: true,
            domStorageEnabled: true,
            supportZoom: false,
            useWideViewPort: true,
            loadWithOverviewMode: true,
            onShouldOverrideUrlLoading: (
              request: ComposeWebViewNavigationRequest
            ) => handleNavigation(request),
            onInterceptRequest: (request: ComposeWebViewResourceRequest) =>
              handleResourceRequest(request),
          })
        : UI.Box(
            {
              fillMaxSize: true,
              contentAlignment: "center",
              padding: 24,
            },
            UI.Text({
              text: resourceError || "正在装载本地小游戏资源...",
              style: "titleMedium",
              color: "#202124",
            })
          ),
      resourcesReady
        ? UI.Column(
            {
              fillMaxSize: true,
              zIndex: 1,
              verticalArrangement: "end",
              horizontalAlignment: "center",
              padding: { horizontal: 12, vertical: 18 },
            },
            [
              UI.Box(
                {
                  fillMaxWidth: true,
                  contentAlignment: "center",
                  padding: { horizontal: 28, vertical: 12 },
                },
                UI.Text({
                  text: easterEggLine,
                  style: "bodySmall",
                  color: "#6B7280",
                })
              ),
              UI.Row(
              {
                spacing: 10,
                verticalAlignment: "center",
              },
              [
                UI.Surface(
                  {
                    onClick: handlePrimaryButton,
                    containerColor: "#5F6368EE",
                    contentColor: "#F7F7F7",
                    shape: { type: "pill" },
                    shadowElevation: 2,
                    padding: { horizontal: 22, vertical: 14 },
                  },
                  UI.Text({
                    text: "开始/暂停",
                    color: "#F7F7F7",
                    style: "labelLarge",
                  })
                ),
                UI.Surface(
                  {
                    onClick: handleResetButton,
                    containerColor: "#5F6368EE",
                    contentColor: "#F7F7F7",
                    shape: { type: "pill" },
                    shadowElevation: 2,
                    padding: { horizontal: 22, vertical: 14 },
                  },
                  UI.Text({
                    text: "重置",
                    color: "#F7F7F7",
                    style: "labelLarge",
                  })
                ),
                UI.Surface(
                  {
                    containerColor: "#FFFFFFF0",
                    contentColor: "#202124",
                    shape: { type: "pill" },
                    shadowElevation: 1,
                    padding: { horizontal: 18, vertical: 14 },
                  },
                  UI.Text({
                    text: `分数 ${overlayScore}`,
                    color: "#202124",
                    style: "labelLarge",
                  })
                ),
              ]
            ),
            ]
          )
        : UI.Spacer({ height: 0 }),
    ]
  );
}
