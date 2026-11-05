import type { HapticKind } from '../bridge.ts';
import { EASE, play, prefersReducedMotion, shake } from '../motion/anim.ts';
import { promptIcon, type PromptIcon } from '../sketch/doodles.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgRoot } from '../sketch/svg.ts';

export type Surface = 'paper' | 'night' | 'cover';

export interface SceneContext {
  readonly page: HTMLElement;
  readonly body: HTMLElement;
  readonly index: number;
  /** 本页数据已经全部揭晓，解锁"上滑继续"。 */
  reveal(): void;
  isRevealed(): boolean;
  /** 设置底部的交互提示。 */
  prompt(text: string, icon: PromptIcon): void;
  haptic(kind: HapticKind): void;
  goNext(): void;
}

export interface SceneHandle {
  /** 每次成为当前页时调用；首次进入负责入场动画。 */
  enter(): void;
  /** 每次离开当前页时调用，停掉循环动画。 */
  leave(): void;
  /** 用户在揭晓前就想翻页时调用。 */
  nudge(): void;
}

export interface SceneSpec {
  id: string;
  surface: Surface;
  /** 揭晓后底部提示的文字，默认"上滑继续"。 */
  nextLabel?: string;
  build(ctx: SceneContext): SceneHandle;
}

interface PageState {
  spec: SceneSpec;
  element: HTMLElement | null;
  handle: SceneHandle | null;
  revealed: boolean;
  promptEl: HTMLElement | null;
  nextEl: HTMLElement | null;
  shadowEl: HTMLElement | null;
}

interface DragState {
  pointerId: number;
  startX: number;
  startY: number;
  lastY: number;
  lastT: number;
  velocity: number;
  mode: 'pending' | 'next' | 'prev' | 'none';
  angle: number;
}

export interface DeckOptions {
  onClose(): void;
  haptic(kind: HapticKind): void;
}

const FLIP_END = 100;
const DRAG_ANGLE_PER_PX = 0.36;
const BLOCKED_MAX_ANGLE = 9;

export class Deck {
  private readonly root: HTMLElement;
  private readonly stack: HTMLElement;
  private readonly progress: HTMLElement;
  private readonly options: DeckOptions;
  private pages: PageState[] = [];
  private current = 0;
  private busy = false;
  private drag: DragState | null = null;

  constructor(host: HTMLElement, options: DeckOptions) {
    this.options = options;
    this.root = el('div', 'deck');
    this.stack = el('div', 'deck-stack');
    this.progress = el('div', 'deck-progress');
    const close = el('button', 'deck-close');
    close.type = 'button';
    close.setAttribute('aria-label', '关闭年度报告');
    close.dataset.gesture = 'own';
    const closeIcon = svgRoot(40, 40);
    closeIcon.append(
      pen.circle(20, 20, 30, { seed: 11, stroke: 'ink-soft', strokeWidth: 1.6, roughness: 0.9 }),
      pen.line(14, 14, 26, 26, { seed: 12, stroke: 'ink', strokeWidth: 2.2, roughness: 0.6, multiStroke: false }),
      pen.line(26, 14, 14, 26, { seed: 13, stroke: 'ink', strokeWidth: 2.2, roughness: 0.6, multiStroke: false }),
    );
    close.append(closeIcon);
    close.addEventListener('click', () => this.options.onClose());
    this.root.append(this.stack, this.progress, close);
    host.append(this.root);
    this.bindGestures();
    this.bindKeys();
  }

  get length(): number {
    return this.pages.length;
  }

  append(specs: SceneSpec[]): void {
    for (const spec of specs) {
      this.pages.push({ spec, element: null, handle: null, revealed: false, promptEl: null, nextEl: null, shadowEl: null });
    }
    this.renderProgress();
    this.ensureBuilt(this.current + 1);
  }

  start(): void {
    const state = this.ensureBuilt(0);
    if (state === null || state.element === null || state.handle === null) throw new Error('首页构建失败');
    state.element.classList.add('is-current');
    this.renderProgress();
    this.ensureBuilt(1);
    state.handle.enter();
  }

