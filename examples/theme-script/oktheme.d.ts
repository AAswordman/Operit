/** Android 原生主题外观协议；apiVersion 仅用于机器校验。 */
export type SurfaceId = "background" | "userMessage" | "assistantMessage" | "composer" |
    "userAvatar" | "assistantAvatar" | "appBar" | "navigation" | "navigationItem" |
    "button" | "iconButton" | "input" | "settingsCard" | "dialog" | "sheet" | "menu" |
    "listItem" | "status" | "toolCard";
export type IconId = "composer.settings" | "composer.attachment" | "composer.microphone" |
    "composer.send" | "composer.stop" | "composer.fullscreen" | "composer.queue" |
    "composer.expand" | "appBar.back" | "appBar.menu" | "appBar.workspace" |
    "appBar.floating" | "navigation.item";
/** 六位是 RGB，八位是 ARGB。 */
export type Color = `#${string}`;
export type ColorToken = "primary" | "onPrimary" | "primaryContainer" | "onPrimaryContainer" |
    "inversePrimary" | "secondary" | "onSecondary" | "secondaryContainer" | "onSecondaryContainer" |
    "tertiary" | "onTertiary" | "tertiaryContainer" | "onTertiaryContainer" |
    "background" | "onBackground" | "surface" | "onSurface" | "surfaceVariant" | "onSurfaceVariant" |
    "surfaceTint" | "inverseSurface" | "inverseOnSurface" | "error" | "onError" | "errorContainer" |
    "onErrorContainer" | "outline" | "outlineVariant" | "scrim" | "surfaceBright" | "surfaceDim" |
    "surfaceContainerLowest" | "surfaceContainerLow" | "surfaceContainer" |
    "surfaceContainerHigh" | "surfaceContainerHighest";
export interface Palette {
    light?: Partial<Record<ColorToken, Color>>;
    dark?: Partial<Record<ColorToken, Color>>;
}
export interface Typography {
    family?: "default" | "sans-serif" | "serif" | "monospace" | "cursive" | `asset:${string}`;
    scale?: number;
    lineHeight?: number;
    letterSpacing?: number;
    /** 全局或区域文字颜色；null 表示沿用原有配色。 */
    color?: Color | null;
}
export interface Shape { type?: "rounded" | "cut" | "circle"; radius?: number; }
export interface IconAsset { asset: string; size?: number; opacity?: number; useContentTint?: boolean; tintColor?: Color; }
/** 位置为 -1..1，宽高为 (0,1]；平移保持尺寸，超出部件边缘的部分裁剪。 */
export interface Bounds { x?: number; y?: number; width?: number; height?: number; }
/** 内容留白使用 dp，九宫格切片使用原图像素。 */
export interface Insets { start?: number; top?: number; end?: number; bottom?: number; }
interface BaseLayer {
    id?: string;
    bounds?: Bounds;
    opacity?: number;
    placement?: "background" | "foreground";
    sampling?: "smooth" | "nearest";
    alignment?: "center" | "top" | "bottom" | "start" | "end";
    /** 0..8 dp；非零值要求 Android 12 或更新系统。 */
    blur?: number;
    state?: "always" | "pressed" | "focused" | "selected" | "disabled" | "error";
}
export interface PaintLayer extends BaseLayer {
    type: "rect" | "ellipse" | "line";
    color?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    radius?: number;
}
export interface ImageLayer extends BaseLayer {
    type: "image";
    asset: string;
    fit?: "fill" | "fit" | "crop" | "tile";
}
export interface NineSliceLayer extends BaseLayer {
    type: "nineSlice";
    asset: string;
    slices: Insets;
}
export type Layer = PaintLayer | ImageLayer | NineSliceLayer;
export interface Surface {
    padding?: Insets;
    layers?: readonly Layer[];
    opacity?: number;
    states?: Partial<Record<"pressed" | "focused" | "selected" | "disabled" | "error", SurfaceState>>;
    shape?: Shape;
    fillColor?: Color;
    contentColor?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    elevation?: number;
    minHeight?: number;
    /** 仅用于头像，单位 dp。 */
    size?: number;
    /** 仅用于消息，为现有最大宽度的比例。 */
    messageWidth?: number;
    textStyle?: Typography;
}
/** 空状态字段继承该部件的默认值，透明度与默认透明度相乘。 */
export interface SurfaceState {
    fillColor?: Color;
    contentColor?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    opacity?: number;
}
/** 只覆盖声明的部件；单个部件以完整局部外观替换公共声明。 */
export interface AppearanceMode {
    surfaces?: Partial<Record<SurfaceId, Surface>>;
    icons?: Partial<Record<IconId, IconAsset>>;
    typography?: Typography;
}
export interface Appearance {
    apiVersion: 1;
    surfaces?: Partial<Record<SurfaceId, Surface>>;
    icons?: Partial<Record<IconId, IconAsset>>;
    palette?: Palette;
    typography?: Typography;
    modes?: Partial<Record<"light" | "dark", AppearanceMode>>;
}
export interface ThemeContext {
    readonly apiVersion: 1;
    readonly mode: "light" | "dark";
    readonly colors: Readonly<Record<ColorToken, Color>>;
    readonly appearance: Readonly<Appearance>;
    readonly settings: {
        readonly strings: Readonly<Record<string, string>>;
        readonly booleans: Readonly<Record<string, boolean>>;
        readonly ints: Readonly<Record<string, number>>;
        readonly floats: Readonly<Record<string, number>>;
    };
}
export interface ThemeScript {
    apiVersion: 1;
    render(context: ThemeContext): Appearance;
}
/** 制作信息与相对 assets/ 空目录随完整主题包保留。 */
export interface StudioMetadata {
    id?: string;
    name?: string;
    author?: string;
    version?: string;
    description?: string;
    license?: string;
    folders?: readonly string[];
}
export interface ScriptManifest {
    apiVersion: 1;
    entry: string;
    assets?: Record<string, string>;
    appearance?: Appearance;
    studio?: StudioMetadata;
}
