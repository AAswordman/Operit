// 生成抽屉里年度报告入口徽章的手绘路径。
// 徽章由代码绘制：rough.js 画纸片、胶带和涂鸦，手写字形与报告页共用 src/sketch/glyphPath.ts。
// 同一张图用三组 seed 各画一遍，App 里低帧率轮换，形成手绘动画里线条微微颤动的效果。
//
// 用法：
//   node ./scripts/generate-entry-badge.ts --year 2026 [--preview <html 输出路径>]
// 产物：
//   app/src/main/java/com/ai/assistance/operit/ui/features/annualreport/entry/AnnualReportBadgeArt.kt

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import rough from 'roughjs';
import { layoutGlyphs } from '../src/sketch/glyphPath.ts';

type Ink = 'paper' | 'ink' | 'ghost' | 'lavender' | 'coral' | 'yellow' | 'tape' | 'shadow';
type Kind = 'stroke' | 'fill';

interface Stroke {
  d: string;
  ink: Ink;
  kind: Kind;
  width: number;
}

interface Sparkle {
  x: number;
  y: number;
  size: number;
  ink: Ink;
  phase: number;
}

const WIDTH = 200;
const HEIGHT = 100;
const FRAMES = 3;
const TOKEN = 'ink:';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const kotlinPath = resolve(
  projectRoot,
  '..',
  'app/src/main/java/com/ai/assistance/operit/ui/features/annualreport/entry/AnnualReportBadgeArt.kt',
);

const generator = rough.generator();

interface ShapeStyle {
  seed: number;
  stroke?: Ink;
  strokeWidth?: number;
  fill?: Ink;
  fillStyle?: 'solid' | 'hachure' | 'zigzag';
  hachureGap?: number;
  fillWeight?: number;
  roughness?: number;
  multiStroke?: boolean;
}

function options(style: ShapeStyle) {
  return {
    seed: style.seed,
    stroke: style.stroke === undefined ? 'none' : `${TOKEN}${style.stroke}`,
    strokeWidth: style.strokeWidth ?? 1.6,
    fill: style.fill === undefined ? undefined : `${TOKEN}${style.fill}`,
    fillStyle: style.fillStyle ?? 'solid',
    hachureGap: style.hachureGap ?? 3,
    fillWeight: style.fillWeight ?? 1,
    roughness: style.roughness ?? 1,
    disableMultiStroke: style.multiStroke === false,
    fixedDecimalPlaceDigits: 1,
  };
}

function inkOf(value: string | undefined): Ink | null {
  if (value === undefined || value === 'none') return null;
  if (!value.startsWith(TOKEN)) throw new Error(`颜色必须来自色板：${value}`);
  return value.slice(TOKEN.length) as Ink;
}

function collect(drawable: ReturnType<typeof generator.path>): Stroke[] {
  const strokes: Stroke[] = [];
  for (const info of generator.toPaths(drawable)) {
    const fillInk = inkOf(info.fill);
    const strokeInk = inkOf(info.stroke);
    if (fillInk !== null) {
      strokes.push({ d: info.d, ink: fillInk, kind: 'fill', width: 0 });
    } else if (strokeInk !== null) {
      strokes.push({ d: info.d, ink: strokeInk, kind: 'stroke', width: Math.round(info.strokeWidth * 10) / 10 });
    }
  }
  return strokes;
}

function rotate(points: [number, number][], cx: number, cy: number, degrees: number): [number, number][] {
  const angle = (degrees * Math.PI) / 180;
  return points.map(([x, y]) => [
    cx + (x - cx) * Math.cos(angle) - (y - cy) * Math.sin(angle),
    cy + (x - cx) * Math.sin(angle) + (y - cy) * Math.cos(angle),
  ]);
}

function writing(text: string, size: number, x: number, y: number, seed: number, ink: Ink, ghost: boolean, slant?: number): Stroke[] {
  const layout = layoutGlyphs(text, { size, seed, originX: x, originY: y, slant, tracking: 3 });
  const width = Math.round(size * 0.1 * 10) / 10;
  const strokes: Stroke[] = [];
  if (ghost) {
    for (const stroke of layout.ghosts) strokes.push({ d: stroke.d, ink: 'ghost', kind: 'stroke', width: Math.round(width * 0.45 * 10) / 10 });
  }
  for (const stroke of layout.strokes) strokes.push({ d: stroke.d, ink, kind: 'stroke', width });
  return strokes;
}

