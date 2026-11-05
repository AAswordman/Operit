import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, durationParts, groupDigits, localDate } from '../data/format.ts';
import type { UserReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, popIn, shake, wait } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { pen, type Ink } from '../sketch/pen.ts';
import { el, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';
import { stickyNote } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const TOOL_INKS: Ink[] = ['coral', 'lavender', 'mint', 'sky', 'yellow'];

/** 五种工具小图标，按名次轮流分配，只是装饰。 */
function toolIcon(kind: number, seed: number): SVGSVGElement {
  const icon = svgRoot(34, 34, 'tool-icon');
  const style = { seed, stroke: 'ink' as Ink, strokeWidth: 1.8, roughness: 0.6, multiStroke: false };
  switch (kind % 5) {
    case 0:
      icon.append(pen.line(9, 26, 22, 13, style), pen.circle(24, 10, 10, style));
      break;
    case 1:
      icon.append(pen.line(10, 28, 20, 14, style), pen.rect(15, 5, 16, 9, { ...style, fill: 'coral', fillStyle: 'solid' }));
      break;
    case 2:
      icon.append(pen.circle(14, 14, 15, style), pen.line(19, 19, 28, 28, { ...style, strokeWidth: 2.6 }));
      break;
    case 3:
      icon.append(pen.line(17, 4, 17, 20, { ...style, strokeWidth: 3 }), pen.polygon([[12, 20], [22, 20], [19, 30], [15, 30]], { ...style, fill: 'lavender', fillStyle: 'solid' }));
      break;
    default:
      icon.append(pen.circle(10, 24, 9, style), pen.circle(24, 24, 9, style), pen.line(13, 19, 26, 4, style), pen.line(21, 19, 8, 4, style));
  }
  return icon;
}

export function toolsScene(user: UserReport, kicker: string): SceneSpec {
  const tools = user.tools;
  const max = Math.max(1, ...tools.top.map((tool) => tool.count));
  return {
    id: 'tools',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: 'AI 替你{{动手}}了', seed: 3701 });
      const box = el('button', 'toolbox tappable');
      box.type = 'button';
      box.setAttribute('aria-label', '打开工具箱');
      const art = svgRoot(220, 120, 'toolbox-art');
      const lid = svgEl('g', { class: 'toolbox-lid' });
      lid.append(
        pen.rect(20, 34, 180, 26, { seed: 3702, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'hachure', hachureGap: 5, roughness: 0.9 }),
        pen.rect(84, 14, 52, 20, { seed: 3703, stroke: 'ink', strokeWidth: 2.2, roughness: 0.8 }),
      );
      lid.style.transformOrigin = '20px 60px';
      art.append(
        pen.rect(20, 58, 180, 58, { seed: 3704, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'cross-hatch', hachureGap: 7, roughness: 0.9 }),
        pen.rect(98, 54, 24, 14, { seed: 3705, stroke: 'ink', strokeWidth: 1.8, fill: 'yellow', fillStyle: 'solid', roughness: 0.6 }),
        lid,
      );
      box.append(art);

      const totalLine = el('div', 'tools-total');
      const total = written(groupDigits(tools.total), { size: 48, seed: 3710, ink: 'ink', unit: '次工具调用' });
      totalLine.append(total.el);
      totalLine.style.visibility = 'hidden';
      const list = el('ol', 'tool-list');
      const rows = tools.top.map((tool, index) => {
        const bar = el('span', 'tool-bar');
        bar.style.setProperty('--w', `${Math.max(8, (tool.count / max) * 100)}%`);
        bar.style.setProperty('--ink', `var(--${TOOL_INKS[index % TOOL_INKS.length]})`);
        const row = el('li', 'tool-row', [toolIcon(index, 3720 + index), el('span', 'tool-name', [tool.name]), bar, el('span', 'tool-count', [groupDigits(tool.count)])]);
        row.style.opacity = '0';
        list.append(row);
        return row;
      });
      frame.stage.append(box, totalLine, list);

      let opened = false;
      const open = async () => {
        opened = true;
        box.classList.remove('tappable');
        ctx.haptic('heavy');
        await play(lid, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(-28deg) translateY(-6px)', offset: 0.6 }, { transform: 'rotate(-24deg)' }], { duration: 520, easing: EASE.outBack });
        const rect = box.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height * 0.4, { count: 20, inks: ['coral', 'yellow', 'sky'] });
        await play(box, [{ transform: 'scale(1)' }, { transform: 'scale(.7)' }], { duration: 420, easing: EASE.out });
        totalLine.style.visibility = 'visible';
        await total.write(0, 1.3);
        for (const [index, row] of rows.entries()) {
          await wait(80);
          void play(row, [{ opacity: 0, transform: 'translateX(-20px) rotate(-2deg)' }, { opacity: 1, transform: 'translateX(0) rotate(0)' }], { duration: 420, easing: EASE.outBack });
          if (index === 0) ctx.haptic('tap');
        }
        await wait(500);
        ctx.haptic('success');
        ctx.reveal();
      };
      onTap(box, () => {
        if (!opened) void open();
      }, { once: true });

      ctx.prompt('打开工具箱', 'open');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(box, [{ opacity: 0, transform: 'translateY(30px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 560, delay: 260, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!opened) void shake(box, 6);
        },
      };
    },
  };
}

