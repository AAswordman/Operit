import type { ThemeScript, PaintLayer } from "./oktheme";

// 编译为 CommonJS 后，在主题设置中导入生成的 minimal.js。
const theme: ThemeScript = {
    apiVersion: 1,
    render(context) {
        const border: PaintLayer = {
            type: "rect",
            strokeColor: context.colors.primary,
            strokeWidth: 1,
            radius: 16,
            opacity: context.mode === "dark" ? 0.7 : 0.45,
        };
        return {
            ...context.appearance,
            apiVersion: 1,
            surfaces: {
                ...context.appearance.surfaces,
                userMessage: { ...context.appearance.surfaces?.userMessage, padding: { start: 2, end: 2 }, layers: [border] },
                assistantMessage: { ...context.appearance.surfaces?.assistantMessage, layers: [border] },
                composer: { ...context.appearance.surfaces?.composer, layers: [border, { ...border, state: "focused", strokeWidth: 2 }] },
            },
        };
    },
};
export = theme;
