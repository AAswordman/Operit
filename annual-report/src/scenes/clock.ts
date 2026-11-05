import type { SceneSpec } from '../deck/deck.ts';
import { groupDigits, localTime, pad2 } from '../data/format.ts';
import type { UserReport } from '../data/types.ts';
import { onDrag } from '../interact/drag.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { artText, prepareStamp, stampIn } from '../sketch/artText.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';
import { stickyNote } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const SIZE = 300;
const C = SIZE / 2;
const INNER = 62;
const PETAL_MAX = 72;

/** 按最常出现的小时给一个称号，海报页也会用到。 */
export function hourPersona(hour: number): { name: string; line: string } {
  if (hour < 5) return { name: '深夜哲学家', line: '凌晨的你，总有想不完的问题' };
  if (hour < 9) return { name: '晨光探索者', line: '天刚亮，你就来找我了' };
  if (hour < 12) return { name: '上午实干家', line: '一天里最清醒的时候，你在做事' };
  if (hour < 14) return { name: '午间充电员', line: '午饭前后，是我们的固定时间' };
  if (hour < 18) return { name: '午后漫游者', line: '下午的时光，适合慢慢聊' };
  if (hour < 21) return { name: '黄昏诗人', line: '下班路上，晚饭之后' };
  return { name: '夜猫子', line: '夜深了，你还在和我说话' };
}

export function peakHour(byHour: number[]): number {
  let best = 0;
  byHour.forEach((value, hour) => {
    if (value > byHour[best]) best = hour;
  });
  return best;
}