function frame(seed: number, year: number): Stroke[] {
  const strokes: Stroke[] = [];
  // 纸片：略微倾斜的手撕卡片，先画一层硬朗的投影。
  const card = rotate([[12, 12], [192, 9], [193, 90], [10, 93]], 100, 50, -2.5);
  const shadow = card.map(([x, y]): [number, number] => [x + 3, y + 4]);
  strokes.push(...collect(generator.polygon(shadow, options({ seed: seed + 1, fill: 'shadow', roughness: 0.6 }))));
  strokes.push(...collect(generator.polygon(card, options({ seed: seed + 2, stroke: 'ink', strokeWidth: 1.8, fill: 'paper', roughness: 0.9 }))));
  // 左上角的纸胶带
  const tape = rotate([[4, 6], [46, 6], [46, 18], [4, 18]], 25, 12, -32);
  strokes.push(...collect(generator.polygon(tape, options({ seed: seed + 3, fill: 'tape', roughness: 0.7 }))));
  // 文字
  strokes.push(...writing('Operit', 17, 30, 14, seed + 10, 'lavender', false, 0.2));
  strokes.push(...writing('年度报告', 27, 22, 38, seed + 20, 'ink', true));
  strokes.push(...writing(String(year), 13, 142, 15, seed + 30, 'coral', false));
  // 圈住年份
  const loop: [number, number][] = [];
  for (let index = 0; index <= 20; index += 1) {
    const t = -0.4 + ((Math.PI * 2 + 0.6) * index) / 20;
    const wobble = 1 + index * 0.004;
    loop.push([160 + 23 * wobble * Math.cos(t), 23 + 13 * wobble * Math.sin(t)]);
  }
  strokes.push(...collect(generator.curve(loop, options({ seed: seed + 4, stroke: 'coral', strokeWidth: 1.3, roughness: 0.5, multiStroke: false }))));
  // 年度报告下面的波浪线
  const wave: [number, number][] = [];
  for (let index = 0; index <= 10; index += 1) wave.push([24 + index * 12.4, 80 + (index % 2 === 0 ? -1.6 : 1.6)]);
  strokes.push(...collect(generator.curve(wave, options({ seed: seed + 5, stroke: 'coral', strokeWidth: 1.8, roughness: 0.6, multiStroke: false }))));
  // 右下角"点我"的小箭头
  strokes.push(...collect(generator.curve([[156, 72], [168, 66], [182, 70]], options({ seed: seed + 6, stroke: 'lavender', strokeWidth: 1.6, roughness: 0.5, multiStroke: false }))));
  strokes.push(...collect(generator.linearPath([[175, 64], [183, 70], [175, 76]], options({ seed: seed + 7, stroke: 'lavender', strokeWidth: 1.6, roughness: 0.4, multiStroke: false }))));
  return strokes;
}

const SPARKLES: Sparkle[] = [
  { x: 188, y: 8, size: 7, ink: 'yellow', phase: 0 },
  { x: 132, y: 30, size: 3.6, ink: 'lavender', phase: 0.35 },
  { x: 150, y: 86, size: 4.6, ink: 'coral', phase: 0.7 },
  { x: 6, y: 66, size: 5, ink: 'yellow', phase: 0.5 },
];

const PREVIEW_COLORS: Record<Ink, string> = {
  paper: '#fff4da',
  ink: '#26283b',
  ghost: 'rgba(38,40,59,0.4)',
  lavender: '#8574ff',
  coral: '#ff6f5e',
  yellow: '#ffc93a',
  tape: 'rgba(133,116,255,0.38)',
  shadow: 'rgba(38,40,59,0.85)',
};

