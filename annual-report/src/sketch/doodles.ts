import { pen, type Ink } from './pen.ts';
import { between, createRandom } from './random.ts';
import { el, svgEl, svgRoot } from './svg.ts';

type Point = [number, number];

/** 四角闪光。 */
export function sparkle(cx: number, cy: number, size: number, ink: Ink, seed: number): SVGGElement {
  const s = size;
  const w = s * 0.2;
  const points: Point[] = [
    [cx, cy - s],
    [cx + w, cy - w],
    [cx + s, cy],
    [cx + w, cy + w],
    [cx, cy + s],
    [cx - w, cy + w],
    [cx - s, cy],
    [cx - w, cy - w],
  ];
  return pen.polygon(points, {
    seed,
    stroke: ink,
    strokeWidth: Math.max(1, size * 0.08),
    fill: ink,
    fillStyle: 'solid',
    roughness: 0.6,
  });
}

/** 五角星。 */
export function star(cx: number, cy: number, radius: number, ink: Ink, seed: number, filled = true): SVGGElement {
  const points: Point[] = [];
  for (let index = 0; index < 10; index += 1) {
    const r = index % 2 === 0 ? radius : radius * 0.45;
    const angle = -Math.PI / 2 + (index * Math.PI) / 5;
    points.push([cx + r * Math.cos(angle), cy + r * Math.sin(angle)]);
  }
  return pen.polygon(points, {
    seed,
    stroke: ink,
    strokeWidth: Math.max(1.2, radius * 0.09),
    fill: filled ? ink : undefined,
    fillStyle: 'solid',
    roughness: 0.9,
  });
}

/** 手画箭头：曲线加箭头两笔。 */
export function arrow(points: Point[], ink: Ink, seed: number, width = 2.4, headSize = 12): SVGGElement {
  const group = svgEl('g');
  group.append(pen.curve(points, { seed, stroke: ink, strokeWidth: width, roughness: 1.1, multiStroke: false }));
  const end = points[points.length - 1];
  const before = points[points.length - 2];
  if (end === undefined || before === undefined) throw new Error('箭头至少需要两个点');
  const angle = Math.atan2(end[1] - before[1], end[0] - before[0]);
  const spread = 0.5;
  const left: Point = [end[0] - headSize * Math.cos(angle - spread), end[1] - headSize * Math.sin(angle - spread)];
  const right: Point = [end[0] - headSize * Math.cos(angle + spread), end[1] - headSize * Math.sin(angle + spread)];
  group.append(
    pen.polyline([left, end, right], { seed: seed + 1, stroke: ink, strokeWidth: width, roughness: 0.8, multiStroke: false }),
  );
  return group;
}

/** 波浪线。 */
export function squiggle(x: number, y: number, width: number, amplitude: number, ink: Ink, seed: number, strokeWidth = 2.2): SVGGElement {
  const points: Point[] = [];
  const steps = Math.max(4, Math.round(width / 14));
  for (let index = 0; index <= steps; index += 1) {
    points.push([x + (width * index) / steps, y + (index % 2 === 0 ? -amplitude : amplitude)]);
  }
  return pen.curve(points, { seed, stroke: ink, strokeWidth, roughness: 0.8, multiStroke: false });
}

/** 像用笔圈重点那样绕一圈多一点的椭圆。 */
export function circleMark(cx: number, cy: number, rx: number, ry: number, ink: Ink, seed: number, strokeWidth = 2.6): SVGGElement {
  const random = createRandom(seed);
  const points: Point[] = [];
  const start = between(random, -0.6, -0.2);
  const total = Math.PI * 2 + 0.55;
  const steps = 22;
  for (let index = 0; index <= steps; index += 1) {
    const t = start + (total * index) / steps;
    const wobble = 1 + between(random, -0.05, 0.05) + (index / steps) * 0.07;
    points.push([cx + rx * wobble * Math.cos(t), cy + ry * wobble * Math.sin(t)]);
  }
  return pen.curve(points, { seed, stroke: ink, strokeWidth, roughness: 0.6, multiStroke: false });
}

export function underline(x: number, y: number, width: number, ink: Ink, seed: number, strokeWidth = 3): SVGGElement {
  const random = createRandom(seed);
  return pen.curve(
    [
      [x, y + between(random, -1, 1)],
      [x + width * 0.35, y + between(random, -2, 1)],
      [x + width * 0.7, y + between(random, -1, 2)],
      [x + width, y + between(random, -3, 0)],
    ],
    { seed, stroke: ink, strokeWidth, roughness: 0.7, multiStroke: false },
  );
}