  next(): void {
    if (this.busy) return;
    const state = this.pages[this.current];
    if (state === undefined) return;
    if (!state.revealed) {
      this.blocked();
      return;
    }
    if (this.current >= this.pages.length - 1) return;
    void this.flipForward(0);
  }

  prev(): void {
    if (this.busy || this.current === 0) return;
    void this.flipBackward(FLIP_END);
  }

  /** 直接回到某一页，用于海报页的"再看一遍"。 */
  jumpTo(index: number): void {
    if (this.busy || index === this.current) return;
    const from = this.pages[this.current];
    const to = this.ensureBuilt(index);
    if (from === undefined || from.element === null || from.handle === null) return;
    if (to === null || to.element === null || to.handle === null) return;
    this.busy = true;
    from.handle.leave();
    const target = to.element;
    target.classList.add('is-current');
    target.style.zIndex = '30';
    void play(target, [{ opacity: 0, transform: 'scale(1.04)' }, { opacity: 1, transform: 'scale(1)' }], {
      duration: 520,
      easing: EASE.outExpo,
    }).then(() => {
      from.element?.classList.remove('is-current');
      target.style.zIndex = '';
      this.current = index;
      this.busy = false;
      this.renderProgress();
      this.ensureBuilt(index + 1);
      to.handle?.enter();
    });
  }

  /** 越界返回 null：翻到首页之前或末页之后都没有页面可建。 */
  private ensureBuilt(index: number): PageState | null {
    const state = this.pages[index];
    if (state === undefined) return null;
    if (state.element !== null) return state;
    const page = el('section', `page surface-${state.spec.surface}`);
    page.dataset.scene = state.spec.id;
    const body = el('div', 'page-body');
    const cue = el('footer', 'page-cue');
    const promptEl = el('div', 'cue-prompt');
    const nextEl = el('div', 'cue-next');
    const nextIcon = svgRoot(28, 28);
    nextIcon.append(
      pen.polyline([[7, 17], [14, 9], [21, 17]], { seed: 21, stroke: 'ink-soft', strokeWidth: 2, roughness: 0.6, multiStroke: false }),
      pen.polyline([[9, 23], [14, 18], [19, 23]], { seed: 22, stroke: 'pencil', strokeWidth: 1.6, roughness: 0.6, multiStroke: false }),
    );
    nextEl.append(nextIcon, el('span', '', [state.spec.nextLabel ?? '上滑继续']));
    cue.append(promptEl, nextEl);
    const shade = el('div', 'page-shade');
    const underShadow = el('div', 'page-under-shadow');
    page.append(body, cue, shade, underShadow);
    this.stack.append(page);
    state.element = page;
    state.promptEl = promptEl;
    state.nextEl = nextEl;
    state.shadowEl = underShadow;
    const context: SceneContext = {
      page,
      body,
      index,
      reveal: () => this.markRevealed(state),
      isRevealed: () => state.revealed,
      prompt: (text, icon) => {
        promptEl.replaceChildren(promptIcon(icon, index * 31 + 7), el('span', '', [text]));
      },
      haptic: (kind) => this.options.haptic(kind),
      goNext: () => this.next(),
    };
    state.handle = state.spec.build(context);
    return state;
  }

