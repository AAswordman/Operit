"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const plan_mode_constants_js_1 = require("../../shared/plan_mode_constants.js");
const plan_mode_i18n_js_1 = require("../../shared/plan_mode_i18n.js");
const plan_mode_execution_js_1 = require("../../shared/plan_mode_execution.js");
const plan_mode_xml_js_1 = require("../../shared/plan_mode_xml.js");
const PLAN_PREVIEW_LINE_COUNT = 4;
const PLAN_MARKDOWN_FONT_SIZE = 12;
function useStateValue(ctx, key, initialValue) {
    const pair = ctx.useState(key, initialValue);
    return { value: pair[0], set: pair[1] };
}
function Screen(ctx) {
    const text = (0, plan_mode_i18n_js_1.resolvePlanModeI18n)();
    const [xmlContent] = ctx.useState("xmlContent", "");
    const submittingState = useStateValue(ctx, "submitting", false);
    const startedState = useStateValue(ctx, "started", false);
    const startedCheckedState = useStateValue(ctx, "startedChecked", false);
    const closedState = useStateValue(ctx, "closed", false);
    const errorState = useStateValue(ctx, "error", "");
    const expandedState = useStateValue(ctx, "expanded", false);
    const parsed = (0, plan_mode_xml_js_1.parsePlantodoXml)(xmlContent);
    const planContent = parsed.body.trim();
    const ready = parsed.closed || closedState.value;
    const lines = (0, plan_mode_xml_js_1.splitPlanBodyLines)(planContent);
    const canExpand = lines.length > PLAN_PREVIEW_LINE_COUNT;
    const shouldRenderFullMarkdown = expandedState.value || !canExpand;
    const visibleLines = canExpand && !expandedState.value ? lines.slice(0, PLAN_PREVIEW_LINE_COUNT) : lines;
    const previewMarkdown = visibleLines.join("\n");
    const rootKey = ready ? "plantodo-ready" : "plantodo-streaming";
    const handleStart = async () => {
        if (submittingState.value) {
            return;
        }
        errorState.set("");
        submittingState.set(true);
        const result = await (0, plan_mode_execution_js_1.startPlanImplementation)(planContent);
        submittingState.set(false);
        if (result.success) {
            startedState.set(true);
            return;
        }
        errorState.set(result.error ?? "");
    };
    const children = [
        ctx.UI.Card({
            fillMaxWidth: true,
            paddingHorizontal: 4,
            paddingVertical: 2,
            containerColor: ctx.MaterialTheme.colorScheme.surface.copy({ alpha: 0.95 }),
            shape: { cornerRadius: 8 },
            elevation: 2,
        }, [
            ctx.UI.Column({
                fillMaxWidth: true,
                padding: 16,
                spacing: 12,
            }, [
                ctx.UI.Row({ verticalAlignment: "center", spacing: 10 }, [
                    ctx.UI.Icon({ name: "assignment", tint: "primary", size: 22 }),
                    ctx.UI.Column({ spacing: 2, weight: 1 }, [
                        ctx.UI.Text({
                            text: text.rendererTitle,
                            style: "titleSmall",
                            fontWeight: "semibold",
                            color: ctx.MaterialTheme.colorScheme.onSurface,
                            fontSize: 13,
                        }),
                    ]),
                ]),
                lines.length
                    ? ctx.UI.Card({
                        fillMaxWidth: true,
                        containerColor: ctx.MaterialTheme.colorScheme.surfaceVariant.copy({
                            alpha: 0.22,
                        }),
                        shape: { cornerRadius: 8 },
                        elevation: 0,
                    }, shouldRenderFullMarkdown
                        ? [
                            ctx.UI.Column({
                                fillMaxWidth: true,
                            }, [
                                ctx.UI.Markdown({
                                    text: parsed.body,
                                    color: "onSurface",
                                    fontSize: PLAN_MARKDOWN_FONT_SIZE,
                                    fillMaxWidth: true,
                                    padding: { horizontal: 14, vertical: 10 },
                                    streamTagName: plan_mode_constants_js_1.XML_TAG,
                                }),
                                ...(canExpand
                                    ? [
                                        ctx.UI.Row({
                                            fillMaxWidth: true,
                                            onClick: () => {
                                                expandedState.set(false);
                                            },
                                            horizontalArrangement: "center",
                                            verticalAlignment: "center",
                                            spacing: 10,
                                            padding: { horizontal: 14, vertical: 12 },
                                        }, [
                                            ctx.UI.Icon({
                                                name: "expandLess",
                                                tint: "primary",
                                                size: 18,
                                            }),
                                            ctx.UI.Text({
                                                text: text.rendererCollapse,
                                                style: "labelLarge",
                                                color: "primary",
                                                fontWeight: "bold",
                                            }),
                                        ]),
                                    ]
                                    : []),
                            ]),
                        ]
                        : [
                            ctx.UI.Column({
                                fillMaxWidth: true,
                            }, [
                                ctx.UI.Markdown({
                                    text: previewMarkdown,
                                    color: "onSurface",
                                    fontSize: PLAN_MARKDOWN_FONT_SIZE,
                                    fillMaxWidth: true,
                                    padding: { horizontal: 14, vertical: 10 },
                                }),
                                ctx.UI.Row({
                                    fillMaxWidth: true,
                                    onClick: () => {
                                        expandedState.set(true);
                                    },
                                    horizontalArrangement: "center",
                                    verticalAlignment: "center",
                                    spacing: 10,
                                    padding: { horizontal: 14, vertical: 12 },
                                }, [
                                    ctx.UI.Icon({
                                        name: "expandMore",
                                        tint: "primary",
                                        size: 18,
                                    }),
                                    ctx.UI.Text({
                                        text: text.rendererExpand,
                                        style: "labelLarge",
                                        color: "primary",
                                        fontWeight: "bold",
                                    }),
                                ]),
                            ]),
                        ])
                    : ctx.UI.Text({
                        text: text.rendererEmpty,
                        style: "bodyMedium",
                        color: "onSurfaceVariant",
                    }),
                ...(ready && !startedState.value && lines.length
                    ? [
                        ctx.UI.Row({
                            fillMaxWidth: true,
                            horizontalArrangement: "end",
                        }, [
                            submittingState.value
                                ? ctx.UI.Button({
                                    enabled: false,
                                    onClick: handleStart,
                                    contentPadding: { horizontal: 12, vertical: 8 },
                                }, [
                                    ctx.UI.Row({
                                        verticalAlignment: "center",
                                        horizontalArrangement: "center",
                                        spacing: 8,
                                    }, [
                                        ctx.UI.CircularProgressIndicator({
                                            width: 14,
                                            height: 14,
                                            strokeWidth: 2,
                                            color: "onPrimary",
                                        }),
                                        ctx.UI.Text({ text: text.rendererButtonBusy }),
                                    ]),
                                ])
                                : ctx.UI.Button({
                                    text: text.rendererButtonIdle,
                                    onClick: handleStart,
                                    contentPadding: { horizontal: 12, vertical: 8 },
                                }),
                        ]),
                    ]
                    : []),
            ]),
        ]),
    ];
    if (errorState.value !== "") {
        children.push(ctx.UI.Card({
            fillMaxWidth: true,
            containerColor: "errorContainer",
            shape: { cornerRadius: 12 },
            elevation: 0,
        }, [
            ctx.UI.Row({
                padding: { horizontal: 14, vertical: 12 },
                spacing: 8,
                verticalAlignment: "center",
            }, [
                ctx.UI.Icon({ name: "error", tint: "onErrorContainer", size: 18 }),
                ctx.UI.Text({
                    text: errorState.value,
                    style: "bodyMedium",
                    color: "onErrorContainer",
                }),
            ]),
        ]));
    }
    return ctx.UI.Column({
        key: rootKey,
        fillMaxWidth: true,
        spacing: 12,
        onLoad: async () => {
            if (parsed.closed && !closedState.value) {
                closedState.set(true);
            }
            // This screen is re-created from scratch on every chat view rebuild, so the
            // handoff has to be re-read from the plugin runtime instead of assumed idle.
            if (!ready || !planContent || startedState.value || startedCheckedState.value) {
                return;
            }
            startedCheckedState.set(true);
            if (await (0, plan_mode_execution_js_1.isPlanImplementationStarted)(planContent)) {
                startedState.set(true);
            }
        },
    }, children);
}
