// 把手写字形骨架排版成 SVG path。纯函数，不碰 DOM：
// 前端的手写数字和入口徽章的生成脚本共用这一份实现，保证两边笔迹一致。

import { GLYPHS, type StrokePoint } from './glyphs.ts';
import { between, createRandom } from './random.ts';

export interface GlyphLayoutOptions {
  /** 字身高度，单位与输出坐标一致。 */
  size: number;
  seed: number;
  /** 斜体系数，对数字和拉丁字母生效。 */
  slant?: number;
  /** 控制点的随机偏移，单位为字身的百分比。 */
  jitter?: number;
  /** 每个字额外的间距，单位为字身的百分比。 */
  tracking?: number;
  /** 每个字的随机旋转幅度，单位为度。 */
  wobble?: number;
  /** 整段文字左上角的位置，生成脚本用它把路径直接画到徽章坐标里。 */
  originX?: number;
  originY?: number;
}

export interface LaidStroke {
  d: string;
  glyphIndex: number;
}

export interface GlyphLayout {
  strokes: LaidStroke[];
  /** 与 strokes 一一对应，用于描双线的"虚影"笔迹。 */
  ghosts: LaidStroke[];
  width: number;
  /** 包含下伸部分的总高度。 */
  height: number;
}

const DESCENT = 0.38;

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

function point(x: number, y: number): string {
  return `${round(x)} ${round(y)}`;
}

/** 均匀 Catmull-Rom 样条转三次贝塞尔：曲线经过每个控制点，转折处保持圆润。 */
export function smoothPath(points: StrokePoint[]): string {
  const first = points[0];
  if (first === undefined) throw new Error('笔画至少需要一个点');
  if (points.length === 1) return `M ${point(first[0], first[1])} l 0.1 0`;
  let d = `M ${point(first[0], first[1])}`;
  // 首尾端点复制自身作为相邻点，下标都已钳制在数组范围内。
  for (let index = 0; index < points.length - 1; index += 1) {
    const p0 = points[Math.max(0, index - 1)];
    const p1 = points[index];
    const p2 = points[index + 1];
    const p3 = points[Math.min(points.length - 1, index + 2)];
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C ${point(c1x, c1y)} ${point(c2x, c2y)} ${point(p2[0], p2[1])}`;
  }
  return d;
}

export function layoutGlyphs(text: string, options: GlyphLayoutOptions): GlyphLayout {
  const scale = options.size / 100;
  const slant = options.slant ?? 0.14;
  const jitter = options.jitter ?? 1.6;
  const tracking = options.tracking ?? 4;
  const wobble = options.wobble ?? 2.6;
  const originX = options.originX ?? 0;
  const originY = options.originY ?? 0;
  const random = createRandom(options.seed);
  const ghostRandom = createRandom(options.seed ^ 0x5bd1e995);
  const strokes: LaidStroke[] = [];
  const ghosts: LaidStroke[] = [];
  let cursor = 0;
  let glyphIndex = 0;
  for (const char of text) {
    const glyph = GLYPHS[char];
    if (glyph === undefined) throw new Error(`手写字形缺少字符「${char}」`);
    const glyphSlant = glyph.upright === true ? 0.03 : slant;
    const angle = (between(random, -wobble, wobble) * Math.PI) / 180;
    const glyphScale = between(random, 0.96, 1.04);
    const lift = between(random, -2.2, 2.2);
    const centerX = glyph.advance / 2;
    const centerY = 50;
    const place = (source: StrokePoint, noise: () => number): StrokePoint => {
      const jx = between(noise, -jitter, jitter);
      const jy = between(noise, -jitter, jitter);
      const x0 = (source[0] - centerX) * glyphScale + jx;
      const y0 = (source[1] - centerY) * glyphScale + jy;
      const rx = x0 * Math.cos(angle) - y0 * Math.sin(angle);
      const ry = x0 * Math.sin(angle) + y0 * Math.cos(angle);
      const slanted = rx + (centerY - (ry + centerY)) * glyphSlant;
      return [originX + (cursor + centerX + slanted) * scale, originY + (ry + centerY + lift) * scale];
    };
    for (const stroke of glyph.strokes) {
      strokes.push({ d: smoothPath(stroke.map((source) => place(source, random))), glyphIndex });
      ghosts.push({ d: smoothPath(stroke.map((source) => place(source, ghostRandom))), glyphIndex });
    }
    cursor += glyph.advance + tracking;
    glyphIndex += 1;
  }
  const width = Math.max(0, cursor - tracking) * scale;
  return { strokes, ghosts, width, height: options.size * (1 + DESCENT) };
}
