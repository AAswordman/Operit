export type TickHandler = (now: number, deltaMs: number) => void;

const handlers = new Set<TickHandler>();
let frame = 0;
let last = 0;

function loop(now: number): void {
  const delta = last === 0 ? 16 : Math.min(64, now - last);
  last = now;
  for (const handler of Array.from(handlers)) {
    handler(now, delta);
  }
  if (handlers.size > 0) {
    frame = requestAnimationFrame(loop);
  } else {
    frame = 0;
    last = 0;
  }
}

/** 共享一个 requestAnimationFrame 循环，没有订阅者时自动停下。 */
export function onTick(handler: TickHandler): () => void {
  handlers.add(handler);
  if (frame === 0) frame = requestAnimationFrame(loop);
  return () => {
    handlers.delete(handler);
  };
}
