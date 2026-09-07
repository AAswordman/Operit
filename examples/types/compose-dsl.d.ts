/**
 * Compose-like DSL type definitions for toolpkg runtime module `runtime="compose_dsl"`.
 */
import type { ComposeMaterial3GeneratedUiFactoryRegistry } from "./compose-dsl.material3.generated";

export type ComposeTextStyle =
  | "headlineSmall"
  | "headlineMedium"
  | "titleLarge"
  | "titleMedium"
  | "titleSmall"
  | "bodyLarge"
  | "bodyMedium"
  | "bodySmall"
  | "labelLarge"
  | "labelMedium"
  | "labelSmall";

export type ComposeArrangement =
  | "start"
  | "center"
  | "end"
  | "spaceBetween"
  | "spaceAround"
  | "spaceEvenly";

export type ComposeAlignment = "start" | "center" | "end";

export type ComposeShapeType =
  | "rounded"
  | "cut"
  | "circle"
  | "pill";

export interface ComposeShape {
  type?: ComposeShapeType;
  cornerRadius?: number;
  radius?: number;
  topStart?: number;
  topEnd?: number;
  bottomStart?: number;
  bottomEnd?: number;
  topLeft?: number;
  topRight?: number;
  bottomLeft?: number;
  bottomRight?: number;
}

export interface ComposeBorder {
  width?: number;
  color?: ComposeColor;
  alpha?: number;
}

export interface ComposePadding {
  horizontal?: number;
  vertical?: number;
}

export type ComposeCanvasUnit = "px" | "dp" | "fraction";

export interface ComposeUnitValue {
  value: number;
  unit: ComposeCanvasUnit;
}

export type ComposeCanvasNumber = number | ComposeUnitValue;

export type ComposeTextOverflow = "clip" | "ellipsis";

export type ComposeContentScale =
  | "fit"
  | "crop"
  | "fillBounds"
  | "fillWidth"
  | "fillHeight"
  | "inside"
  | "none";

export interface ComposeTextMeasureRequest {
  text: string;
  fontSize?: number;
  maxWidth: number;
  maxHeight?: number;
  minWidth?: number;
  minHeight?: number;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
}

export interface ComposeTextMeasureResult {
  width: number;
  height: number;
}

export interface ComposeColorToken {
  __colorToken: string;
  alpha?: number;
  copy(options: { alpha: number }): ComposeColorToken;
}

export type ComposeColor = string | ComposeColorToken;

export interface ComposeColorScheme {
  [key: string]: ComposeColorToken;
}

export interface ComposeMaterialTheme {
  colorScheme: ComposeColorScheme;
}

export interface ComposeCanvasBrush {
  type: "verticalGradient";
  colors: ComposeColor[];
}

export type ComposeHorizontalAlignment =
  | "start"
  | "center"
  | "end"
  | "left"
  | "right"
  | "centerHorizontally";

export type ComposeVerticalAlignment =
  | "top"
  | "center"
  | "bottom"
  | "start"
  | "end"
  | "centerVertically";

export type ComposeBoxAlignment =
  | "center"
  | "topStart"
  | "startTop"
  | "topCenter"
  | "centerTop"
  | "topEnd"
  | "endTop"
  | "centerStart"
  | "startCenter"
  | "centerEnd"
  | "endCenter"
  | "bottomStart"
  | "startBottom"
  | "bottomCenter"
  | "centerBottom"
  | "bottomEnd"
  | "endBottom";

export type ComposeModifierAlign =
  | ComposeHorizontalAlignment
  | ComposeVerticalAlignment
  | ComposeBoxAlignment;

export interface ComposeModifierPadding {
  all?: number;
  horizontal?: number;
  vertical?: number;
  start?: number;
  top?: number;
  end?: number;
  bottom?: number;
}

export interface ComposeModifierOffset {
  x?: number;
  y?: number;
}

export interface ComposeModifierAxisBounds {
  min?: number;
  max?: number;
}

export interface ComposeModifierWidthBounds extends ComposeModifierAxisBounds {
  minWidth?: number;
  maxWidth?: number;
}

export interface ComposeModifierHeightBounds extends ComposeModifierAxisBounds {
  minHeight?: number;
  maxHeight?: number;
}

export interface ComposeModifierSizeBounds {
  min?: number;
  max?: number;
  minWidth?: number;
  minHeight?: number;
  maxWidth?: number;
  maxHeight?: number;
}

export interface ComposeModifierDefaultMinSize {
  all?: number;
  minWidth?: number;
  minHeight?: number;
}

export interface ComposeModifierWrapContentWidthOptions {
  align?: ComposeHorizontalAlignment;
  unbounded?: boolean;
}

export interface ComposeModifierWrapContentHeightOptions {
  align?: ComposeVerticalAlignment;
  unbounded?: boolean;
}

export interface ComposeModifierWrapContentSizeOptions {
  align?: ComposeBoxAlignment;
  unbounded?: boolean;
}

