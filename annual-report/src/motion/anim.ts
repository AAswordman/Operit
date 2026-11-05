export const EASE = {
  out: 'cubic-bezier(.22,1,.36,1)',
  outBack: 'cubic-bezier(.34,1.56,.64,1)',
  outExpo: 'cubic-bezier(.16,1,.3,1)',
  inOut: 'cubic-bezier(.65,0,.35,1)',
  in: 'cubic-bezier(.55,0,1,.45)',
  pen: 'cubic-bezier(.45,.05,.3,1)',
} as const;

const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

export function prefersReducedMotion(): boolean {
  return reducedMotionQuery.matches;
}

/**
 * WAAPI 动画结束后把终态写回内联样式再取消动画，
 * 避免大量 fill: forwards 的动画常驻合成器。
 */
export function play(
  element: Element,
  keyframes: Keyframe[],
  options: KeyframeAnimationOptions,
): Promise<void> {
  const animation = element.animate(keyframes, { fill: 'both', ...options });
  if (prefersReducedMotion()) animation.finish();
  return animation.finished.then(
    () => {
      if (element.isConnected) animation.commitStyles();
      animation.cancel();
    },
    (error: unknown) => {
      // 动画被主动取消属于正常流程（离开场景时会取消），只记录不抛出。
      console.debug('[anim] animation cancelled', error);
    },
  );
}

export function wait(ms: number): Promise<void> {
  if (prefersReducedMotion()) return Promise.resolve();
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function nextFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

/** 弹出：带一点过冲的缩放与旋转，像贴纸被按到纸上。 */
export function popIn(element: Element, delay = 0, rotate = 0): Promise<void> {
  return play(
    element,
    [
      { opacity: 0, transform: `scale(.4) rotate(${rotate - 8}deg)` },
      { opacity: 1, transform: `scale(1.08) rotate(${rotate + 2}deg)`, offset: 0.62 },
      { opacity: 1, transform: `scale(1) rotate(${rotate}deg)` },
    ],
    { duration: 520, delay, easing: EASE.out },
  );
}

export function fadeUp(element: Element, delay = 0, distance = 14): Promise<void> {
  return play(
    element,
    [
      { opacity: 0, transform: `translateY(${distance}px)` },
      { opacity: 1, transform: 'translateY(0)' },
    ],
    { duration: 520, delay, easing: EASE.outExpo },
  );
}

/** 拒绝时的左右晃动，用于还没完成交互就想翻页的情况。 */
export function shake(element: Element, amplitude = 7): Promise<void> {
  return play(
    element,
    [
      { transform: 'translateX(0)' },
      { transform: `translateX(${-amplitude}px) rotate(-1deg)` },
      { transform: `translateX(${amplitude}px) rotate(1deg)` },
      { transform: `translateX(${-amplitude * 0.6}px)` },
      { transform: `translateX(${amplitude * 0.4}px)` },
      { transform: 'translateX(0)' },
    ],
    { duration: 460, easing: EASE.out },
  );
}

/** 把数值从 from 平滑过渡到 to，回调里拿到当前值。 */
export function tween(
  from: number,
  to: number,
  duration: number,
  onUpdate: (value: number) => void,
  easing: (t: number) => number = easeOutCubic,
): Promise<void> {
  if (prefersReducedMotion() || duration <= 0) {
    onUpdate(to);
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const start = performance.now();
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      onUpdate(from + (to - from) * easing(t));
      if (t < 1) {
        requestAnimationFrame(step);
      } else {
        resolve();
      }
    };
    requestAnimationFrame(step);
  });
}

export function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3;
}

export function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}
