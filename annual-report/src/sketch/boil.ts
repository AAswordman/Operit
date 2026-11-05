import { prefersReducedMotion } from '../motion/anim.ts';
import { onTick } from '../motion/ticker.ts';
import { svgEl } from './svg.ts';

export interface Boilable {
  group: SVGGElement;
  start(): void;
  stop(): void;
}

/**
 * 手绘动画里的"线条沸腾"：同一图形用不同 seed 画几遍，低帧率轮换显示。
 * 只在当前场景运行，离开时必须 stop。
 */
export function boilable(make: (seed: number) => SVGGElement, baseSeed: number, frames = 3, fps = 7): Boilable {
  const group = svgEl('g', { class: 'boil' });
  const variants: SVGGElement[] = [];
  for (let index = 0; index < frames; index += 1) {
    const variant = make(baseSeed + index * 97);
    variant.style.display = index === 0 ? '' : 'none';
    variants.push(variant);
    group.append(variant);
  }
  let unsubscribe: (() => void) | null = null;
  let elapsed = 0;
  let current = 0;
  const interval = 1000 / fps;
  return {
    group,
    start() {
      if (unsubscribe !== null || prefersReducedMotion()) return;
      unsubscribe = onTick((_now, delta) => {
        elapsed += delta;
        if (elapsed < interval) return;
        elapsed = 0;
        const previous = variants[current];
        current = (current + 1) % variants.length;
        const next = variants[current];
        if (previous !== undefined) previous.style.display = 'none';
        if (next !== undefined) next.style.display = '';
      });
    },
    stop() {
      if (unsubscribe === null) return;
      unsubscribe();
      unsubscribe = null;
    },
  };
}
