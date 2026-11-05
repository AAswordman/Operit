import type { SceneContext } from '../deck/deck.ts';
import { EASE, play } from '../motion/anim.ts';
import { artText, prepareStamp, stampIn } from '../sketch/artText.ts';
import { el } from '../sketch/svg.ts';

export interface FrameOptions {
  kicker: string;
  title: string;
  seed: number;
}

export interface Frame {
  header: HTMLElement;
  title: HTMLElement;
  stage: HTMLElement;
  /** 标题逐字盖印入场。 */
  intro(delay?: number): Promise<void>;
}

/** 每页统一的版式：小标签、手写感标题、主舞台。 */
export function sceneFrame(ctx: SceneContext, options: FrameOptions): Frame {
  const kicker = el('div', 'kicker', [el('span', 'kicker-dot'), el('span', '', [options.kicker])]);
  const title = artText(options.title, { seed: options.seed, className: 'title' });
  const header = el('header', 'scene-header', [kicker, title]);
  const stage = el('div', 'stage');
  ctx.body.append(header, stage);
  prepareStamp(title);
  kicker.style.opacity = '0';
  return {
    header,
    title,
    stage,
    intro(delay = 0) {
      const kick = play(kicker, [{ opacity: 0, transform: 'translateX(-10px)' }, { opacity: 1, transform: 'translateX(0)' }], {
        duration: 420,
        delay,
        easing: EASE.outExpo,
      });
      return Promise.all([kick, stampIn(title, delay + 120)]).then(() => undefined);
    },
  };
}
