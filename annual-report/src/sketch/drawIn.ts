import { prefersReducedMotion } from '../motion/anim.ts';

export interface DrawInOptions {
  /** 每像素笔画耗时，模拟真实运笔速度。 */
  msPerPx?: number;
  minDuration?: number;
  maxDuration?: number;
  delay?: number;
  /** 相邻笔画的间隔；为负数时允许重叠。 */
  gap?: number;
  easing?: string;
  /** 为 true 时所有笔画同时开始。 */
  together?: boolean;
}

const lengths = new WeakMap<SVGGeometryElement, number>();

function lengthOf(path: SVGGeometryElement): number {
  const cached = lengths.get(path);
  if (cached !== undefined) return cached;
  const length = path.getTotalLength();
  lengths.set(path, length);
  return length;
}

/** 把笔画收起来，等待描绘。必须在元素挂进文档之后调用。 */
export function hideStrokes(paths: SVGGeometryElement[]): void {
  for (const path of paths) {
    const length = lengthOf(path) + 1;
    path.style.strokeDasharray = `${length} ${length}`;
    path.style.strokeDashoffset = `${length}`;
    if (path.dataset.role === 'fill') path.style.opacity = '0';
  }
}

export function showStrokes(paths: SVGGeometryElement[]): void {
  for (const path of paths) {
    path.style.strokeDasharray = '';
    path.style.strokeDashoffset = '';
    path.style.opacity = '';
  }
}

/** 按顺序把笔画描出来。返回值在最后一笔完成时兑现。 */
export function drawIn(paths: SVGGeometryElement[], options: DrawInOptions = {}): Promise<void> {
  if (paths.length === 0) return Promise.resolve();
  if (prefersReducedMotion()) {
    showStrokes(paths);
    return Promise.resolve();
  }
  const msPerPx = options.msPerPx ?? 1.1;
  const minDuration = options.minDuration ?? 90;
  const maxDuration = options.maxDuration ?? 900;
  const gap = options.gap ?? 30;
  const easing = options.easing ?? 'cubic-bezier(.45,.05,.3,1)';
  let cursor = options.delay ?? 0;
  const finished: Promise<void>[] = [];
  for (const path of paths) {
    const length = lengthOf(path) + 1;
    const duration = Math.min(maxDuration, Math.max(minDuration, length * msPerPx));
    if (path.dataset.role === 'fill') {
      const animation = path.animate([{ opacity: 0 }, { opacity: 1 }], {
        duration: duration * 0.8,
        delay: cursor,
        easing: 'ease-out',
        fill: 'both',
      });
      finished.push(
        animation.finished.then(() => {
          path.style.opacity = '';
          animation.cancel();
        }),
      );
    } else {
      path.style.strokeDasharray = `${length} ${length}`;
      const animation = path.animate(
        [{ strokeDashoffset: `${length}` }, { strokeDashoffset: '0' }],
        { duration, delay: cursor, easing, fill: 'both' },
      );
      finished.push(
        animation.finished.then(() => {
          path.style.strokeDasharray = '';
          path.style.strokeDashoffset = '';
          animation.cancel();
        }),
      );
    }
    if (options.together !== true) cursor += duration + gap;
  }
  return Promise.all(finished).then(() => undefined);
}
