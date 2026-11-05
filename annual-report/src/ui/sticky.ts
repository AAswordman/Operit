import { EASE, play } from '../motion/anim.ts';
import { onTap } from '../interact/press.ts';
import { handText } from '../sketch/handwriting.ts';
import { tape } from '../sketch/doodles.ts';
import { between, createRandom } from '../sketch/random.ts';
import { el } from '../sketch/svg.ts';

export type StickyColor = 'yellow' | 'pink' | 'mint' | 'blue' | 'lavender';

export interface StickyOptions {
  color: StickyColor;
  seed: number;
  /** 翻开前写在背面的问题。 */
  question: string;
  /** 翻开后才构建正面内容，返回值的 write 在翻面结束后调用。 */
  reveal(): { content: HTMLElement; write(): Promise<void> };
  onFlip(): void;
}

export interface Sticky {
  el: HTMLElement;
  isFlipped(): boolean;
}

/** 便利贴：先显示一个问号和问题，点一下翻面揭晓。 */
export function stickyNote(options: StickyOptions): Sticky {
  const random = createRandom(options.seed);
  const root = el('div', `sticky sticky-${options.color}`);
  root.style.setProperty('--tilt', `${between(random, -4, 4).toFixed(1)}deg`);
  const inner = el('div', 'sticky-inner');
  const back = el('div', 'sticky-face sticky-back');
  const mark = handText('?', { size: 30, seed: options.seed, ink: 'sticky-ink', ghost: false, weight: 0.1 });
  back.append(mark.svg, el('span', 'sticky-question', [options.question]));
  const front = el('div', 'sticky-face sticky-front');
  inner.append(back, front);
  root.append(tape(46, 15, between(random, -8, 8), options.seed + 3, 'sticky-tape'), inner);
  let flipped = false;
  onTap(
    root,
    () => {
      flipped = true;
      root.classList.add('is-flipped');
      const revealed = options.reveal();
      front.append(revealed.content);
      options.onFlip();
      void play(
        inner,
        [
          { transform: 'rotateY(0deg) scale(1)' },
          { transform: 'rotateY(100deg) scale(1.08)', offset: 0.45 },
          { transform: 'rotateY(196deg) scale(1.02)', offset: 0.78 },
          { transform: 'rotateY(180deg) scale(1)' },
        ],
        { duration: 640, easing: EASE.inOut },
      ).then(() => revealed.write());
    },
    { once: true, pressScale: 0.94 },
  );
  return {
    el: root,
    isFlipped: () => flipped,
  };
}
