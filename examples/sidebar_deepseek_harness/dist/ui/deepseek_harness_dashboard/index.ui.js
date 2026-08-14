"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const deepseek_harness_web_runtime_js_1 = require("../../shared/deepseek_harness_web_runtime.js");
function clampProgress(value) {
    return Math.max(0, Math.min(100, Math.round(value)));
}
function Screen(ctx) {
    const { UI } = ctx;
    const colors = ctx.MaterialTheme.colorScheme;
    const [initialized, setInitialized] = ctx.useState("initialized", false);
    const [loading, setLoading] = ctx.useState("loading", false);
    const [serverUrl, setServerUrl] = ctx.useState("serverUrl", "");
    const [errorText, setErrorText] = ctx.useState("errorText", "");
    const [statusText, setStatusText] = ctx.useState("statusText", "Preparing DeepSeek Harness");
    const [progress, setProgress] = ctx.useState("progress", 0);
    const [reloadToken, setReloadToken] = ctx.useState("reloadToken", "0");
    async function startRuntime(forceRestart) {
        setLoading(true);
        setErrorText("");
        setProgress(5);
        try {
            const result = await (0, deepseek_harness_web_runtime_js_1.ensureDeepSeekHarnessWebServer)({
                forceRestart,
                onProgress: (event) => {
                    setStatusText(event.message);
                    setProgress(clampProgress(event.progress));
                },
            });
            if (!result.success) {
                setErrorText(result.diagnostic ?? result.message);
                setStatusText(result.message);
                return;
            }
            setServerUrl(result.url);
            setReloadToken(`${Date.now()}`);
            setProgress(90);
            setStatusText("Loading DeepSeek Harness");
        }
        catch (error) {
            console.error("DeepSeek Harness startup failed", error);
            setErrorText("DeepSeek Harness could not be started from the Linux runtime.");
            setStatusText("DeepSeek Harness startup failed");
        }
        finally {
            setLoading(false);
        }
    }
    const showOverlay = loading || !serverUrl || Boolean(errorText);
    const overlay = UI.Box({
        fillMaxSize: true,
        zIndex: 1,
        background: colors.surface,
        contentAlignment: "center",
    }, UI.Column({
        width: 280,
        spacing: 14,
        horizontalAlignment: "center",
    }, [
        UI.Icon({
            name: errorText ? "error" : "Code",
            size: 34,
            tint: errorText ? colors.error : colors.primary,
            spin: !errorText && loading,
            spinDurationMs: 850,
        }),
        UI.Text({
            text: errorText || statusText,
            style: "bodyMedium",
            color: errorText ? colors.error : colors.onSurfaceVariant,
            maxLines: 3,
            overflow: "ellipsis",
        }),
        !errorText
            ? UI.LinearProgressIndicator({
                width: 220,
                progress: clampProgress(progress) / 100,
            })
            : UI.Button({
                text: "Retry",
                onClick: () => startRuntime(true),
            }),
    ]));
    const webContent = serverUrl
        ? UI.Box({ fillMaxSize: true }, [
            UI.WebView({
                key: `deepseek_harness_webview_${reloadToken}`,
                fillMaxSize: true,
                url: serverUrl,
                javaScriptEnabled: true,
                domStorageEnabled: true,
                allowFileAccess: false,
                allowContentAccess: false,
                supportZoom: false,
                useWideViewPort: true,
                loadWithOverviewMode: true,
                safeBrowsingEnabled: true,
                onPageStarted: () => {
                    setProgress(92);
                    setStatusText("Loading DeepSeek Harness Web resources");
                },
                onProgressChanged: (event) => {
                    setProgress(Math.max(92, clampProgress(event.progress)));
                },
                onPageFinished: () => {
                    setProgress(100);
                    setStatusText("DeepSeek Harness is ready");
                },
                onReceivedError: () => {
                    setErrorText("DeepSeek Harness Web failed to load from the local runtime.");
                },
            }),
            showOverlay ? overlay : UI.Spacer({ height: 0 }),
        ])
        : overlay;
    return UI.Box({
        fillMaxSize: true,
        onLoad: async () => {
            if (!initialized) {
                setInitialized(true);
                await startRuntime(false);
            }
        },
    }, webContent);
}
