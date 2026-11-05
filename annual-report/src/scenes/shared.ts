import { el } from '../sketch/svg.ts';
import { written } from '../ui/number.ts';

/** 便利贴正面的标准内容：一行手写数字、一行标签、可选的说明。 */
export function stickyContent(
  value: string,
  label: string,
  detail: string | null,
  seed: number,
  size = 26,
): { content: HTMLElement; write(): Promise<void> } {
  const number = written(value, { size, seed, ink: 'sticky-ink', weight: 0.1, tracking: 2, ghost: false });
  const content = el('div', 'sticky-content', [number.el, el('span', 'sticky-label', [label])]);
  if (detail !== null) content.append(el('span', 'sticky-detail', [detail]));
  return { content, write: () => number.write(0, 1.4) };
}
