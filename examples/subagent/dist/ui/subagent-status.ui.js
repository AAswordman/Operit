"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
function resolveTone(ctx, tone) {
    const scheme = ctx.MaterialTheme.colorScheme;
    switch (tone.trim().toLowerCase()) {
        case "start":
            return {
                container: scheme.secondaryContainer.copy({ alpha: 0.38 }),
                title: scheme.onSecondaryContainer,
                summary: scheme.onSecondaryContainer,
                detail: scheme.onSecondaryContainer.copy({ alpha: 0.78 }),
                badge: scheme.onSecondaryContainer.copy({ alpha: 0.74 }),
            };
        case "blocked":
            return {
                container: scheme.tertiaryContainer.copy({ alpha: 0.30 }),
                title: scheme.onTertiaryContainer,
                summary: scheme.onTertiaryContainer,
                detail: scheme.onTertiaryContainer.copy({ alpha: 0.82 }),
                badge: scheme.onTertiaryContainer.copy({ alpha: 0.76 }),
            };
        case "failure":
            return {
                container: scheme.errorContainer.copy({ alpha: 0.32 }),
                title: scheme.error,
                summary: scheme.error,
                detail: scheme.error.copy({ alpha: 0.82 }),
                badge: scheme.error.copy({ alpha: 0.76 }),
            };
        case "success":
            return {
                container: scheme.secondaryContainer.copy({ alpha: 0.30 }),
                title: scheme.onSecondaryContainer,
                summary: scheme.onSecondaryContainer,
                detail: scheme.onSecondaryContainer.copy({ alpha: 0.76 }),
                badge: scheme.onSecondaryContainer.copy({ alpha: 0.72 }),
            };
        case "progress":
        default:
            return {
                container: scheme.primaryContainer.copy({ alpha: 0.26 }),
                title: scheme.onPrimaryContainer,
                summary: scheme.onPrimaryContainer,
                detail: scheme.onPrimaryContainer.copy({ alpha: 0.76 }),
                badge: scheme.onPrimaryContainer.copy({ alpha: 0.72 }),
            };
    }
}
function buildHeader(ctx, colors, title, badge) {
    const children = [
        ctx.UI.Text({
            text: title,
            style: "labelLarge",
            fontWeight: "semibold",
            color: colors.title,
            weight: 1,
        }),
    ];
    if (badge) {
        children.push(ctx.UI.Text({
            text: badge,
            style: "labelSmall",
            fontWeight: "medium",
            color: colors.badge,
        }));
    }
    return ctx.UI.Row({
        fillMaxWidth: true,
        horizontalArrangement: "spaceBetween",
        verticalAlignment: "center",
    }, children);
}
function Screen(ctx) {
    const [title] = ctx.useState("title", "Subagent");
    const [summary] = ctx.useState("summary", "");
    const [detail] = ctx.useState("detail", "");
    const [badge] = ctx.useState("badge", "");
    const [tone] = ctx.useState("tone", "progress");
    const colors = resolveTone(ctx, tone);
    const bodyChildren = [
        buildHeader(ctx, colors, title, badge.trim()),
    ];
    if (summary.trim()) {
        bodyChildren.push(ctx.UI.Text({
            text: summary.trim(),
            style: "bodySmall",
            color: colors.summary,
            fontSize: 11,
            maxLines: 4,
        }));
    }
    if (detail.trim()) {
        bodyChildren.push(ctx.UI.Text({
            text: detail.trim(),
            style: "labelSmall",
            color: colors.detail,
            fontSize: 10,
            maxLines: 2,
        }));
    }
    return ctx.UI.Card({
        fillMaxWidth: true,
        paddingHorizontal: 4,
        paddingVertical: 2,
        containerColor: colors.container,
        elevation: tone.trim().toLowerCase() === "progress" ? 1 : 0,
        shape: { cornerRadius: 10 },
    }, ctx.UI.Column({
        padding: 10,
        spacing: 4,
    }, bodyChildren));
}
