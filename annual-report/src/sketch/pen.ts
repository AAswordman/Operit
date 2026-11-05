import rough from 'roughjs';
import type { Drawable, Options } from 'roughjs/bin/core';
import { svgEl } from './svg.ts';

/** 画笔颜色只能取 CSS 变量里的色板，深浅色主题由样式表切换。 */
export type Ink =
  | 'ink'
  | 'ink-soft'
  | 'pencil'
  | 'paper'
  | 'paper-deep'
  | 'yellow'
  | 'coral'
  | 'mint'
  | 'lavender'
  | 'sky'
  | 'pink'
  | 'sticky-ink'
  | 'night'
  | 'star';

export type FillStyle = 'hachure' | 'solid' | 'zigzag' | 'cross-hatch' | 'dots' | 'dashed' | 'zigzag-line';

export interface PenStyle {
  seed: number;
  stroke?: Ink | 'none';
  strokeWidth?: number;
  fill?: Ink;
  fillStyle?: FillStyle;
  fillWeight?: number;
  hachureGap?: number;
  hachureAngle?: number;
  roughness?: number;
  bowing?: number;
  multiStroke?: boolean;
  preserveVertices?: boolean;
}

type Point = [number, number];

const generator = rough.generator();
const TOKEN_PREFIX = 'ink:';

function toOptions(style: PenStyle): Options {
  const options: Options = {
    seed: style.seed,
    stroke: style.stroke === undefined ? `${TOKEN_PREFIX}ink` : style.stroke === 'none' ? 'none' : `${TOKEN_PREFIX}${style.stroke}`,
    strokeWidth: style.strokeWidth ?? 2,
    roughness: style.roughness ?? 1.1,
    bowing: style.bowing ?? 1,
    disableMultiStroke: style.multiStroke === false,
    preserveVertices: style.preserveVertices ?? false,
    fixedDecimalPlaceDigits: 1,
  };
  if (style.fill !== undefined) {
    options.fill = `${TOKEN_PREFIX}${style.fill}`;
    options.fillStyle = style.fillStyle ?? 'hachure';
    if (style.fillWeight !== undefined) options.fillWeight = style.fillWeight;
    if (style.hachureGap !== undefined) options.hachureGap = style.hachureGap;
    if (style.hachureAngle !== undefined) options.hachureAngle = style.hachureAngle;
  }
  return options;
}

function paint(value: string | undefined): string {
  if (value === undefined || value === 'none') return 'none';
  if (!value.startsWith(TOKEN_PREFIX)) throw new Error(`画笔颜色必须来自色板：${value}`);
  return `var(--${value.slice(TOKEN_PREFIX.length)})`;
}

/** 把 rough.js 的图形转成普通 path，颜色走 CSS 变量，便于做描绘动画和主题切换。 */
export function renderDrawable(drawable: Drawable, className?: string): SVGGElement {
  const group = svgEl('g');
  if (className !== undefined) group.setAttribute('class', className);
  for (const info of generator.toPaths(drawable)) {
    const path = svgEl('path', { d: info.d });
    path.style.fill = paint(info.fill);
    path.style.stroke = paint(info.stroke);
    path.style.strokeWidth = String(info.strokeWidth);
    path.setAttribute('stroke-linecap', 'round');
    path.setAttribute('stroke-linejoin', 'round');
    if (info.fill !== undefined && info.fill !== 'none') {
      path.dataset.role = 'fill';
      path.style.fillRule = 'evenodd';
    } else if (info.stroke === drawable.options.fill) {
      path.dataset.role = 'hatch';
    } else {
      path.dataset.role = 'outline';
    }
    group.append(path);
  }
  return group;
}

export const pen = {
  line(x1: number, y1: number, x2: number, y2: number, style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.line(x1, y1, x2, y2, toOptions(style)), className);
  },
  rect(x: number, y: number, width: number, height: number, style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.rectangle(x, y, width, height, toOptions(style)), className);
  },
  ellipse(cx: number, cy: number, width: number, height: number, style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.ellipse(cx, cy, width, height, toOptions(style)), className);
  },
  circle(cx: number, cy: number, diameter: number, style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.circle(cx, cy, diameter, toOptions(style)), className);
  },
  polyline(points: Point[], style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.linearPath(points, toOptions(style)), className);
  },
  polygon(points: Point[], style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.polygon(points, toOptions(style)), className);
  },
  curve(points: Point[], style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.curve(points, toOptions(style)), className);
  },
  arc(
    cx: number,
    cy: number,
    width: number,
    height: number,
    start: number,
    stop: number,
    closed: boolean,
    style: PenStyle,
    className?: string,
  ): SVGGElement {
    return renderDrawable(generator.arc(cx, cy, width, height, start, stop, closed, toOptions(style)), className);
  },
  path(d: string, style: PenStyle, className?: string): SVGGElement {
    return renderDrawable(generator.path(d, toOptions(style)), className);
  },
};
