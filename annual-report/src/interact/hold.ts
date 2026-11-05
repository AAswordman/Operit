import { onTick } from '../motion/ticker.ts';
import { ownGesture } from './press.ts';

export interface HoldOptions {
  /** 按满所需毫秒数。 */
  duration: number;
  onProgress(progress: number): void;
  onComplete(): void;
  /** 中途松手时调用，进度会自己回落。 */
  onEarlyRelease?(progress: number): void;
  onMilestone?(milestone: number): void;
}

/** 长按蓄力：按住时进度上涨，松手后回落，按满即完成。 */
export function holdToFill(element: HTMLElement, options: HoldOptions): () => void {
  ownGesture(element);
  let progress = 0;
  let holding = false;
  let done = false;
  let lastMilestone = 0;
  let stopTick: (() => void) | null = null;

  const ensureTick = () => {
    if (stopTick !== null) return;
    stopTick = onTick((_now, delta) => {
      if (done) return;
      if (holding) {
        progress = Math.min(1, progress + delta / options.duration);
      } else {
        // 松手后以三倍速度回落，留出重新按住的余地。
        progress = Math.max(0, progress - (delta / options.duration) * 3);
      }
      const milestone = Math.floor(progress * 4);
      if (holding && milestone > lastMilestone && milestone < 4) options.onMilestone?.(milestone);
      lastMilestone = milestone;
      options.onProgress(progress);
      if (progress >= 1) {
        done = true;
        holding = false;
        element.classList.remove('is-holding');
        stopTick?.();
        stopTick = null;
        options.onComplete();
      } else if (!holding && progress === 0) {
        stopTick?.();
        stopTick = null;
      }
    });
  };

  const down = (event: PointerEvent) => {
    if (done) return;
    event.preventDefault();
    element.setPointerCapture(event.pointerId);
    holding = true;
    element.classList.add('is-holding');
    ensureTick();
  };
  const up = () => {
    if (!holding) return;
    holding = false;
    element.classList.remove('is-holding');
    if (!done && progress < 1) options.onEarlyRelease?.(progress);
  };
  element.addEventListener('pointerdown', down);
  element.addEventListener('pointerup', up);
  element.addEventListener('pointercancel', up);
  element.addEventListener('contextmenu', (event) => event.preventDefault());
  return () => {
    element.removeEventListener('pointerdown', down);
    element.removeEventListener('pointerup', up);
    element.removeEventListener('pointercancel', up);
    stopTick?.();
    stopTick = null;
  };
}