export function heart(cx: number, cy: number, size: number, ink: Ink, seed: number): SVGGElement {
  const s = size / 2;
  const d = `M ${cx} ${cy + s * 0.9} C ${cx - s * 1.6} ${cy - s * 0.1}, ${cx - s * 0.7} ${cy - s * 1.3}, ${cx} ${cy - s * 0.35} C ${cx + s * 0.7} ${cy - s * 1.3}, ${cx + s * 1.6} ${cy - s * 0.1}, ${cx} ${cy + s * 0.9} Z`;
  return pen.path(d, { seed, stroke: ink, strokeWidth: 2, fill: ink, fillStyle: 'solid', roughness: 0.7 });
}

/** HTML 层的纸胶带，两端是撕开的锯齿。 */
export function tape(width: number, height: number, rotate: number, seed: number, className = ''): HTMLElement {
  const random = createRandom(seed);
  const tooth = 6;
  const left: string[] = [];
  const right: string[] = [];
  for (let y = 0; y <= height; y += height / tooth) {
    left.push(`${between(random, 0, 5).toFixed(1)}px ${y.toFixed(1)}px`);
    right.push(`${(width - between(random, 0, 5)).toFixed(1)}px ${y.toFixed(1)}px`);
  }
  const strip = el('span', `tape ${className}`.trim());
  strip.style.width = `${width}px`;
  strip.style.height = `${height}px`;
  strip.style.setProperty('--rotate', `${rotate}deg`);
  strip.style.clipPath = `polygon(${[...left, ...right.reverse()].join(',')})`;
  return strip;
}

export type PromptIcon = 'tap' | 'hold' | 'swipe-up' | 'swipe-side' | 'scratch' | 'rub' | 'rotate' | 'drag' | 'open';

/** 交互提示的小图标。 */
export function promptIcon(kind: PromptIcon, seed: number): SVGSVGElement {
  const root = svgRoot(32, 32, `prompt-icon prompt-${kind}`);
  const style = { seed, stroke: 'ink-soft' as Ink, strokeWidth: 1.8, roughness: 0.7, multiStroke: false };
  switch (kind) {
    case 'tap':
    case 'hold': {
      root.append(pen.circle(16, 14, kind === 'hold' ? 20 : 12, { ...style, stroke: kind === 'hold' ? 'lavender' : 'ink-soft' }));
      root.append(pen.circle(16, 14, 4, { ...style, fill: 'ink-soft', fillStyle: 'solid' }));
      root.append(pen.curve([[16, 18], [17, 25], [20, 30]], style));
      break;
    }
    case 'swipe-up': {
      root.append(pen.curve([[16, 28], [15, 18], [16, 6]], style));
      root.append(pen.polyline([[9, 12], [16, 5], [23, 12]], style));
      break;
    }
    case 'swipe-side': {
      root.append(pen.curve([[3, 17], [16, 15], [29, 17]], style));
      root.append(pen.polyline([[23, 10], [29, 16], [23, 23]], style));
      break;
    }
    case 'scratch': {
      root.append(pen.polyline([[4, 24], [10, 8], [15, 24], [20, 8], [27, 24]], { ...style, stroke: 'coral' }));
      break;
    }
    case 'rub': {
      root.append(pen.curve([[4, 20], [9, 8], [14, 22], [19, 9], [24, 22], [29, 10]], { ...style, stroke: 'mint' }));
      break;
    }
    case 'rotate': {
      root.append(pen.arc(16, 16, 22, 22, -Math.PI * 0.2, Math.PI * 1.45, false, style));
      root.append(pen.polyline([[23, 4], [25, 10], [19, 11]], style));
      break;
    }
    case 'drag': {
      root.append(pen.curve([[4, 16], [16, 15], [28, 16]], style));
      root.append(pen.polyline([[9, 11], [4, 16], [9, 21]], style));
      root.append(pen.polyline([[23, 11], [28, 16], [23, 21]], style));
      break;
    }
    case 'open': {
      root.append(pen.rect(6, 10, 20, 16, style));
      root.append(pen.polyline([[6, 10], [16, 4], [26, 10]], { ...style, stroke: 'coral' }));
      break;
    }
  }
  return root;
}
