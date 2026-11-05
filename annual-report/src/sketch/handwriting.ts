import { drawIn, hideStrokes, showStrokes } from './drawIn.ts';
import { layoutGlyphs } from './glyphPath.ts';
import type { Ink } from './pen.ts';
import { svgEl } from './svg.ts';

export interface HandOptions {
  size: number;
  seed: number;
  ink?: Ink;
  /** 笔画粗细占字身的比例。 */
  weight?: number;
  /** 是否叠一层错位的细笔迹，让线条更像手描。 */
  ghost?: boolean;
  slant?: number;
  tracking?: number;
  className?: string;
}

export interface HandText {
  svg: SVGSVGElement;
  strokes: SVGPathElement[];
  ghosts: SVGPathElement[];
  width: number;
  height: number;
  /** 基线到 SVG 顶边的距离，用来让旁边的文字对齐手写数字的基线。 */
  baseline: number;
}

/** 用代码绘制的手写字：数字、少量汉字和拉丁字母，逐笔可动画。 */
export function handText(text: string, options: HandOptions): HandText {
  const layout = layoutGlyphs(text, {
    size: options.size,
    seed: options.seed,
    slant: options.slant,
    tracking: options.tracking,
  });
  const weight = options.size * (options.weight ?? 0.085);
  const pad = weight * 1.4;
  const width = layout.width + pad * 2;
  const height = layout.height + pad * 2;
  const svg = svgEl('svg', {
    viewBox: `${-pad} ${-pad} ${width} ${height}`,
    width: Math.ceil(width),
    height: Math.ceil(height),
    'aria-hidden': 'true',
  });
  svg.classList.add('hand');
  if (options.className !== undefined) svg.classList.add(options.className);
  const ink = `var(--${options.ink ?? 'ink'})`;
  const ghosts: SVGPathElement[] = [];
  if (options.ghost !== false) {
    for (const stroke of layout.ghosts) {
      const path = svgEl('path', { d: stroke.d });
      path.style.stroke = ink;
      path.style.strokeWidth = String(weight * 0.42);
      path.style.opacity = '0.4';
      path.classList.add('hand-ghost');
      ghosts.push(path);
      svg.append(path);
    }
  }
  const strokes: SVGPathElement[] = [];
  for (const stroke of layout.strokes) {
    const path = svgEl('path', { d: stroke.d });
    path.style.stroke = ink;
    path.style.strokeWidth = String(weight);
    path.classList.add('hand-stroke');
    strokes.push(path);
    svg.append(path);
  }
  return { svg, strokes, ghosts, width, height, baseline: options.size + pad };
}

export function hideHand(hand: HandText): void {
  hideStrokes([...hand.strokes, ...hand.ghosts]);
}

export function showHand(hand: HandText): void {
  showStrokes([...hand.strokes, ...hand.ghosts]);
}

/** 一笔一笔写出来；虚影笔迹稍后跟上，像笔尖回描。 */
export async function writeHand(hand: HandText, speed = 1, delay = 0): Promise<void> {
  const main = drawIn(hand.strokes, {
    msPerPx: 1.25 / speed,
    minDuration: 70,
    maxDuration: 520,
    gap: 26 / speed,
    delay,
  });
  const ghost = drawIn(hand.ghosts, {
    msPerPx: 1.25 / speed,
    minDuration: 70,
    maxDuration: 520,
    gap: 26 / speed,
    delay: delay + 140,
  });
  await Promise.all([main, ghost]);
}