/** AI 工作时长：把沙漏翻过来，沙子落下时揭晓。 */
export function aiTimeScene(user: UserReport, kicker: string): SceneSpec {
  const work = user.aiWork;
  const parts = durationParts(work.durationMs);
  return {
    id: 'ai-time',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '这一年，AI 为你\n{{埋头工作}}了', seed: 3801 });
      const glass = el('button', 'hourglass tappable');
      glass.type = 'button';
      glass.setAttribute('aria-label', '翻转沙漏');
      const art = svgRoot(140, 190, 'hourglass-art');
      const sandTop = svgEl('g', { class: 'sand-top' });
      sandTop.append(pen.polygon([[38, 34], [102, 34], [70, 86]], { seed: 3802, stroke: 'none', fill: 'yellow', fillStyle: 'solid', roughness: 0.8 }));
      sandTop.style.opacity = '0';
      const sandBottom = svgEl('g', { class: 'sand-bottom' });
      sandBottom.append(pen.polygon([[34, 164], [106, 164], [70, 120]], { seed: 3803, stroke: 'none', fill: 'yellow', fillStyle: 'solid', roughness: 0.8 }));
      // 沙漏翻转 180 度后，原来下半部的沙子到了上面，沙流在原坐标里从瓶颈指向原来的上半部。
      const stream = svgEl('line', { x1: 70, y1: 96, x2: 70, y2: 40, class: 'sand-stream' });
      art.append(
        sandTop,
        sandBottom,
        stream,
        pen.path('M 30 20 C 30 70, 66 82, 66 95 C 66 108, 30 120, 30 170 L 110 170 C 110 120, 74 108, 74 95 C 74 82, 110 70, 110 20 Z', { seed: 3804, stroke: 'ink', strokeWidth: 2.2, roughness: 0.8 }),
        pen.rect(18, 8, 104, 14, { seed: 3805, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'hachure', hachureGap: 4, roughness: 0.7 }),
        pen.rect(18, 168, 104, 14, { seed: 3806, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'hachure', hachureGap: 4, roughness: 0.7 }),
      );
      glass.append(art);

      const result = el('div', 'ai-time-result');
      const hours = written(groupDigits(parts.hours), { size: 64, seed: 3810, ink: 'ink', unit: '小时' });
      const minutes = written(String(parts.minutes), { size: 40, seed: 3811, ink: 'ink-soft', unit: '分钟' });
      result.append(el('div', 'ai-time-numbers', [hours.el, minutes.el]));
      if (work.coverageStart !== null) {
        const from = localDate(work.coverageStart);
        result.append(el('p', 'caption', [`从 ${from.month} 月 ${from.day} 日开始记录 AI 的等待与输出耗时`]));
      }
      result.style.visibility = 'hidden';
      const board = el('div', 'sticky-board compact');
      board.style.display = 'none';
      const note = stickyNote({
        color: 'yellow',
        seed: 3812,
        question: '最长的一次回复？',
        onFlip: () => {
          ctx.haptic('tap');
          ctx.reveal();
        },
        reveal: () => stickyContent(compactCount(work.longestReplyTokens), 'tokens 一口气写完', null, 3813, 24),
      });
      board.append(note.el);
      frame.stage.append(glass, result, board);

      let flipped = false;
      const flip = async () => {
        flipped = true;
        glass.classList.remove('tappable');
        ctx.haptic('heavy');
        await play(glass, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(200deg)', offset: 0.7 }, { transform: 'rotate(180deg)' }], { duration: 760, easing: EASE.out });
        glass.classList.add('is-flowing');
        void play(sandBottom, [{ transform: 'scaleY(1)' }, { transform: 'scaleY(.12)' }], { duration: 1600, easing: 'ease-in' });
        void play(sandTop, [{ opacity: 1, transform: 'scaleY(.1)' }, { opacity: 1, transform: 'scaleY(1)' }], { duration: 1600, easing: 'ease-out' });
        result.style.visibility = 'visible';
        await hours.write(200, 1);
        await minutes.write(0, 1.4);
        glass.classList.remove('is-flowing');
        board.style.display = '';
        await popIn(note.el, 0, 0);
        ctx.prompt('翻开便利贴', 'tap');
      };
      onTap(glass, () => {
        if (!flipped) void flip();
      }, { once: true });

      ctx.prompt('把沙漏翻过来', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(glass, [{ opacity: 0, transform: 'translateY(20px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 520, delay: 260, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!flipped) void shake(glass, 6);
          else void shake(note.el, 4);
        },
      };
    },
  };
}

