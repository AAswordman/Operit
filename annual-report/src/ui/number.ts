import { EASE, play } from '../motion/anim.ts';
import { handText, hideHand, showHand, writeHand, type HandOptions } from '../sketch/handwriting.ts';
import { el } from '../sketch/svg.ts';

export interface WrittenOptions extends HandOptions {
  /** 跟在数字后面的单位，用系统字体。 */
  unit?: string;
  /** 写在数字前面的小字。 */
  prefix?: string;
}

export interface Written {
  el: HTMLElement;
  write(delay?: number, speed?: number): Promise<void>;
  show(): void;
}

/** 手写数字：先隐藏，揭晓时一笔一笔写出来。 */
export function written(text: string, options: WrittenOptions): Written {
  const hand = handText(text, options);
  const root = el('span', 'written');
  // 单位和前缀贴着手写数字的基线，而不是 SVG 的底边（底边下面还留着下伸空间）。
  const descent = `${(hand.height - hand.baseline).toFixed(1)}px`;
  if (options.prefix !== undefined) {
    const prefix = el('span', 'written-prefix', [options.prefix]);
    prefix.style.marginBottom = descent;
    root.append(prefix);
  }
  root.append(hand.svg);
  const unit = options.unit === undefined ? null : el('span', 'written-unit', [options.unit]);
  if (unit !== null) {
    unit.style.opacity = '0';
    unit.style.marginBottom = descent;
    root.append(unit);
  }
  let prepared = false;
  const prepare = () => {
    if (prepared) return;
    prepared = true;
    hideHand(hand);
  };
  // 笔画长度要在挂进文档后才能测量，所以第一次写之前再隐藏。
  queueMicrotask(() => {
    if (hand.svg.isConnected) prepare();
  });
  return {
    el: root,
    async write(delay = 0, speed = 1) {
      prepare();
      await writeHand(hand, speed, delay);
      if (unit !== null) {
        await play(unit, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], {
          duration: 360,
          easing: EASE.outExpo,
        });
      }
    },
    show() {
      prepared = true;
      showHand(hand);
      if (unit !== null) unit.style.opacity = '1';
    },
  };
}