export interface ComposeModifierShadowOptions {
  elevation: number;
  shape?: ComposeShape;
  clip?: boolean;
}

export interface ComposeModifierCombinedClickableOptions {
  onClick: () => void | Promise<void>;
  onLongClick?: () => void | Promise<void>;
  onDoubleClick?: () => void | Promise<void>;
}

export interface ComposePointerOffsetEvent {
  x: number;
  y: number;
}

export interface ComposeDragGestureEvent extends ComposePointerOffsetEvent {
  deltaX: number;
  deltaY: number;
}

export interface ComposeSizeChangedEvent {
  width: number;
  height: number;
}

export interface ComposeGloballyPositionedEvent {
  rootX: number;
  rootY: number;
  width: number;
  height: number;
  windowX: number;
  windowY: number;
}

export interface ComposeModifierTapGesturesOptions {
  onPress?: (event: ComposePointerOffsetEvent) => void | Promise<void>;
  onTap?: (event: ComposePointerOffsetEvent) => void | Promise<void>;
  onDoubleTap?: (event: ComposePointerOffsetEvent) => void | Promise<void>;
  onLongPress?: (event: ComposePointerOffsetEvent) => void | Promise<void>;
}

export interface ComposeModifierDragGesturesOptions {
  onDragStart?: (event: ComposePointerOffsetEvent) => void | Promise<void>;
  onDrag?: (event: ComposeDragGestureEvent) => void | Promise<void>;
  onDragEnd?: () => void | Promise<void>;
  onDragCancel?: () => void | Promise<void>;
}

export interface ComposeModifierTransformGesturesOptions {
  panZoomLock?: boolean;
  onGesture: (event: ComposeCanvasTransformEvent) => void | Promise<void>;
}

export interface ComposeCanvasTransform {
  scale?: number;
  offsetX?: number;
  offsetY?: number;
  pivotX?: number;
  pivotY?: number;
}

export interface ComposeCanvasTransformEvent {
  centroidX: number;
  centroidY: number;
  panX: number;
  panY: number;
  zoom: number;
  rotation: number;
}

export interface ComposeCanvasSizeEvent extends ComposeSizeChangedEvent {}

export interface ComposeWebViewPageEvent {
  url?: string | null;
  title?: string | null;
  canGoBack?: boolean;
  canGoForward?: boolean;
}

export interface ComposeWebViewNavigationEvent {
  url?: string | null;
  isMainFrame?: boolean;
  method?: string | null;
}

export interface ComposeWebViewProgressEvent {
  progress: number;
  url?: string | null;
  title?: string | null;
}

export interface ComposeWebViewErrorEvent {
  errorCode: number;
  description?: string | null;
  url?: string | null;
}

export interface ComposeWebViewHttpErrorEvent {
  statusCode?: number | null;
  reasonPhrase?: string | null;
  url?: string | null;
  isMainFrame?: boolean;
}

export interface ComposeWebViewSslErrorEvent {
  primaryError?: number | null;
  url?: string | null;
}

export interface ComposeWebViewDownloadEvent {
  url: string;
  userAgent?: string | null;
  contentDisposition?: string | null;
  mimeType?: string | null;
  contentLength?: number | null;
  suggestedFileName?: string | null;
}

export interface ComposeWebViewConsoleEvent {
  message?: string | null;
  sourceId?: string | null;
  lineNumber?: number | null;
  level?: string | null;
}

export interface ComposeWebViewState {
  url?: string | null;
  title?: string | null;
  loading: boolean;
  progress: number;
  canGoBack: boolean;
  canGoForward: boolean;
}

export interface ComposeWebViewLifecycleEvent {
  type: "created" | "disposed" | "pageCommitVisible" | "renderProcessGone";
  url?: string | null;
  title?: string | null;
  loading?: boolean;
  progress?: number;
  canGoBack?: boolean;
  canGoForward?: boolean;
  didCrash?: boolean;
  rendererPriorityAtExit?: number | null;
}

export interface ComposeWebViewNavigationRequest {
  url: string;
  method?: string | null;
  headers?: Record<string, string>;
  isMainFrame?: boolean;
  hasGesture?: boolean;
  isRedirect?: boolean;
  scheme?: string | null;
}

export type ComposeWebViewNavigationDecision =
  | { action: "allow" }
  | { action: "cancel" }
  | {
      action: "rewrite";
      url: string;
      headers?: Record<string, string>;
    }
  | {
      action: "external";
      url?: string;
    };

export interface ComposeWebViewResourceRequest {
  url: string;
  method?: string | null;
  headers?: Record<string, string>;
  isMainFrame?: boolean;
  hasGesture?: boolean;
  isRedirect?: boolean;
  scheme?: string | null;
}

