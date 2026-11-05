/** 标记为自有手势，翻页手势不会从这里开始。 */
export function ownGesture(element: Element): void {
  if (element instanceof HTMLElement || element instanceof SVGElement) {
    element.dataset.gesture = 'own';
  }
}

export interface TapOptions {
  once?: boolean;
  /** 按下时的缩放比例。 */
  pressScale?: number;
}

/**
 * 点按：按下有回弹反馈，移动超过阈值视为拖动而不触发。返回解绑函数。
 * 不标记为自有手势：从可点的元素上开始上滑，照样可以翻页。
 */
export function onTap(element: HTMLElement, handler: () => void, options: TapOptions = {}): () => void {
  let start: { x: number; y: number; id: number } | null = null;
  const scale = options.pressScale ?? 0.95;
  const down = (event: PointerEvent) => {
    start = { x: event.clientX, y: event.clientY, id: event.pointerId };
    element.style.setProperty('--press', String(scale));
    element.classList.add('is-pressed');
  };
  const up = (event: PointerEvent) => {
    element.classList.remove('is-pressed');
    if (start === null || start.id !== event.pointerId) return;
    const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y);
    start = null;
    if (moved > 14) return;
    if (options.once === true) unbind();
    handler();
  };
  const cancel = () => {
    start = null;
    element.classList.remove('is-pressed');
  };
  const unbind = () => {
    element.removeEventListener('pointerdown', down);
    element.removeEventListener('pointerup', up);
    element.removeEventListener('pointercancel', cancel);
    element.removeEventListener('pointerleave', cancel);
  };
  element.addEventListener('pointerdown', down);
  element.addEventListener('pointerup', up);
  element.addEventListener('pointercancel', cancel);
  element.addEventListener('pointerleave', cancel);
  return unbind;
}
