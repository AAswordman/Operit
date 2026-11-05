import type { SceneSpec } from '../deck/deck.ts';
import type { UserReport } from '../data/types.ts';
import { holdToFill } from '../interact/hold.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import type { UserSource } from './index.ts';

const PRINT = 150;

/** 一圈圈不闭合的弧线，像指纹。按住时从里到外上色。 */
function fingerprint(): { svg: SVGSVGElement; rings: SVGGElement[] } {
  const svg = svgRoot(PRINT, PRINT, 'fingerprint');
  const rings: SVGGElement[] = [];
  const cx = PRINT / 2;
  const cy = PRINT / 2 + 4;
  for (let index = 0; index < 8; index += 1) {
    const radius = 10 + index * 8.4;
    const gap = 0.5 + (index % 3) * 0.18;
    const start = -Math.PI / 2 + gap + index * 0.21;
    const end = start + Math.PI * 2 - gap * 2;
    const ring = svgEl('g', { class: 'print-ring' });
    ring.append(pen.arc(cx, cy, radius * 2, radius * 2.2, start, end, false, { seed: 2100 + index, stroke: 'pencil', strokeWidth: 2.4, roughness: 0.7, multiStroke: false }));
    rings.push(ring);
    svg.append(ring);
  }
  return { svg, rings };
}

export function interludeScene(user: UserSource): SceneSpec {
  return {
    id: 'interlude',
    surface: 'paper',
    nextLabel: '上滑，看看你的这一年',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '翻到下一章', title: '接下来，\n是{{只属于你}}的部分', seed: 2101 });
      const lead = el('p', 'lead interlude-lead', ['下面的每一个数字，都只在你的手机里生成，不会上传到任何地方。']);
      const print = fingerprint();
      const pad = el('button', 'print-pad', [print.svg]);
      pad.type = 'button';
      pad.setAttribute('aria-label', '按住解锁');
      const status = el('p', 'caption interlude-status', ['']);
      const retry = el('button', 'ink-button', ['再试一次']);
      retry.type = 'button';
      retry.style.display = 'none';
      frame.stage.append(lead, el('div', 'print-zone', [pad, status, retry]));

      let unlocked = false;
      let ready: UserReport | null = null;
      let failure: string | null = null;
      let appended = false;

      const tryReveal = () => {
        if (!unlocked) return;
        if (failure !== null) {
          status.textContent = `没能读出你的数据：${failure}`;
          retry.style.display = '';
          return;
        }
        if (ready === null) {
          status.textContent = '正在翻阅你的这一年……';
          status.classList.add('is-busy');
          return;
        }
        status.classList.remove('is-busy');
        status.textContent = '解锁成功';
        ctx.haptic('success');
        ctx.reveal();
      };

      const watch = (request: Promise<UserReport>) => {
        failure = null;
        request.then(
          (report) => {
            ready = report;
            if (!appended) {
              appended = true;
              user.onReady(report);
            }
            tryReveal();
          },
          (error: unknown) => {
            console.error('[interlude] 用户数据加载失败', error);
            failure = error instanceof Error ? error.message : String(error);
            tryReveal();
          },
        );
      };
      watch(user.current());

      onTap(retry, () => {
        retry.style.display = 'none';
        status.textContent = '正在重新翻阅……';
        watch(user.retry());
      });

      holdToFill(pad, {
        duration: 1300,
        onProgress(progress) {
          const lit = Math.round(progress * print.rings.length);
          print.rings.forEach((ring, index) => ring.classList.toggle('is-lit', index < lit));
        },
        onMilestone() {
          ctx.haptic('tick');
        },
        onEarlyRelease() {
          void shake(pad, 5);
        },
        onComplete() {
          unlocked = true;
          const rect = pad.getBoundingClientRect();
          burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 26, inks: ['lavender', 'mint', 'yellow'] });
          void play(pad, [{ transform: 'scale(1)' }, { transform: 'scale(1.12)' }, { transform: 'scale(1)' }], { duration: 420, easing: EASE.outBack });
          tryReveal();
        },
      });

      ctx.prompt('按住指纹', 'hold');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(lead, [{ opacity: 0 }, { opacity: 1 }], { duration: 600, delay: 500, easing: 'ease-out' });
        },
        leave() {},
        nudge() {
          void shake(pad, 6);
        },
      };
    },
  };
}
