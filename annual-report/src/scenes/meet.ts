import type { SceneSpec } from '../deck/deck.ts';
import { daysBetween, groupDigits, localDate } from '../data/format.ts';
import type { UserReport } from '../data/types.ts';
import { onDrag } from '../interact/drag.ts';
import { EASE, play, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { handText, hideHand, writeHand } from '../sketch/handwriting.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

export function meetScene(user: UserReport, kicker: string): SceneSpec {
  const install = localDate(user.install.firstInstallAt);
  const end = localDate(user.window.end);
  const together = daysBetween(install, end) + 1;
  const weekday = WEEKDAYS[new Date(Date.UTC(install.year, install.month - 1, install.day)).getUTCDay()];
  const sameYear = install.year === user.year;
  return {
    id: 'meet',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '在这台手机上，\n我们{{相识于}}', seed: 3001 });
      const calendar = el('div', 'desk-calendar');
      const rings = svgRoot(220, 28, 'cal-rings');
      for (const x of [56, 164]) {
        rings.append(pen.ellipse(x, 14, 14, 26, { seed: 3002 + x, stroke: 'ink', strokeWidth: 2.4, roughness: 0.6 }));
      }
      const under = el('div', 'cal-page cal-under');
      const monthDay = handText(`${install.month}月${install.day}日`, { size: 44, seed: 3010, ink: 'coral', tracking: 4 });
      const yearText = el('div', 'cal-year', [`${install.year} · ${weekday}`]);
      under.append(el('div', 'cal-head', [String(install.year)]), monthDay.svg, yearText);
      const top = el('div', 'cal-page cal-top');
      const mark = handText('?', { size: 70, seed: 3011, ink: 'pencil', ghost: false });
      top.append(el('div', 'cal-head', ['DAY ONE']), mark.svg, el('div', 'cal-hint', ['往下撕掉这一页']));
      calendar.append(under, top, rings);

      const summary = el('div', 'meet-summary');
      const days = written(groupDigits(together), { size: 56, seed: 3020, ink: 'ink', prefix: sameYear ? '今年相遇，已经' : '一起走过', unit: '天' });
      const note = el('p', 'lead meet-note', [
        sameYear ? `从 ${install.month} 月 ${install.day} 日第一次打开 Operit 开始` : `从 ${install.year} 年 ${install.month} 月 ${install.day} 日第一次打开 Operit 开始`,
      ]);
      note.style.opacity = '0';
      summary.append(days.el, note);
      summary.style.visibility = 'hidden';
      frame.stage.append(calendar, summary);
      hideHand(monthDay);

      let torn = false;
      const tear = async (fromY: number) => {
        torn = true;
        ctx.haptic('heavy');
        const rect = top.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + 10, { count: 16, inks: ['paper-deep', 'pencil', 'coral'], speed: 0.4 });
        await play(top, [
          { transform: `translateY(${fromY}px) rotate(${fromY * 0.03}deg)`, opacity: 1 },
          { transform: `translateY(${fromY + 420}px) translateX(60px) rotate(28deg)`, opacity: 0 },
        ], { duration: 680, easing: EASE.in });
        top.style.display = 'none';
        await writeHand(monthDay, 1.2);
        summary.style.visibility = 'visible';
        await days.write(0, 1.2);
        await play(note, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: EASE.outExpo });
        ctx.haptic('success');
        ctx.reveal();
      };

      onDrag(top, {
        onMove(point) {
          if (torn) return;
          const pull = Math.max(0, point.dy);
          top.style.transform = `translateY(${pull * 0.55}px) rotate(${pull * 0.035}deg)`;
          top.style.setProperty('--crease', String(Math.min(1, pull / 110)));
        },
        onEnd(point) {
          if (torn) return;
          if (point.dy > 100) {
            void tear(point.dy * 0.55);
          } else {
            void play(top, [{ transform: top.style.transform }, { transform: 'translateY(0) rotate(0)' }], { duration: 360, easing: EASE.outBack }).then(() => {
              top.style.transform = '';
            });
          }
        },
      });

      ctx.prompt('按住日历往下撕', 'drag');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(calendar, [{ opacity: 0, transform: 'translateY(18px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 620, delay: 260, easing: EASE.outExpo });
        },
        leave() {},
        nudge() {
          if (!torn) void shake(top, 6);
        },
      };
    },
  };
}
