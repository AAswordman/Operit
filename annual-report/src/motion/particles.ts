import type { Ink } from '../sketch/pen.ts';
import { prefersReducedMotion } from './anim.ts';
import { onTick } from './ticker.ts';

interface Scrap {
  x: number;
  y: number;
  vx: number;
  vy: number;
  rotation: number;
  spin: number;
  size: number;
  color: string;
  shape: 'rect' | 'tri' | 'squiggle';
  life: number;
  maxLife: number;
}

export interface BurstOptions {
  count?: number;
  inks?: Ink[];
  spread?: number;
  speed?: number;
  gravity?: number;
  /** 发射方向，弧度，默认向上。 */
  direction?: number;
}

let canvas: HTMLCanvasElement | null = null;
let context: CanvasRenderingContext2D | null = null;
const scraps: Scrap[] = [];
let stopLoop: (() => void) | null = null;
let gravityNow = 0.0016;

function ensureCanvas(): CanvasRenderingContext2D {
  if (canvas !== null && context !== null) return context;
  canvas = document.createElement('canvas');
  canvas.className = 'fx-layer';
  document.body.append(canvas);
  const ctx = canvas.getContext('2d');
  if (ctx === null) throw new Error('无法创建粒子画布');
  context = ctx;
  resize();
  window.addEventListener('resize', resize);
  return ctx;
}

function resize(): void {
  if (canvas === null || context === null) return;
  const ratio = window.devicePixelRatio;
  canvas.width = Math.round(window.innerWidth * ratio);
  canvas.height = Math.round(window.innerHeight * ratio);
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
}

export function inkColor(ink: Ink): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(`--${ink}`).trim();
  if (value === '') throw new Error(`色板缺少 --${ink}`);
  return value;
}

/** 纸屑迸发：盖章、揭晓时的庆祝效果。坐标为视口坐标。 */
export function burst(x: number, y: number, options: BurstOptions = {}): void {
  if (prefersReducedMotion()) return;
  ensureCanvas();
  const inks = options.inks ?? ['coral', 'yellow', 'mint', 'lavender', 'sky'];
  const colors = inks.map(inkColor);
  if (colors.length === 0) throw new Error('纸屑至少需要一种颜色');
  const count = options.count ?? 26;
  const spread = options.spread ?? Math.PI * 0.9;
  const speed = options.speed ?? 0.62;
  const direction = options.direction ?? -Math.PI / 2;
  gravityNow = options.gravity ?? 0.0016;
  for (let index = 0; index < count; index += 1) {
    const angle = direction + (Math.random() - 0.5) * spread;
    const velocity = speed * (0.45 + Math.random() * 0.75);
    const shapeRoll = Math.random();
    scraps.push({
      x,
      y,
      vx: Math.cos(angle) * velocity,
      vy: Math.sin(angle) * velocity,
      rotation: Math.random() * Math.PI,
      spin: (Math.random() - 0.5) * 0.02,
      size: 5 + Math.random() * 7,
      color: colors[index % colors.length],
      shape: shapeRoll < 0.5 ? 'rect' : shapeRoll < 0.8 ? 'tri' : 'squiggle',
      life: 0,
      maxLife: 1400 + Math.random() * 900,
    });
  }
  if (stopLoop === null) stopLoop = onTick(step);
}

function step(_now: number, delta: number): void {
  const ctx = ensureCanvas();
  ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
  for (let index = scraps.length - 1; index >= 0; index -= 1) {
    const scrap = scraps[index];
    if (scrap === undefined) continue;
    scrap.life += delta;
    scrap.vy += gravityNow * delta;
    scrap.vx *= 0.992;
    scrap.x += scrap.vx * delta;
    scrap.y += scrap.vy * delta;
    scrap.rotation += scrap.spin * delta;
    const fade = 1 - Math.max(0, (scrap.life - scrap.maxLife * 0.7) / (scrap.maxLife * 0.3));
    if (scrap.life >= scrap.maxLife || scrap.y > window.innerHeight + 40) {
      scraps.splice(index, 1);
      continue;
    }
    ctx.save();
    ctx.globalAlpha = Math.max(0, fade);
    ctx.translate(scrap.x, scrap.y);
    ctx.rotate(scrap.rotation);
    ctx.fillStyle = scrap.color;
    ctx.strokeStyle = scrap.color;
    // 纸屑在空中翻转：用 cos 压扁宽度模拟三维翻面。
    const flip = Math.abs(Math.cos(scrap.life * 0.012 + scrap.size));
    if (scrap.shape === 'rect') {
      ctx.fillRect((-scrap.size / 2) * flip, -scrap.size / 3, scrap.size * flip, (scrap.size * 2) / 3);
    } else if (scrap.shape === 'tri') {
      ctx.beginPath();
      ctx.moveTo(0, -scrap.size / 2);
      ctx.lineTo((scrap.size / 2) * flip, scrap.size / 2);
      ctx.lineTo((-scrap.size / 2) * flip, scrap.size / 2);
      ctx.closePath();
      ctx.fill();
    } else {
      ctx.lineWidth = 2;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(-scrap.size, 0);
      ctx.quadraticCurveTo(-scrap.size / 2, -scrap.size / 2 * flip, 0, 0);
      ctx.quadraticCurveTo(scrap.size / 2, scrap.size / 2 * flip, scrap.size, 0);
      ctx.stroke();
    }
    ctx.restore();
  }
  if (scraps.length === 0 && stopLoop !== null) {
    stopLoop();
    stopLoop = null;
    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
  }
}