function previewSvg(strokes: Stroke[], background: string): string {
  const paths = strokes
    .map((stroke) =>
      stroke.kind === 'fill'
        ? `<path d="${stroke.d}" fill="${PREVIEW_COLORS[stroke.ink]}"/>`
        : `<path d="${stroke.d}" fill="none" stroke="${PREVIEW_COLORS[stroke.ink]}" stroke-width="${stroke.width}" stroke-linecap="round" stroke-linejoin="round"/>`,
    )
    .join('\n');
  const sparkles = SPARKLES.map((sparkle) => {
    const s = sparkle.size;
    const w = s * 0.22;
    const d = `M ${sparkle.x} ${sparkle.y - s} L ${sparkle.x + w} ${sparkle.y - w} L ${sparkle.x + s} ${sparkle.y} L ${sparkle.x + w} ${sparkle.y + w} L ${sparkle.x} ${sparkle.y + s} L ${sparkle.x - w} ${sparkle.y + w} L ${sparkle.x - s} ${sparkle.y} L ${sparkle.x - w} ${sparkle.y - w} Z`;
    return `<path d="${d}" fill="${PREVIEW_COLORS[sparkle.ink]}"/>`;
  }).join('\n');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-8 -8 ${WIDTH + 16} ${HEIGHT + 16}" width="${(WIDTH + 16) * 4}" height="${(HEIGHT + 16) * 4}"><rect x="-8" y="-8" width="${WIDTH + 16}" height="${HEIGHT + 16}" fill="${background}"/>${paths}\n${sparkles}</svg>`;
}

function kotlinString(value: string): string {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$')}"`;
}

function kotlinSource(frames: Stroke[][]): string {
  const inkName = (ink: Ink) => ink.toUpperCase();
  const frameBlocks = frames.map((strokes) => {
    const lines = strokes.map(
      (stroke) =>
        `            BadgeStroke(${kotlinString(stroke.d)}, BadgeInk.${inkName(stroke.ink)}, BadgeStrokeKind.${stroke.kind.toUpperCase()}, ${stroke.width.toFixed(1)}f),`,
    );
    return `        listOf(\n${lines.join('\n')}\n        ),`;
  });
  const sparkleLines = SPARKLES.map(
    (sparkle) => `        BadgeSparkle(${sparkle.x.toFixed(1)}f, ${sparkle.y.toFixed(1)}f, ${sparkle.size.toFixed(1)}f, BadgeInk.${inkName(sparkle.ink)}, ${sparkle.phase.toFixed(2)}f),`,
  );
  return `// 由 annual-report/scripts/generate-entry-badge.ts 生成，修改图形请改脚本后重新生成，不要手改本文件。
package com.ai.assistance.operit.ui.features.annualreport.entry

internal enum class BadgeInk { PAPER, INK, GHOST, LAVENDER, CORAL, YELLOW, TAPE, SHADOW }

internal enum class BadgeStrokeKind { STROKE, FILL }

internal data class BadgeStroke(
    val pathData: String,
    val ink: BadgeInk,
    val kind: BadgeStrokeKind,
    val width: Float,
)

internal data class BadgeSparkle(
    val x: Float,
    val y: Float,
    val size: Float,
    val ink: BadgeInk,
    val phase: Float,
)

internal object AnnualReportBadgeArt {
    const val WIDTH = ${WIDTH}f
    const val HEIGHT = ${HEIGHT}f

    /** 同一张徽章的 ${FRAMES} 个手绘版本，轮换显示形成线条颤动。 */
    val frames: List<List<BadgeStroke>> = listOf(
${frameBlocks.join('\n')}
    )

    val sparkles: List<BadgeSparkle> = listOf(
${sparkleLines.join('\n')}
    )
}
`;
}

function main(): void {
  const args = process.argv.slice(2);
  const yearIndex = args.indexOf('--year');
  const yearText = yearIndex >= 0 ? args[yearIndex + 1] : undefined;
  if (yearText === undefined) throw new Error('缺少 --year：徽章上要写明报告年份');
  const year = Number.parseInt(yearText, 10);
  if (!Number.isInteger(year)) throw new Error(`年份无效：${yearText}`);
  const previewIndex = args.indexOf('--preview');
  const frames = Array.from({ length: FRAMES }, (_, index) => frame(2026 + index * 131, year));
  mkdirSync(dirname(kotlinPath), { recursive: true });
  writeFileSync(kotlinPath, kotlinSource(frames), 'utf8');
  console.error(`写入 ${kotlinPath}`);
  if (previewIndex >= 0) {
    const target = args[previewIndex + 1];
    if (target === undefined) throw new Error('--preview 需要输出路径');
    const html = `<!doctype html><meta charset="utf-8"><body style="margin:0;display:flex;flex-direction:column;gap:12px;padding:12px;background:#ddd">${frames
      .map((strokes) => previewSvg(strokes, '#f6f0e2'))
      .join('')}${previewSvg(frames[0] ?? [], '#141218')}</body>`;
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, html, 'utf8');
    console.error(`预览 ${target}`);
  }
}

main();