export function clockScene(user: UserReport, kicker: string): SceneSpec {
  const byHour = user.hours.byHour;
  const max = Math.max(1, ...byHour);
  const peak = peakHour(byHour);
  const persona = hourPersona(peak);
  return {
    id: 'clock',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '你通常在{{什么时候}}\n来找我？', seed: 3401 });
      const dial = svgRoot(SIZE, SIZE, 'dial');
      dial.append(
        pen.circle(C, C, SIZE - 30, { seed: 3402, stroke: 'ink', strokeWidth: 2, roughness: 1 }),
        pen.circle(C, C, INNER * 2, { seed: 3403, stroke: 'pencil', strokeWidth: 1.2, roughness: 1.2 }),
      );
      for (let hour = 0; hour < 24; hour += 1) {
        const angle = (hour / 24) * Math.PI * 2 - Math.PI / 2;
        const outer = SIZE / 2 - 15;
        const inner = hour % 6 === 0 ? outer - 12 : outer - 6;
        dial.append(pen.line(C + Math.cos(angle) * inner, C + Math.sin(angle) * inner, C + Math.cos(angle) * outer, C + Math.sin(angle) * outer, { seed: 3410 + hour, stroke: 'ink-soft', strokeWidth: hour % 6 === 0 ? 2 : 1.1, roughness: 0.5, multiStroke: false }));
        if (hour % 6 === 0) {
          const label = svgEl('text', { x: C + Math.cos(angle) * (outer - 26), y: C + Math.sin(angle) * (outer - 26) + 4, class: 'dial-label' });
          label.textContent = String(hour);
          dial.append(label);
        }
      }
      const petals: SVGGElement[] = byHour.map((value, hour) => {
        const angle = (hour / 24) * Math.PI * 2 - Math.PI / 2;
        const length = 6 + (PETAL_MAX * value) / max;
        const width = 9;
        const x0 = C + Math.cos(angle) * (INNER + 2);
        const y0 = C + Math.sin(angle) * (INNER + 2);
        const x1 = C + Math.cos(angle) * (INNER + 2 + length);
        const y1 = C + Math.sin(angle) * (INNER + 2 + length);
        const nx = -Math.sin(angle) * (width / 2);
        const ny = Math.cos(angle) * (width / 2);
        const petal = pen.polygon([[x0 + nx, y0 + ny], [x1 + nx * 0.6, y1 + ny * 0.6], [x1 - nx * 0.6, y1 - ny * 0.6], [x0 - nx, y0 - ny]], {
          seed: 3440 + hour,
          stroke: 'ink',
          strokeWidth: 1.1,
          fill: hour === peak ? 'coral' : hour < 6 || hour >= 21 ? 'lavender' : 'yellow',
          fillStyle: 'hachure',
          hachureGap: 3,
          fillWeight: 1.2,
          roughness: 0.8,
        });
        petal.style.transformOrigin = `${x0}px ${y0}px`;
        petal.style.opacity = '0';
        dial.append(petal);
        return petal;
      });
      const hand = svgEl('g', { class: 'dial-hand' });
      hand.append(
        pen.line(C, C, C, C - (INNER + PETAL_MAX + 6), { seed: 3470, stroke: 'ink', strokeWidth: 3, roughness: 0.5, multiStroke: false }),
        pen.polygon([[C - 7, C - INNER - PETAL_MAX + 2], [C, C - INNER - PETAL_MAX - 12], [C + 7, C - INNER - PETAL_MAX + 2]], { seed: 3471, stroke: 'ink', strokeWidth: 1.6, fill: 'coral', fillStyle: 'solid', roughness: 0.5 }),
        pen.circle(C, C, 16, { seed: 3472, stroke: 'ink', strokeWidth: 2, fill: 'yellow', fillStyle: 'solid', roughness: 0.6 }),
      );
      hand.style.transformOrigin = `${C}px ${C}px`;
      dial.append(hand);
      const center = el('div', 'dial-center');
      const peakText = written(pad2(peak), { size: 40, seed: 3480, ink: 'coral', unit: '点' });
      center.append(peakText.el);
      center.style.visibility = 'hidden';
      const dialWrap = el('div', 'dial-wrap', [dial, center]);

      const personaBox = el('div', 'persona');
      const personaName = artText(`{{${persona.name}}}`, { seed: 3490, className: 'persona-name' });
      const personaLine = el('p', 'lead persona-line', [persona.line, el('br'), `你最常在 ${peak} 点出现，那个小时你说了 ${groupDigits(byHour[peak])} 句话`]);
      personaBox.append(personaName, personaLine);
      personaBox.style.visibility = 'hidden';
      prepareStamp(personaName);
      personaLine.style.opacity = '0';

      const board = el('div', 'sticky-board compact');
      board.style.display = 'none';
      const latest = user.hours.latestNight;
      let latestNote: HTMLElement | null = null;
      if (latest !== null) {
        const time = localTime(latest);
        latestNote = stickyNote({
            color: 'lavender',
            seed: 3495,
            question: '最晚的一次？',
            onFlip: () => {
              ctx.haptic('tap');
              ctx.reveal();
            },
            reveal: () => stickyContent(`${time.hour}:${pad2(time.minute)}`, '凌晨还在聊', null, 3496),
          }).el;
        board.append(latestNote);
      }
      frame.stage.append(dialWrap, personaBox, board);

      const visited = new Set<number>();
      let angleNow = 0;
      let completed = false;
      const bloom = (hour: number) => {
        if (visited.has(hour)) return;
        visited.add(hour);
        const petal = petals[hour];
        if (petal === undefined) return;
        void play(petal, [{ opacity: 0, transform: 'scale(0)' }, { opacity: 1, transform: 'scale(1.15)', offset: 0.7 }, { opacity: 1, transform: 'scale(1)' }], { duration: 380, easing: EASE.out });
        if (hour % 6 === 0) ctx.haptic('tick');
        if (visited.size === 24) void complete();
      };
      const complete = async () => {
        completed = true;
        ctx.haptic('success');
        const target = (peak / 24) * 360;
        await play(hand, [{ transform: `rotate(${angleNow}deg)` }, { transform: `rotate(${target + 360 * Math.round((angleNow - target) / 360)}deg)` }], { duration: 700, easing: EASE.outBack });
        const rect = dial.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 24 });
        center.style.visibility = 'visible';
        await peakText.write(0, 1.3);
        personaBox.style.visibility = 'visible';
        await stampIn(personaName, 0, 60);
        await play(personaLine, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: EASE.outExpo });
        if (latestNote === null) {
          ctx.reveal();
        } else {
          board.style.display = '';
          await popIn(latestNote, 0, 0);
          ctx.prompt('翻开便利贴', 'tap');
        }
      };

      onDrag(dialWrap, {
        onMove(point) {
          if (completed) return;
          const box = dialWrap.getBoundingClientRect();
          const dx = point.x - box.width / 2;
          const dy = point.y - box.height / 2;
          if (dx * dx + dy * dy < 20 * 20) return;
          let degrees = (Math.atan2(dy, dx) * 180) / Math.PI + 90;
          if (degrees < 0) degrees += 360;
          // 指针每次移动的角度变化取最短方向，累计到连续角度上，避免越过 0 点时跳一圈。
          const current = ((angleNow % 360) + 360) % 360;
          let delta = degrees - current;
          if (delta > 180) delta -= 360;
          if (delta < -180) delta += 360;
          const steps = Math.ceil(Math.abs(delta) / 7.5);
          for (let index = 1; index <= steps; index += 1) {
            const sample = angleNow + (delta * index) / steps;
            bloom(((Math.floor((((sample % 360) + 360) % 360) / 15)) + 24) % 24);
          }
          angleNow += delta;
          hand.style.transform = `rotate(${angleNow}deg)`;
        },
      });

      ctx.prompt('拨动指针转一圈', 'rotate');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(dialWrap, [{ opacity: 0, transform: 'scale(.9) rotate(-8deg)' }, { opacity: 1, transform: 'scale(1) rotate(0)' }], { duration: 640, delay: 260, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!completed) void shake(dialWrap, 5);
          else void shake(board, 4);
        },
      };
    },
  };
}