export type ComposeWebViewResourceResponse =
  | {
      mimeType?: string;
      encoding?: string;
      statusCode?: number;
      reasonPhrase?: string;
      headers?: Record<string, string>;
      text: string;
      base64?: never;
      filePath?: never;
    }
  | {
      mimeType?: string;
      encoding?: string;
      statusCode?: number;
      reasonPhrase?: string;
      headers?: Record<string, string>;
      base64: string;
      text?: never;
      filePath?: never;
    }
  | {
      mimeType?: string;
      encoding?: string;
      statusCode?: number;
      reasonPhrase?: string;
      headers?: Record<string, string>;
      filePath: string;
      text?: never;
      base64?: never;
    };

export type ComposeWebViewResourceDecision =
  | { action: "allow" }
  | { action: "block" }
  | {
      action: "rewrite";
      url: string;
      headers?: Record<string, string>;
    }
  | {
      action: "respond";
      response: ComposeWebViewResourceResponse;
    };

export type ComposeWebViewJavascriptInterfaceMethod = (
  ...args: unknown[]
) => unknown | Promise<unknown>;

export type ComposeWebViewJavascriptInterface = Record<
  string,
  ComposeWebViewJavascriptInterfaceMethod
>;

export interface ComposeWebViewLoadHtmlOptions {
  baseUrl?: string;
  mimeType?: string;
  encoding?: string;
}

export interface ComposeWebViewController {
  readonly key: string;
  loadUrl(url: string, headers?: Record<string, string>): void;
  loadHtml(html: string, options?: ComposeWebViewLoadHtmlOptions): void;
  reload(): void;
  stopLoading(): void;
  goBack(): void;
  goForward(): void;
  clearHistory(): void;
  evaluateJavascript<TResult = unknown>(script: string): Promise<TResult | null | undefined>;
  getState(): ComposeWebViewState | null | undefined;
  addJavascriptInterface(
    name: string,
    object: ComposeWebViewJavascriptInterface
  ): void;
  removeJavascriptInterface(name: string): void;
}

export type ComposeWebViewMixedContentMode =
  | "alwaysAllow"
  | "neverAllow"
  | "compatibilityMode";

export type ComposeWebViewCacheMode =
  | "default"
  | "noCache"
  | "cacheElseNetwork"
  | "cacheOnly";

declare global {
  interface Number {
    readonly px: ComposeUnitValue;
    readonly dp: ComposeUnitValue;
    readonly fraction: ComposeUnitValue;
  }
}

