"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const page_js_1 = require("./page.js");
function toText(value) {
    return String(value ?? "").trim();
}
function asRecord(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
        return {};
    }
    return value;
}
function buildEasterEggLine(score) {
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
function buildFileResponse(mimeType, filePath, headers) {
    return {
        action: "respond",
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
function buildTextResponse(mimeType, text, headers, statusCode = 200, reasonPhrase = "OK") {
    return {
        action: "respond",
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
function buildBase64Response(mimeType, base64, headers) {
    return {
        action: "respond",
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
function Screen(ctx) {
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
    async function runGameScript(script) {
        return controller.evaluateJavascript(script);
    }
    async function handlePrimaryButton() {
        const result = await runGameScript("window.__arcadeConsole && window.__arcadeConsole.hostPrimaryAction ? window.__arcadeConsole.hostPrimaryAction() : null");
        const state = asRecord(asRecord(result).state);
        const score = Number(state.score || 0);
        if (Number.isFinite(score)) {
            setOverlayScore(score);
        }
    }
    async function handleResetButton() {
        const result = await runGameScript("window.__arcadeConsole && window.__arcadeConsole.hostResetBoard ? window.__arcadeConsole.hostResetBoard() : null");
        const state = asRecord(asRecord(result).state);
        const score = Number(state.score || 0);
        if (Number.isFinite(score)) {
            setOverlayScore(score);
        }
    }
    function registerJavascriptInterface() {
        controller.removeJavascriptInterface(page_js_1.ARCADE_HOST_INTERFACE_NAME);
        const arcadeHost = {
            pageLog: (payload) => {
                return {
                    received: true,
                    payload,
                    hostTime: new Date().toISOString(),
                };
            },
            updateOverlayScore: (payload) => {
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
        controller.addJavascriptInterface(page_js_1.ARCADE_HOST_INTERFACE_NAME, arcadeHost);
    }
    async function bootArcadeBrowser() {
        if (initialized) {
            return;
        }
        setInitialized(true);
        const [appHtml, themeCss, consoleJs, spritePng] = await Promise.all([
            ToolPkg.readResource(page_js_1.ARCADE_RESOURCE_SPECS.appHtml.key, page_js_1.ARCADE_RESOURCE_SPECS.appHtml.outputName),
            ToolPkg.readResource(page_js_1.ARCADE_RESOURCE_SPECS.themeCss.key, page_js_1.ARCADE_RESOURCE_SPECS.themeCss.outputName),
            ToolPkg.readResource(page_js_1.ARCADE_RESOURCE_SPECS.consoleJs.key, page_js_1.ARCADE_RESOURCE_SPECS.consoleJs.outputName),
            ToolPkg.readResource(page_js_1.ARCADE_RESOURCE_SPECS.spritePng.key, page_js_1.ARCADE_RESOURCE_SPECS.spritePng.outputName),
        ]);
        const resolvedAppHtml = toText(appHtml);
        const resolvedThemeCss = toText(themeCss);
        const resolvedConsoleJs = toText(consoleJs);
        const resolvedSpritePng = toText(spritePng);
        if (!resolvedAppHtml ||
            !resolvedThemeCss ||
            !resolvedConsoleJs ||
            !resolvedSpritePng) {
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
    function buildNotFoundResponse(url) {
        return buildTextResponse("text/html", (0, page_js_1.buildArcadeNoticeHtml)("这个关卡不存在", `当前地址没有命中资源表，所以由宿主直接合成了一张 404 页面: ${url}`), undefined, 404, "Page Not Found");
    }
    function handleNavigation(request) {
        const url = toText(request?.url);
        if (url === page_js_1.ARCADE_ROUTES.legacyStage) {
            return {
                action: "rewrite",
                url: `${page_js_1.ARCADE_ROUTES.stage1}?from=legacy`,
                headers: {
                    "X-Arcade-Rewrite": "legacy-stage",
                },
            };
        }
        if (url === page_js_1.ARCADE_ROUTES.lockedDoor) {
            return { action: "cancel" };
        }
        if (url === page_js_1.ARCADE_ROUTES.externalHub || url.startsWith("mailto:")) {
            return {
                action: "external",
                url,
            };
        }
        return { action: "allow" };
    }
    function handleResourceRequest(request) {
        const url = toText(request?.url);
        const pathname = (0, page_js_1.resolveArcadePathname)(url);
        if (!pathname) {
            return { action: "allow" };
        }
        if (pathname === "/assets/blocked-ads.js") {
            return { action: "block" };
        }
        if (pathname === "/assets/theme.css") {
            return buildFileResponse("text/css", themeCssPath);
        }
        if (pathname === "/assets/arcade_theme.css") {
            return buildFileResponse("text/css", themeCssPath);
        }
        if (pathname === "/assets/live-poster.jpg") {
            return {
                action: "rewrite",
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
            return buildBase64Response("image/svg+xml", page_js_1.ARCADE_BONUS_TOKEN_BASE64);
        }
        if (pathname === "/downloads/scores.csv") {
            return buildTextResponse("text/csv", (0, page_js_1.buildScoreCsv)(), {
                "Content-Disposition": 'attachment; filename="arcade-scores.csv"',
            });
        }
        if (pathname === "/" ||
            pathname === "/arcade/lobby" ||
            pathname === "/arcade/stage-1" ||
            pathname === "/arcade/boss-rush") {
            return buildFileResponse("text/html", appHtmlPath);
        }
        if (pathname === "/offline/gallery") {
            return buildTextResponse("text/html", (0, page_js_1.buildArcadeNoticeHtml)("离线画廊正在换展", "这个入口故意保留为宿主合成页，用来展示普通关卡与 respond(text) 可以同时共存。"));
        }
        return buildNotFoundResponse(url);
    }
    return UI.Box({
        fillMaxSize: true,
        backgroundColor: "#F7F7F7",
        onLoad: bootArcadeBrowser,
    }, [
        resourcesReady
            ? UI.WebView({
                fillMaxSize: true,
                controller,
                key: "dino_runner_arcade_webview",
                url: page_js_1.ARCADE_ROUTES.lobby,
                nestedScrollInterop: true,
                javaScriptEnabled: true,
                domStorageEnabled: true,
                supportZoom: false,
                useWideViewPort: true,
                loadWithOverviewMode: true,
                onShouldOverrideUrlLoading: (request) => handleNavigation(request),
                onInterceptRequest: (request) => handleResourceRequest(request),
            })
            : UI.Box({
                fillMaxSize: true,
                contentAlignment: "center",
                padding: 24,
            }, UI.Text({
                text: resourceError || "正在装载本地小游戏资源...",
                style: "titleMedium",
                color: "#202124",
            })),
        resourcesReady
            ? UI.Column({
                fillMaxSize: true,
                zIndex: 1,
                verticalArrangement: "end",
                horizontalAlignment: "center",
                padding: { horizontal: 12, vertical: 18 },
            }, [
                UI.Box({
                    fillMaxWidth: true,
                    contentAlignment: "center",
                    padding: { horizontal: 28, vertical: 12 },
                }, UI.Text({
                    text: easterEggLine,
                    style: "bodySmall",
                    color: "#6B7280",
                })),
                UI.Row({
                    spacing: 10,
                    verticalAlignment: "center",
                }, [
                    UI.Surface({
                        onClick: handlePrimaryButton,
                        containerColor: "#5F6368EE",
                        contentColor: "#F7F7F7",
                        shape: { type: "pill" },
                        shadowElevation: 2,
                        padding: { horizontal: 22, vertical: 14 },
                    }, UI.Text({
                        text: "开始/暂停",
                        color: "#F7F7F7",
                        style: "labelLarge",
                    })),
                    UI.Surface({
                        onClick: handleResetButton,
                        containerColor: "#5F6368EE",
                        contentColor: "#F7F7F7",
                        shape: { type: "pill" },
                        shadowElevation: 2,
                        padding: { horizontal: 22, vertical: 14 },
                    }, UI.Text({
                        text: "重置",
                        color: "#F7F7F7",
                        style: "labelLarge",
                    })),
                    UI.Surface({
                        containerColor: "#FFFFFFF0",
                        contentColor: "#202124",
                        shape: { type: "pill" },
                        shadowElevation: 1,
                        padding: { horizontal: 18, vertical: 14 },
                    }, UI.Text({
                        text: `分数 ${overlayScore}`,
                        color: "#202124",
                        style: "labelLarge",
                    })),
                ]),
            ])
            : UI.Spacer({ height: 0 }),
    ]);
}