  private markRevealed(state: PageState): void {
    if (state.revealed) return;
    state.revealed = true;
    state.element?.classList.add('is-revealed');
    if (state.nextEl !== null) {
      void play(state.nextEl, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], {
        duration: 420,
        delay: 260,
        easing: EASE.outExpo,
      });
    }
    this.renderProgress();
  }

  private blocked(): void {
    const state = this.pages[this.current];
    if (state === undefined || state.handle === null) return;
    this.options.haptic('tick');
    if (state.promptEl !== null) void shake(state.promptEl, 6);
    state.handle.nudge();
  }

  private renderProgress(): void {
    const segments: HTMLElement[] = [];
    this.pages.forEach((state, index) => {
      const segment = el('span', 'seg');
      if (index < this.current || (index === this.current && state.revealed)) segment.classList.add('done');
      if (index === this.current) segment.classList.add('now');
      segment.style.setProperty('--tilt', `${((index * 37) % 7) - 3}deg`);
      segments.push(segment);
    });
    this.progress.replaceChildren(...segments);
  }

  private setAngle(page: HTMLElement, angle: number): void {
    page.style.transform = `rotateX(${-angle}deg)`;
    page.style.setProperty('--turn', String(angle / FLIP_END));
  }

  private async flipForward(fromAngle: number): Promise<void> {
    const from = this.pages[this.current];
    const to = this.ensureBuilt(this.current + 1);
    if (from === undefined || from.element === null || from.handle === null) return;
    if (to === null || to.element === null || to.handle === null || to.shadowEl === null) return;
    this.busy = true;
    const page = from.element;
    const under = to.element;
    const shadow = to.shadowEl;
    under.classList.add('is-under');
    page.classList.add('is-turning');
    this.options.haptic('tap');
    const remaining = (FLIP_END - fromAngle) / FLIP_END;
    const duration = prefersReducedMotion() ? 0 : 260 + 420 * remaining;
    await Promise.all([
      play(page, [{ transform: `rotateX(${-fromAngle}deg)` }, { transform: `rotateX(${-FLIP_END}deg)` }], {
        duration,
        easing: fromAngle > 0 ? EASE.out : 'cubic-bezier(.5,0,.3,1)',
      }),
      play(shadow, [{ opacity: 1 - fromAngle / FLIP_END }, { opacity: 0 }], {
        duration,
        easing: 'linear',
      }),
    ]);
    from.handle.leave();
    page.classList.remove('is-current', 'is-turning');
    page.style.transform = '';
    page.style.removeProperty('--turn');
    under.classList.remove('is-under');
    under.classList.add('is-current');
    this.current += 1;
    this.busy = false;
    this.renderProgress();
    this.ensureBuilt(this.current + 1);
    to.handle.enter();
  }

  private async flipBackward(fromAngle: number): Promise<void> {
    const from = this.pages[this.current];
    const to = this.ensureBuilt(this.current - 1);
    if (from === undefined || from.element === null || from.handle === null) return;
    if (to === null || to.element === null || to.handle === null) return;
    this.busy = true;
    const page = to.element;
    page.classList.add('is-current', 'is-returning');
    const duration = prefersReducedMotion() ? 0 : 240 + 380 * (fromAngle / FLIP_END);
    this.options.haptic('tap');
    await play(page, [{ transform: `rotateX(${-fromAngle}deg)` }, { transform: 'rotateX(0deg)' }], {
      duration,
      easing: EASE.out,
    });
    from.handle.leave();
    from.element.classList.remove('is-current');
    page.classList.remove('is-returning');
    page.style.transform = '';
    this.current -= 1;
    this.busy = false;
    this.renderProgress();
    to.handle.enter();
  }

  private bindGestures(): void {
    this.root.addEventListener('pointerdown', (event) => {
      if (this.busy || this.drag !== null) return;
      const target = event.target;
      if (target instanceof Element && target.closest('[data-gesture="own"]') !== null) return;
      this.drag = {
        pointerId: event.pointerId,
        startX: event.clientX,
        startY: event.clientY,
        lastY: event.clientY,
        lastT: event.timeStamp,
        velocity: 0,
        mode: 'pending',
        angle: 0,
      };
    });
    this.root.addEventListener('pointermove', (event) => {
      const drag = this.drag;
      if (drag === null || drag.pointerId !== event.pointerId) return;
      const dx = event.clientX - drag.startX;
      const dy = event.clientY - drag.startY;
      const dt = Math.max(1, event.timeStamp - drag.lastT);
      drag.velocity = drag.velocity * 0.6 + ((event.clientY - drag.lastY) / dt) * 0.4;
      drag.lastY = event.clientY;
      drag.lastT = event.timeStamp;
      if (drag.mode === 'pending') {
        if (Math.abs(dy) < 10 || Math.abs(dy) < Math.abs(dx) * 1.2) return;
        if (dy < 0 && this.current < this.pages.length - 1) {
          drag.mode = 'next';
          this.ensureBuilt(this.current + 1)?.element?.classList.add('is-under');
          this.pages[this.current]?.element?.classList.add('is-turning');
        } else if (dy > 0 && this.current > 0) {
          drag.mode = 'prev';
          this.ensureBuilt(this.current - 1)?.element?.classList.add('is-current', 'is-returning');
        } else {
          drag.mode = 'none';
        }
        this.root.setPointerCapture(event.pointerId);
      }
      if (drag.mode === 'next') {
        const state = this.pages[this.current];
        if (state === undefined || state.element === null) return;
        let angle = Math.min(FLIP_END, Math.max(0, -dy * DRAG_ANGLE_PER_PX));
        if (!state.revealed) angle = BLOCKED_MAX_ANGLE * (1 - Math.exp(-angle / BLOCKED_MAX_ANGLE));
        drag.angle = angle;
        this.setAngle(state.element, angle);
        const shadow = this.pages[this.current + 1]?.shadowEl;
        if (shadow !== undefined && shadow !== null) shadow.style.opacity = String(1 - angle / FLIP_END);
      } else if (drag.mode === 'prev') {
        const page = this.pages[this.current - 1]?.element;
        if (page === undefined || page === null) return;
        const angle = Math.min(FLIP_END, Math.max(0, FLIP_END - dy * DRAG_ANGLE_PER_PX));
        drag.angle = angle;
        this.setAngle(page, angle);
      }
    });
    const finish = (event: PointerEvent) => {
      const drag = this.drag;
      if (drag === null || drag.pointerId !== event.pointerId) return;
      this.drag = null;
      if (drag.mode === 'next') {
        const state = this.pages[this.current];
        if (state === undefined || state.element === null) return;
        const page = state.element;
        const shadow = this.pages[this.current + 1]?.shadowEl;
        if (shadow !== undefined && shadow !== null) shadow.style.opacity = '';
        const commit = state.revealed && (drag.angle > 26 || drag.velocity < -0.55);
        if (commit) {
          page.style.transform = '';
          void this.flipForward(drag.angle);
        } else {
          if (!state.revealed) this.blocked();
          void play(page, [{ transform: `rotateX(${-drag.angle}deg)` }, { transform: 'rotateX(0deg)' }], {
            duration: 360,
            easing: EASE.outBack,
          }).then(() => {
            page.style.transform = '';
            page.classList.remove('is-turning');
            this.pages[this.current + 1]?.element?.classList.remove('is-under');
          });
        }
      } else if (drag.mode === 'prev') {
        const page = this.pages[this.current - 1]?.element;
        if (page === undefined || page === null) return;
        const commit = drag.angle < 70 || drag.velocity > 0.55;
        page.style.transform = '';
        if (commit) {
          void this.flipBackward(drag.angle);
        } else {
          void play(page, [{ transform: `rotateX(${-drag.angle}deg)` }, { transform: `rotateX(${-FLIP_END}deg)` }], {
            duration: 260,
            easing: EASE.out,
          }).then(() => {
            page.classList.remove('is-current', 'is-returning');
            page.style.transform = '';
          });
        }
      }
    };
    this.root.addEventListener('pointerup', finish);
    this.root.addEventListener('pointercancel', finish);
  }

  private bindKeys(): void {
    window.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowDown' || event.key === 'PageDown' || event.key === ' ') {
        event.preventDefault();
        this.next();
      } else if (event.key === 'ArrowUp' || event.key === 'PageUp') {
        event.preventDefault();
        this.prev();
      }
    });
  }
}