export interface ComposeCanvasLineCommand {
  type: "line";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasRectCommand {
  type: "rect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasRoundRectCommand {
  type: "roundRect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  radius?: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasCircleCommand {
  type: "circle";
  cx: ComposeCanvasNumber;
  cy: ComposeCanvasNumber;
  radius: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  filled?: boolean;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasTextCommand {
  type: "text";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  text: string;
  color?: ComposeColor;
  alpha?: number;
  fontSize?: ComposeCanvasNumber;
  minWidth?: ComposeCanvasNumber;
  maxWidth?: ComposeCanvasNumber;
  minHeight?: ComposeCanvasNumber;
  maxHeight?: ComposeCanvasNumber;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
  unit?: ComposeCanvasUnit;
}

// Path operations for drawPath command
export interface ComposeCanvasMoveToOp {
  type: "moveTo";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
}

export interface ComposeCanvasLineToOp {
  type: "lineTo";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
}

export interface ComposeCanvasCubicToOp {
  type: "cubicTo";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
  x3: ComposeCanvasNumber;
  y3: ComposeCanvasNumber;
}

export interface ComposeCanvasQuadToOp {
  type: "quadTo";
  x1: ComposeCanvasNumber;
  y1: ComposeCanvasNumber;
  x2: ComposeCanvasNumber;
  y2: ComposeCanvasNumber;
}

export interface ComposeCanvasCloseOp {
  type: "close";
}

export type ComposeCanvasPathOp =
  | ComposeCanvasMoveToOp
  | ComposeCanvasLineToOp
  | ComposeCanvasCubicToOp
  | ComposeCanvasQuadToOp
  | ComposeCanvasCloseOp;

export type ComposeCanvasDrawStyle = "fill" | "stroke";

// Enhanced Canvas commands
export interface ComposeCanvasDrawPathCommand {
  type: "drawPath";
  path: ComposeCanvasPathOp[];
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  style?: ComposeCanvasDrawStyle;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawRoundRectCommand {
  type: "drawRoundRect";
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  width: ComposeCanvasNumber;
  height: ComposeCanvasNumber;
  cornerRadius?: ComposeCanvasNumber;
  brush?: ComposeCanvasBrush;
  color?: ComposeColor;
  alpha?: number;
  strokeWidth?: ComposeCanvasNumber;
  style?: ComposeCanvasDrawStyle;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawTextCommand {
  type: "drawText";
  text: string;
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  fontSize?: ComposeCanvasNumber;
  fontWeight?: string;
  minWidth?: ComposeCanvasNumber;
  maxWidth?: ComposeCanvasNumber;
  minHeight?: ComposeCanvasNumber;
  maxHeight?: ComposeCanvasNumber;
  maxLines?: number;
  overflow?: ComposeTextOverflow;
  unit?: ComposeCanvasUnit;
}

export interface ComposeCanvasDrawIconCommand {
  type: "drawIcon";
  icon: string;
  x: ComposeCanvasNumber;
  y: ComposeCanvasNumber;
  size?: ComposeCanvasNumber;
  color?: ComposeColor;
  alpha?: number;
  unit?: ComposeCanvasUnit;
}

export type ComposeCanvasCommand =
  | ComposeCanvasLineCommand
  | ComposeCanvasRectCommand
  | ComposeCanvasRoundRectCommand
  | ComposeCanvasCircleCommand
  | ComposeCanvasTextCommand
  | ComposeCanvasDrawPathCommand
  | ComposeCanvasDrawRoundRectCommand
  | ComposeCanvasDrawTextCommand
  | ComposeCanvasDrawIconCommand;

export type ComposeModifierName =
  | "fillMaxSize"
  | "fillMaxWidth"
  | "fillMaxHeight"
  | "width"
  | "height"
  | "requiredWidth"
  | "requiredHeight"
  | "size"
  | "requiredSize"
  | "padding"
  | "offset"
  | "widthIn"
  | "heightIn"
  | "sizeIn"
  | "requiredWidthIn"
  | "requiredHeightIn"
  | "requiredSizeIn"
  | "defaultMinSize"
  | "wrapContentWidth"
  | "wrapContentHeight"
  | "wrapContentSize"
  | "aspectRatio"
  | "alpha"
  | "rotate"
  | "scale"
  | "zIndex"
  | "background"
  | "border"
  | "clip"
  | "clipToBounds"
  | "shadow"
  | "clickable"
  | "combinedClickable"
  | "tapGestures"
  | "dragGestures"
  | "transformGestures"
  | "onSizeChanged"
  | "onGloballyPositioned"
  | "imePadding"
  | "statusBarsPadding"
  | "navigationBarsPadding"
  | "systemBarsPadding"
  | "safeDrawingPadding"
  | "weight"
  | "align"
  | "matchParentSize";

export interface ComposeModifierOp {
  name: ComposeModifierName;
  args?: unknown[];
}

export interface ComposeModifierValue {
  __modifierOps: ComposeModifierOp[];
}

export interface ComposeModifierProxy extends ComposeModifierValue {
  fillMaxSize(fraction?: number): ComposeModifierProxy;
  fillMaxWidth(fraction?: number): ComposeModifierProxy;
  fillMaxHeight(fraction?: number): ComposeModifierProxy;
  width(value: number): ComposeModifierProxy;
  height(value: number): ComposeModifierProxy;
  requiredWidth(value: number): ComposeModifierProxy;
  requiredHeight(value: number): ComposeModifierProxy;
  size(value: number): ComposeModifierProxy;
  size(width: number, height: number): ComposeModifierProxy;
  requiredSize(value: number): ComposeModifierProxy;
  requiredSize(width: number, height: number): ComposeModifierProxy;
  padding(value: number): ComposeModifierProxy;
  padding(horizontal: number, vertical: number): ComposeModifierProxy;
  padding(start: number, top: number, end: number, bottom: number): ComposeModifierProxy;
  padding(values: ComposeModifierPadding): ComposeModifierProxy;
  offset(x: number, y?: number): ComposeModifierProxy;
  offset(values: ComposeModifierOffset): ComposeModifierProxy;
  widthIn(min?: number, max?: number): ComposeModifierProxy;
  widthIn(bounds: ComposeModifierWidthBounds): ComposeModifierProxy;
  heightIn(min?: number, max?: number): ComposeModifierProxy;
  heightIn(bounds: ComposeModifierHeightBounds): ComposeModifierProxy;
  sizeIn(minWidth: number, minHeight: number, maxWidth: number, maxHeight: number): ComposeModifierProxy;
  sizeIn(bounds: ComposeModifierSizeBounds): ComposeModifierProxy;
  requiredWidthIn(min?: number, max?: number): ComposeModifierProxy;
  requiredWidthIn(bounds: ComposeModifierWidthBounds): ComposeModifierProxy;
  requiredHeightIn(min?: number, max?: number): ComposeModifierProxy;
  requiredHeightIn(bounds: ComposeModifierHeightBounds): ComposeModifierProxy;
  requiredSizeIn(minWidth: number, minHeight: number, maxWidth: number, maxHeight: number): ComposeModifierProxy;
  requiredSizeIn(bounds: ComposeModifierSizeBounds): ComposeModifierProxy;
  defaultMinSize(minWidth: number, minHeight?: number): ComposeModifierProxy;
  defaultMinSize(values: ComposeModifierDefaultMinSize): ComposeModifierProxy;
  wrapContentWidth(): ComposeModifierProxy;
  wrapContentWidth(align: ComposeHorizontalAlignment, unbounded?: boolean): ComposeModifierProxy;
  wrapContentWidth(options: ComposeModifierWrapContentWidthOptions): ComposeModifierProxy;
  wrapContentHeight(): ComposeModifierProxy;
  wrapContentHeight(align: ComposeVerticalAlignment, unbounded?: boolean): ComposeModifierProxy;
  wrapContentHeight(options: ComposeModifierWrapContentHeightOptions): ComposeModifierProxy;
  wrapContentSize(): ComposeModifierProxy;
  wrapContentSize(align: ComposeBoxAlignment, unbounded?: boolean): ComposeModifierProxy;
  wrapContentSize(options: ComposeModifierWrapContentSizeOptions): ComposeModifierProxy;
  aspectRatio(ratio: number): ComposeModifierProxy;
  alpha(value: number): ComposeModifierProxy;
  rotate(value: number): ComposeModifierProxy;
  scale(value: number): ComposeModifierProxy;
  zIndex(value: number): ComposeModifierProxy;
  background(value: ComposeColor, shape?: ComposeShape): ComposeModifierProxy;
  background(value: ComposeCanvasBrush, shape?: ComposeShape): ComposeModifierProxy;
  border(width: number, value: ComposeColor, shape?: ComposeShape): ComposeModifierProxy;
  border(width: number, value: ComposeCanvasBrush, shape?: ComposeShape): ComposeModifierProxy;
  clip(shape: ComposeShape): ComposeModifierProxy;
  clipToBounds(): ComposeModifierProxy;
  shadow(elevation: number, shape?: ComposeShape, clip?: boolean): ComposeModifierProxy;
  shadow(options: ComposeModifierShadowOptions): ComposeModifierProxy;
  clickable(onClick: () => void | Promise<void>): ComposeModifierProxy;
  combinedClickable(options: ComposeModifierCombinedClickableOptions): ComposeModifierProxy;
  tapGestures(options: ComposeModifierTapGesturesOptions): ComposeModifierProxy;
  dragGestures(options: ComposeModifierDragGesturesOptions): ComposeModifierProxy;
  transformGestures(options: ComposeModifierTransformGesturesOptions): ComposeModifierProxy;
  onSizeChanged(onSizeChanged: (event: ComposeSizeChangedEvent) => void | Promise<void>): ComposeModifierProxy;
  onGloballyPositioned(
    onGloballyPositioned: (event: ComposeGloballyPositionedEvent) => void | Promise<void>
  ): ComposeModifierProxy;
  imePadding(): ComposeModifierProxy;
  statusBarsPadding(): ComposeModifierProxy;
  navigationBarsPadding(): ComposeModifierProxy;
  systemBarsPadding(): ComposeModifierProxy;
  safeDrawingPadding(): ComposeModifierProxy;
  weight(weight: number, fill?: boolean): ComposeModifierProxy;
  align(alignment: ComposeModifierAlign): ComposeModifierProxy;
  matchParentSize(): ComposeModifierProxy;
  toJSON(): ComposeModifierValue;
}

export interface ComposeTextFieldStyle {
  fontSize?: number;
  fontWeight?: string;
  fontFamily?: string;
  color?: ComposeColor;
}

export interface ComposeCommonProps {
  key?: string;
  onLoad?: () => void | Promise<void>;
  topBarTitle?: ComposeChildren;
  modifier?: ComposeModifierValue;
  zIndex?: number;
  weight?: number;
  weightFill?: boolean;
  width?: number;
  height?: number;
  fillMaxHeight?: boolean;
  padding?: number | ComposePadding;
  paddingStart?: number;
  paddingTop?: number;
  paddingEnd?: number;
  paddingHorizontal?: number;
  paddingVertical?: number;
  paddingBottom?: number;
  spacing?: number;
  fillMaxWidth?: boolean;
  fillMaxSize?: boolean;
  background?: ComposeColor;
  backgroundColor?: ComposeColor;
  containerColor?: ComposeColor;
  backgroundAlpha?: number;
  backgroundBrush?: ComposeCanvasBrush;
  backgroundShape?: ComposeShape;
}

export interface ColumnProps extends ComposeCommonProps {
  content?: ComposeChildren;
  horizontalAlignment?: ComposeAlignment;
  verticalArrangement?: ComposeArrangement;
}

export interface RowProps extends ComposeCommonProps {
  content?: ComposeChildren;
  horizontalArrangement?: ComposeArrangement;
  verticalAlignment?: ComposeAlignment;
  onClick?: () => void | Promise<void>;
}

export interface BoxProps extends ComposeCommonProps {
  content?: ComposeChildren;
  contentAlignment?: ComposeAlignment;
}

export interface SpacerProps {
  width?: number;
  height?: number;
}

export interface TextProps extends ComposeCommonProps {
  text: string;
  style?: ComposeTextStyle;
  color?: ComposeColor;
  fontWeight?: string;
  fontSize?: number;
  fontFamily?: string;
  maxLines?: number;
  softWrap?: boolean;
  overflow?: ComposeTextOverflow;
  weight?: number;
}

export interface MarkdownProps extends ComposeCommonProps {
  text: string;
  color?: ComposeColor;
  fontSize?: number;
  enableDialogs?: boolean;
  streamTagName?: string;
}

export interface TextFieldProps extends ComposeCommonProps {
  label?: string | ComposeChildren;
  placeholder?: string | ComposeChildren;
  leadingIcon?: ComposeChildren;
  trailingIcon?: ComposeChildren;
  prefix?: ComposeChildren;
  suffix?: ComposeChildren;
  supportingText?: ComposeChildren;
  value: string;
  onValueChange: (value: string) => void;
  singleLine?: boolean;
  minLines?: number;
  maxLines?: number;
  readOnly?: boolean;
  isError?: boolean;
  isPassword?: boolean;
  style?: ComposeTextFieldStyle;
}

export interface SwitchProps extends ComposeCommonProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  enabled?: boolean;
  thumbContent?: ComposeChildren;
  checkedThumbColor?: ComposeColor;
  checkedTrackColor?: ComposeColor;
  uncheckedThumbColor?: ComposeColor;
  uncheckedTrackColor?: ComposeColor;
}

export interface CheckboxProps extends ComposeCommonProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  enabled?: boolean;
}

export interface ButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  text?: string;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  contentPadding?: ComposePadding;
  shape?: ComposeShape;
}

export interface IconButtonProps extends ComposeCommonProps {
  content?: ComposeChildren;
  icon?: string;
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  shape?: ComposeShape;
}

export interface CardProps extends ComposeCommonProps {
  content?: ComposeChildren;
  containerColor?: ComposeColor;
  containerAlpha?: number;
  contentColor?: ComposeColor;
  contentAlpha?: number;
  shape?: ComposeShape;
  border?: ComposeBorder;
  elevation?: number;
}

export interface SurfaceProps extends ComposeCommonProps {
  content?: ComposeChildren;
  containerColor?: ComposeColor;
  contentColor?: ComposeColor;
  shape?: ComposeShape;
  alpha?: number;
  onClick?: () => void | Promise<void>;
}

export interface IconProps extends ComposeCommonProps {
  name?: string;
  tint?: ComposeColor;
  size?: number;
  spin?: boolean;
  spinDurationMs?: number;
}

export interface LazyColumnProps extends ComposeCommonProps {
  content?: ComposeChildren;
  spacing?: number;
}

export interface LinearProgressIndicatorProps extends ComposeCommonProps {
  progress?: number;
}

export interface CircularProgressIndicatorProps extends ComposeCommonProps {
  strokeWidth?: number;
  color?: ComposeColor;
}

export interface SnackbarHostProps extends ComposeCommonProps {}

/** @since ToolPkg API 1.0.1 */
export interface DialogProperties {
  dismissOnBackPress?: boolean;
  dismissOnClickOutside?: boolean;
  usePlatformDefaultWidth?: boolean;
  decorFitsSystemWindows?: boolean;
}

/** @since ToolPkg API 1.0.1 */
export interface AlertDialogProps extends ComposeCommonProps {
  title?: string | ComposeChildren;
  text?: string | ComposeChildren;
  markdown?: string;
  content?: ComposeChildren;
  icon?: ComposeChildren;
  confirmButton?: ComposeChildren;
  dismissButton?: ComposeChildren;
  confirmText?: string;
  dismissText?: string;
  onConfirm?: () => void | Promise<void>;
  onDismiss?: () => void | Promise<void>;
  onDismissRequest?: () => void | Promise<void>;
  closeOnConfirm?: boolean;
  closeOnDismiss?: boolean;
  closeOnDismissRequest?: boolean;
  containerColor?: ComposeColor;
  iconContentColor?: ComposeColor;
  titleContentColor?: ComposeColor;
  textContentColor?: ComposeColor;
  tonalElevation?: number;
  shape?: ComposeShape;
  properties?: DialogProperties;
}

/** @since ToolPkg API 1.0.1 */
export interface DialogProps extends ComposeCommonProps {
  content?: ComposeChildren;
  onDismissRequest?: () => void | Promise<void>;
  closeOnDismissRequest?: boolean;
  containerColor?: ComposeColor;
  contentColor?: ComposeColor;
  tonalElevation?: number;
  shape?: ComposeShape;
  properties?: DialogProperties;
}

/** Embeds the host AI chat surface without its workspace panel. */
export interface AiChatProps extends ComposeCommonProps {}

/** Controls a responsive trailing panel around the supplied screen content. */
export interface AdaptiveSidePanelProps extends ComposeCommonProps {
  open: boolean;
  side: ComposeChildren;
  onOpenChanged: (open: boolean) => void;
  defaultWidth?: number;
  minWidth?: number;
  minContentWidth?: number;
  breakpoint?: number;
}

export interface CanvasProps extends ComposeCommonProps {
  commands?: ComposeCanvasCommand[];
  transform?: ComposeCanvasTransform;
  onTransform?: (event: ComposeCanvasTransformEvent) => void;
  onSizeChanged?: (event: ComposeCanvasSizeEvent) => void;
}

export interface WebViewProps extends ComposeCommonProps {
  url?: string;
  html?: string;
  baseUrl?: string;
  mimeType?: string;
  encoding?: string;
  headers?: Record<string, string>;
  javaScriptEnabled?: boolean;
  domStorageEnabled?: boolean;
  databaseEnabled?: boolean;
  javaScriptCanOpenWindowsAutomatically?: boolean;
  supportMultipleWindows?: boolean;
  allowFileAccess?: boolean;
  allowContentAccess?: boolean;
  allowFileAccessFromFileURLs?: boolean;
  allowUniversalAccessFromFileURLs?: boolean;
  userAgent?: string;
  nestedScrollInterop?: boolean;
  supportZoom?: boolean;
  builtInZoomControls?: boolean;
  displayZoomControls?: boolean;
  useWideViewPort?: boolean;
  loadWithOverviewMode?: boolean;
  mixedContentMode?: ComposeWebViewMixedContentMode;
  mediaPlaybackRequiresUserGesture?: boolean;
  textZoom?: number;
  cacheMode?: ComposeWebViewCacheMode;
  safeBrowsingEnabled?: boolean;
  acceptThirdPartyCookies?: boolean;
  controller?: ComposeWebViewController;
  onPageStarted?: (event: ComposeWebViewPageEvent) => void | Promise<void>;
  onPageFinished?: (event: ComposeWebViewPageEvent) => void | Promise<void>;
  onReceivedError?: (event: ComposeWebViewErrorEvent) => void | Promise<void>;
  onReceivedHttpError?: (event: ComposeWebViewHttpErrorEvent) => void | Promise<void>;
  onReceivedSslError?: (event: ComposeWebViewSslErrorEvent) => void | Promise<void>;
  onDownloadStart?: (event: ComposeWebViewDownloadEvent) => void | Promise<void>;
  onConsoleMessage?: (event: ComposeWebViewConsoleEvent) => void | Promise<void>;
  onUrlChanged?: (event: ComposeWebViewNavigationEvent) => void | Promise<void>;
  onProgressChanged?: (event: ComposeWebViewProgressEvent) => void | Promise<void>;
  onStateChanged?: (event: ComposeWebViewState) => void | Promise<void>;
  onLifecycleEvent?: (event: ComposeWebViewLifecycleEvent) => void | Promise<void>;
  onShouldOverrideUrlLoading?: (
    request: ComposeWebViewNavigationRequest
  ) =>
    | ComposeWebViewNavigationDecision
    | null
    | undefined
    | Promise<ComposeWebViewNavigationDecision | null | undefined>;
  onInterceptRequest?: (
    request: ComposeWebViewResourceRequest
  ) =>
    | ComposeWebViewResourceDecision
    | null
    | undefined
    | Promise<ComposeWebViewResourceDecision | null | undefined>;
}

export interface ComposeNode {
  type: string;
  props?: Record<string, unknown>;
  children?: ComposeNode[];
}

export type ComposeChildren = ComposeNode | ComposeNode[] | null | undefined;

export type ComposeNodeFactory<TProps extends Record<string, unknown> = Record<string, unknown>> = (
  props?: TProps,
  children?: ComposeChildren
) => ComposeNode;

export interface ComposeUiFactoryRegistry {
  Column: ComposeNodeFactory<ColumnProps>;
  Row: ComposeNodeFactory<RowProps>;
  Box: ComposeNodeFactory<BoxProps>;
  Spacer: ComposeNodeFactory<SpacerProps>;
  Text: ComposeNodeFactory<TextProps>;
  Markdown: ComposeNodeFactory<MarkdownProps>;
  TextField: ComposeNodeFactory<TextFieldProps>;
  Switch: ComposeNodeFactory<SwitchProps>;
  Checkbox: ComposeNodeFactory<CheckboxProps>;
  Button: ComposeNodeFactory<ButtonProps>;
  IconButton: ComposeNodeFactory<IconButtonProps>;
  Card: ComposeNodeFactory<CardProps>;
  Surface: ComposeNodeFactory<SurfaceProps>;
  Icon: ComposeNodeFactory<IconProps>;
  LazyColumn: ComposeNodeFactory<LazyColumnProps>;
  LinearProgressIndicator: ComposeNodeFactory<LinearProgressIndicatorProps>;
  CircularProgressIndicator: ComposeNodeFactory<CircularProgressIndicatorProps>;
  SnackbarHost: ComposeNodeFactory<SnackbarHostProps>;
  /** @since ToolPkg API 1.0.1 */
  AlertDialog: ComposeNodeFactory<AlertDialogProps>;
  /** @since ToolPkg API 1.0.1 */
  Dialog: ComposeNodeFactory<DialogProps>;
  AiChat: ComposeNodeFactory<AiChatProps>;
  AdaptiveSidePanel: ComposeNodeFactory<AdaptiveSidePanelProps>;
  Canvas: ComposeNodeFactory<CanvasProps>;
  WebView: ComposeNodeFactory<WebViewProps>;
}

export interface ComposeTemplateValues {
  [key: string]: string | number | boolean | null | undefined;
}

export interface ComposeUiModuleSpec {
  id?: string;
  runtime?: string;
  [key: string]: unknown;
}

export interface ComposeToolCallConfig {
  type?: string;
  name: string;
  params?: Record<string, unknown>;
}

export interface ComposeResolveToolNameRequest {
  packageName?: string;
  subpackageId?: string;
  toolName: string;
  preferImported?: boolean;
}

export type ComposeFilePickerMode =
  | "document"
  | "image"
  | "video"
  | "media"
  | "directory"
  | "camera";

export interface ComposeFilePickerOptions {
  /**
   * Selection source. Defaults to "document".
   */
  picker?: ComposeFilePickerMode;
  /**
   * MIME filter for the document picker only.
   */
  mimeTypes?: string[];
  /**
   * Enables multiple selection for document, image, video, and media pickers.
   */
  allowMultiple?: boolean;
  /**
   * Retains URI access for document and directory pickers.
   */
  persistPermission?: boolean;
}

export interface ComposePickedFile {
  /**
   * Selected content URI. Directory results expose this as their only location.
   */
  uri: string;
  /**
   * Temporary local file for document, visual-media, and camera results.
   */
  path?: string;
  name?: string;
  mimeType?: string;
  size?: number | null;
}

export interface ComposeFilePickerResult {
  cancelled: boolean;
  files: ComposePickedFile[];
}

export interface ComposeRouteInfo {
  routeId: string;
  runtime: string;
  title?: string | null;
  ownerPackageName?: string | null;
  toolPkgUiModuleId?: string | null;
}

export interface ComposeDslContext {
  MaterialTheme: ComposeMaterialTheme;
  useState<T>(key: string, initialValue: T): [T, (value: T) => void];
  useMutable<T>(key: string, initialValue: T): [T, (value: T) => void];
  useRef<T>(key: string, initialValue: T): { current: T };
  useMemo<T>(key: string, factory: () => T, deps?: unknown[]): T;
  measureText(request: ComposeTextMeasureRequest): ComposeTextMeasureResult;

