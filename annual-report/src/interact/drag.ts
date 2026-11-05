import { ownGesture } from './press.ts';

export interface DragPoint {
  x: number;
  y: number;
  dx: number;
  dy: number;
}

export interface DragOptions {
  onStart?(point: DragPoint): void;
  onMove(point: DragPoint): void;
  onEnd?(point: DragPoint): void;
}

/** 通用拖动：坐标相对元素左上角，dx/dy 相对按下点。 */
export function onDrag(element: Element, options: DragOptions): () => void {
  ownGesture(element);
  let origin: { x: number; y: number; id: number } | null = null;
  const local = (event: PointerEvent): DragPoint => {
    const rect = element.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    return { x, y, dx: origin === null ? 0 : x - origin.x, dy: origin === null ? 0 : y - origin.y };
  };
  const down = (event: Event) => {
    if (!(event instanceof PointerEvent)) return;
    event.preventDefault();
    element.setPointerCapture(event.pointerId);
    const rect = element.getBoundingClientRect();
    origin = { x: event.clientX - rect.left, y: event.clientY - rect.top, id: event.pointerId };
    options.onStart?.(local(event));
  };
  const move = (event: Event) => {
    if (!(event instanceof PointerEvent) || origin === null || origin.id !== event.pointerId) return;
    options.onMove(local(event));
  };
  const up = (event: Event) => {
    if (!(event instanceof PointerEvent) || origin === null || origin.id !== event.pointerId) return;
    const point = local(event);
    origin = null;
    options.onEnd?.(point);
  };
  element.addEventListener('pointerdown', down);
  element.addEventListener('pointermove', move);
  element.addEventListener('pointerup', up);
  element.addEventListener('pointercancel', up);
  return () => {
    element.removeEventListener('pointerdown', down);
    element.removeEventListener('pointermove', move);
    element.removeEventListener('pointerup', up);
    element.removeEventListener('pointercancel', up);
  };
}