interface Jar {
  label: string;
  value: number;
  ink: Ink;
}

/** 三个罐子：记忆、角色卡、工作流。点一下，纸片落进罐子。 */
export function creationsScene(user: UserReport, kicker: string): SceneSpec {
  const jars: Jar[] = [
    { label: '新记忆', value: user.creations.memories, ink: 'lavender' },
    { label: '新角色卡', value: user.creations.characterCards, ink: 'coral' },
    { label: '新工作流', value: user.creations.workflows, ink: 'mint' },
  ];
  return {
    id: 'creations',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '你还亲手{{攒下}}了', seed: 3901 });
      frame.stage.classList.add('stage-centered');
      const shelf = el('div', 'jar-shelf');
      let filled = 0;
      const items = jars.map((jar, index) => {
        const button = el('button', 'jar tappable');
        button.type = 'button';
        button.setAttribute('aria-label', jar.label);
        const art = svgRoot(96, 128, 'jar-art');
        const fill = svgEl('g', { class: 'jar-fill' });
        fill.append(pen.rect(16, 52, 64, 64, { seed: 3910 + index, stroke: 'none', fill: jar.ink, fillStyle: 'zigzag', hachureGap: 4, fillWeight: 1.6, roughness: 1 }));
        fill.style.transformOrigin = '48px 116px';
        fill.style.transform = 'scaleY(0)';
        art.append(
          fill,
          pen.path('M 22 30 C 10 40, 10 50, 12 70 C 14 100, 16 118, 30 120 L 66 120 C 80 118, 82 100, 84 70 C 86 50, 86 40, 74 30 Z', { seed: 3920 + index, stroke: 'ink', strokeWidth: 2, roughness: 0.8 }),
          pen.rect(20, 14, 56, 16, { seed: 3925 + index, stroke: 'ink', strokeWidth: 2, fill: jar.ink, fillStyle: 'solid', roughness: 0.7 }),
        );
        const number = written(groupDigits(jar.value), { size: 32, seed: 3930 + index, ink: 'ink', tracking: 2 });
        number.el.style.visibility = 'hidden';
        button.append(art, number.el, el('span', 'jar-label', [jar.label]));
        onTap(button, () => {
          if (button.classList.contains('is-filled')) return;
          button.classList.add('is-filled');
          button.classList.remove('tappable');
          ctx.haptic('tap');
          const rect = button.getBoundingClientRect();
          burst(rect.left + rect.width / 2, rect.top + 10, { count: 14, inks: [jar.ink, 'yellow'], direction: Math.PI / 2, spread: 0.8, speed: 0.25, gravity: 0.0009 });
          void play(fill, [{ transform: 'scaleY(0)' }, { transform: `scaleY(${jar.value === 0 ? 0.04 : 1})` }], { duration: 900, delay: 200, easing: EASE.outExpo });
          number.el.style.visibility = 'visible';
          void number.write(300, 1.3).then(() => {
            filled += 1;
            if (filled === jars.length) {
              ctx.haptic('success');
              ctx.reveal();
            }
          });
        }, { once: true });
        shelf.append(button);
        return button;
      });
      const note = el('p', 'caption creations-note', ['都是今年新建的']);
      frame.stage.append(shelf, note);

      ctx.prompt('点一点每个罐子', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          items.forEach((item, index) => void popIn(item, 300 + index * 120, 0));
        },
        leave() {},
        nudge() {
          for (const item of items) if (!item.classList.contains('is-filled')) void shake(item, 5);
        },
      };
    },
  };
}