  callTool<T = any>(toolName: string, params?: Record<string, unknown>): Promise<T>;
  getEnv(key: string): string | undefined;
  setEnv(key: string, value: string): Promise<void> | void;
  navigate(route: string, args?: Record<string, unknown>): Promise<void> | void;
  showToast(message: string): Promise<void> | void;
  reportError(error: unknown): Promise<void> | void;
  createWebViewController(key: string): ComposeWebViewController;
  openFilePicker(options?: ComposeFilePickerOptions): Promise<ComposeFilePickerResult>;

  /**
   * Returns runtime module spec parsed from a registered toolpkg runtime module entry.
   */
  getModuleSpec?<TSpec extends ComposeUiModuleSpec = ComposeUiModuleSpec>(): TSpec;

  /**
   * Runtime identity of current compose_dsl module.
   */
  getCurrentPackageName?(): string | undefined;
  getCurrentToolPkgId?(): string | undefined;
  getCurrentUiModuleId?(): string | undefined;

  /**
   * Formats template text like "failed: {error}" with provided values.
   */
  formatTemplate?(template: string, values: ComposeTemplateValues): string;

  /**
   * Batch environment writes; host may implement atomically.
   */
  setEnvs?(values: Record<string, string>): Promise<void> | void;

  /**
   * Discover available route ids that can be used with ctx.navigate(...).
   */
  listRoutes?(): ComposeRouteInfo[];
  getHostRoutes?(): ComposeRouteInfo[];

