import { inkColor } from '../motion/particles.ts';
import { createRandom } from '../sketch/random.ts';
import { ownGesture } from './press.ts';

export interface ScratchOptions {
  width: number;
  height: number;
  seed: number;
  label: string;
  brush?: number;
  /** 刮开比例达到多少算完成。 */
  threshold?: number;
  onProgress?(ratio: number): void;
  onComplete(): void;
}

export interface ScratchCard {
  canvas: HTMLCanvasElement;
  paint(): void;
}

/** 铅笔排线画成的刮刮乐涂层。用 destination-out 擦除，抽样估算刮开比例。 */
export function scratchCard(options: ScratchOptions): ScratchCard {
  const canvas = document.createElement('canvas');
  canvas.className = 'scratch-layer';
  ownGesture(canvas);
  const ratio = window.devicePixelRatio;
  canvas.width = Math.round(options.width * ratio);
  canvas.height = Math.round(options.height * ratio);
  canvas.style.width = `${options.width}px`;
  canvas.style.height = `${options.height}px`;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  if (ctx === null) throw new Error('无法创建刮刮乐画布');
  ctx.scale(ratio, ratio);
  const brush = options.brush ?? 30;
  const threshold = options.threshold ?? 0.52;
  let done = false;
  let last: { x: number; y: number } | null = null;
  let strokesSinceSample = 0;

  const paint = () => {
    const random = createRandom(options.seed);
    ctx.globalCompositeOperation = 'source-over';
    ctx.clearRect(0, 0, options.width, options.height);
    ctx.fillStyle = inkColor('paper-deep');
    ctx.beginPath();
    ctx.roundRect(4, 4, options.width - 8, options.height - 8, 14);
    ctx.fill();
    // 两层交叉排线，像用铅笔涂黑的格子。
    ctx.strokeStyle = inkColor('pencil');
    ctx.lineCap = 'round';
    for (const angle of [Math.PI / 4, -Math.PI / 3]) {
      ctx.lineWidth = 1.6;
      ctx.globalAlpha = 0.55;
      const step = 7;
      const span = options.width + options.height;
      for (let offset = -span; offset < span; offset += step) {
        ctx.beginPath();
        const jitter = (random() - 0.5) * 3;
        const x0 = offset + jitter;
        ctx.moveTo(x0, 0);
        ctx.lineTo(x0 + Math.cos(angle) * span * 2, Math.sin(angle) * span * 2);
        ctx.stroke();
      }
    }
    ctx.globalAlpha = 1;
    ctx.fillStyle = inkColor('ink');
    ctx.font = '600 17px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(options.label, options.width / 2, options.height / 2);
  };

  const sample = (): number => {
    const step = 12;
    const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    let cleared = 0;
    let total = 0;
    for (let y = step / 2; y < options.height; y += step) {
      for (let x = step / 2; x < options.width; x += step) {
        const index = (Math.floor(y * ratio) * canvas.width + Math.floor(x * ratio)) * 4 + 3;
        total += 1;
        if (data[index] < 40) cleared += 1;
      }
    }
    return total === 0 ? 0 : cleared / total;
  };

  const point = (event: PointerEvent) => {
    const rect = canvas.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  };

  const scrape = (event: PointerEvent) => {
    if (done) return;
    const current = point(event);
    ctx.globalCompositeOperation = 'destination-out';
    ctx.lineWidth = brush;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    // 一笔的起点：新落笔时从当前位置画一个圆点，否则从上一个采样点连线过来。
    if (last === null) {
      ctx.moveTo(current.x, current.y);
    } else {
      ctx.moveTo(last.x, last.y);
    }
    ctx.lineTo(current.x, current.y);
    ctx.stroke();
    last = current;
    strokesSinceSample += 1;
    if (strokesSinceSample >= 5) {
      strokesSinceSample = 0;
      const cleared = sample();
      options.onProgress?.(cleared);
      if (cleared >= threshold) {
        done = true;
        options.onComplete();
      }
    }
  };

  canvas.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    canvas.setPointerCapture(event.pointerId);
    last = null;
    scrape(event);
  });
  canvas.addEventListener('pointermove', (event) => {
    if (!canvas.hasPointerCapture(event.pointerId)) return;
    scrape(event);
  });
  canvas.addEventListener('pointerup', () => {
    last = null;
  });

  return { canvas, paint };
}
