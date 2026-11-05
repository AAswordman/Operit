import { EASE, play } from '../motion/anim.ts';
import { between, createRandom } from './random.ts';
import { el } from './svg.ts';

export interface ArtTextOptions {
  seed: number;
  className?: string;
  /** 每个字的最大旋转角度。 */
  tilt?: number;
}

/**
 * 系统字体加代码效果做成的标题字：逐字微倾斜、上下错落，配合墨迹滤镜。
 * 文案里用 {{...}} 标出要画马克笔高亮的词，用 \n 换行。
 */
export function artText(text: string, options: ArtTextOptions): HTMLElement {
  const random = createRandom(options.seed);
  const tilt = options.tilt ?? 2.6;
  const root = el('span', `art-text ${options.className ?? ''}`.trim());
  root.setAttribute('aria-label', text.replace(/\{\{|\}\}/g, ''));
  const lines = text.split('\n');
  lines.forEach((line, lineIndex) => {
    const lineEl = el('span', 'art-line');
    const parts = line.split(/(\{\{[^}]+\}\})/);
    for (const part of parts) {
      if (part === '') continue;
      const highlighted = part.startsWith('{{') && part.endsWith('}}');
      const content = highlighted ? part.slice(2, -2) : part;
      const holder = highlighted ? el('span', 'marker') : lineEl;
      for (const char of content) {
        const span = el('span', 'ch', [char === ' ' ? ' ' : char]);
        span.style.setProperty('--r', `${between(random, -tilt, tilt).toFixed(2)}deg`);
        span.style.setProperty('--y', `${between(random, -1.6, 1.6).toFixed(2)}px`);
        span.setAttribute('aria-hidden', 'true');
        holder.append(span);
      }
      if (highlighted) lineEl.append(holder);
    }
    root.append(lineEl);
    if (lineIndex < lines.length - 1) root.append(el('br'));
  });
  return root;
}

/** 逐字盖印入场。 */
export function stampIn(root: HTMLElement, delay = 0, stagger = 34): Promise<void> {
  const chars = Array.from(root.querySelectorAll<HTMLElement>('.ch'));
  const markers = Array.from(root.querySelectorAll<HTMLElement>('.marker'));
  const jobs = chars.map((char, index) =>
    play(
      char,
      [
        { opacity: 0, transform: 'translateY(10px) rotate(calc(var(--r) * 4)) scale(1.4)' },
        { opacity: 1, transform: 'translateY(var(--y)) rotate(var(--r)) scale(1)' },
      ],
      { duration: 420, delay: delay + index * stagger, easing: EASE.outBack },
    ),
  );
  for (const marker of markers) {
    jobs.push(
      play(
        marker,
        [{ backgroundSize: '0% 100%' }, { backgroundSize: '100% 100%' }],
        { duration: 520, delay: delay + chars.length * stagger * 0.6, easing: EASE.pen },
      ),
    );
  }
  return Promise.all(jobs).then(() => undefined);
}

export function prepareStamp(root: HTMLElement): void {
  for (const char of Array.from(root.querySelectorAll<HTMLElement>('.ch'))) {
    char.style.opacity = '0';
  }
  for (const marker of Array.from(root.querySelectorAll<HTMLElement>('.marker'))) {
    marker.style.backgroundSize = '0% 100%';
  }
}