  /**
   * Optional toolCall-compatible bridge so compose_dsl script can use package-tool style calls.
   */
  toolCall?<T = unknown>(toolName: string, toolParams?: Record<string, unknown>): Promise<T>;
  toolCall?<T = unknown>(
    toolType: string,
    toolName: string,
    toolParams?: Record<string, unknown>
  ): Promise<T>;
  toolCall?<T = unknown>(config: ComposeToolCallConfig): Promise<T>;

  /**
   * Package-manager bridge methods.
   */
  isPackageImported?(packageName: string): Promise<boolean> | boolean;
  importPackage?(packageName: string): Promise<string> | string;
  removePackage?(packageName: string): Promise<string> | string;
  usePackage?(packageName: string): Promise<string> | string;
  listImportedPackages?(): Promise<string[]> | string[];

  /**
   * Resolve runtime tool name for a package/subpackage before calling it directly.
   */
  resolveToolName?(request: ComposeResolveToolNameRequest): Promise<string> | string;

  h<TProps extends Record<string, unknown> = Record<string, unknown>>(
    type: string,
    props?: TProps,
    children?: ComposeChildren
  ): ComposeNode;

  Modifier: ComposeModifierProxy;

  UI: ComposeUiFactoryRegistry &
    ComposeMaterial3GeneratedUiFactoryRegistry &
    Record<string, ComposeNodeFactory<Record<string, unknown>>>;
}

export type ComposeDslScreen = (ctx: ComposeDslContext) => ComposeNode | Promise<ComposeNode>;
