import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, daysBetween, groupDigits, localDate, pad2 } from '../data/format.ts';
import type { DailyActivity, UserReport } from '../data/types.ts';
import { onDrag } from '../interact/drag.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst, inkColor } from '../motion/particles.ts';
import { onTick } from '../motion/ticker.ts';
import { createRandom } from '../sketch/random.ts';
import { el } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { stickyNote } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const CELL = 11;
const GAP = 2;
const MONTH_W = 7 * (CELL + GAP);
const MONTH_H = 14 + 6 * (CELL + GAP);
const COL_GAP = 16;
const ROW_GAP = 10;
const CANVAS_W = 3 * MONTH_W + 2 * COL_GAP;
const CANVAS_H = 4 * MONTH_H + 3 * ROW_GAP;
const BRUSH = 26;

interface Cell {
  key: string;
  x: number;
  y: number;
  level: number;
  inWindow: boolean;
  /** 这一天没有记录时为 undefined。 */
  day: DailyActivity | undefined;
  painted: boolean;
}

function level(messages: number, max: number): number {
  if (messages <= 0) return 0;
  const ratio = messages / max;
  if (ratio > 0.66) return 4;
  if (ratio > 0.36) return 3;
  if (ratio > 0.14) return 2;
  return 1;
}

export function calendarScene(user: UserReport, kicker: string): SceneSpec {
  const year = user.year;
  const end = localDate(user.window.end);
  const byDate = new Map(user.activity.days.map((day) => [day.date, day]));
  const maxMessages = Math.max(1, ...user.activity.days.map((day) => day.messages));
  const busiest = user.activity.busiestDay;
  return {
    id: 'calendar',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '你的 {{' + year + '}}，\n一格一格涂满', seed: 3301 });
      const ratio = window.devicePixelRatio;
      const canvas = document.createElement('canvas');
      canvas.className = 'calendar-canvas';
      canvas.width = Math.round(CANVAS_W * ratio);
      canvas.height = Math.round(CANVAS_H * ratio);
      canvas.style.width = `${CANVAS_W}px`;
      canvas.style.height = `${CANVAS_H}px`;
      const context = canvas.getContext('2d');
      if (context === null) throw new Error('无法创建日历画布');
      const ctx2d = context;
      ctx2d.scale(ratio, ratio);

      const cells: Cell[] = [];
      for (let month = 0; month < 12; month += 1) {
        const originX = (month % 3) * (MONTH_W + COL_GAP);
        const originY = Math.floor(month / 3) * (MONTH_H + ROW_GAP);
        const firstWeekday = (new Date(Date.UTC(year, month, 1)).getUTCDay() + 6) % 7;
        const days = daysBetween({ year, month: month + 1, day: 1 }, month === 11 ? { year: year + 1, month: 1, day: 1 } : { year, month: month + 2, day: 1 });
        for (let day = 1; day <= days; day += 1) {
          const slot = firstWeekday + day - 1;
          const key = `${year}-${pad2(month + 1)}-${pad2(day)}`;
          const activity = byDate.get(key);
          const inWindow = daysBetween({ year, month: month + 1, day }, end) >= 0;
          cells.push({
            key,
            x: originX + (slot % 7) * (CELL + GAP),
            y: originY + 14 + Math.floor(slot / 7) * (CELL + GAP),
            level: activity === undefined ? 0 : level(activity.messages, maxMessages),
            inWindow,
            day: activity,
            painted: false,
          });
        }
      }

      const colors = {
        pencil: inkColor('pencil'),
        lavender: inkColor('lavender'),
        coral: inkColor('coral'),
        ink: inkColor('ink-soft'),
      };

      const drawGrid = () => {
        ctx2d.clearRect(0, 0, CANVAS_W, CANVAS_H);
        ctx2d.font = '700 10px system-ui, sans-serif';
        ctx2d.fillStyle = colors.ink;
        for (let month = 0; month < 12; month += 1) {
          ctx2d.fillText(`${month + 1}月`, (month % 3) * (MONTH_W + COL_GAP), Math.floor(month / 3) * (MONTH_H + ROW_GAP) + 9);
        }
        const random = createRandom(3310);
        ctx2d.lineWidth = 0.8;
        ctx2d.strokeStyle = colors.pencil;
        for (const cell of cells) {
          ctx2d.globalAlpha = cell.inWindow ? 0.55 : 0.2;
          // 手抖的方格：四个角各偏一点点。
          const j = () => (random() - 0.5) * 1.4;
          ctx2d.beginPath();
          ctx2d.moveTo(cell.x + j(), cell.y + j());
          ctx2d.lineTo(cell.x + CELL + j(), cell.y + j());
          ctx2d.lineTo(cell.x + CELL + j(), cell.y + CELL + j());
          ctx2d.lineTo(cell.x + j(), cell.y + CELL + j());
          ctx2d.closePath();
          ctx2d.stroke();
        }
        ctx2d.globalAlpha = 1;
      };

      const paint = (cell: Cell) => {
        if (cell.painted || !cell.inWindow) return;
        cell.painted = true;
        if (cell.level === 0) return;
        const random = createRandom(cell.x * 31 + cell.y);
        const strokes = 1 + cell.level * 2;
        const color = busiest !== null && busiest.date === cell.key ? colors.coral : colors.lavender;
        ctx2d.strokeStyle = color;
        ctx2d.lineCap = 'round';
        ctx2d.lineWidth = 1.5;
        ctx2d.globalAlpha = 0.35 + cell.level * 0.15;
        // 用几道来回的斜线涂满格子，越活跃涂得越密。
        ctx2d.beginPath();
        for (let index = 0; index < strokes; index += 1) {
          const offset = (index / strokes) * (CELL + 2) - 1;
          ctx2d.moveTo(cell.x + offset + (random() - 0.5), cell.y + CELL + 0.5);
          ctx2d.lineTo(cell.x + offset + 4 + (random() - 0.5), cell.y - 0.5);
        }
        ctx2d.stroke();
        ctx2d.globalAlpha = 1;
      };

      const tooltip = el('div', 'day-tip');
      tooltip.style.display = 'none';
      const wrap = el('div', 'calendar-wrap', [canvas, tooltip]);
      const legend = el('div', 'calendar-legend', [el('span', 'legend-swatch'), el('span', 'caption', ['颜色越深，那天聊得越多'])]);
      legend.style.opacity = '0';

      const board = el('div', 'sticky-board compact');
      board.style.display = 'none';
      const notes: HTMLElement[] = [];
      let flipped = 0;
      const onFlip = () => {
        flipped += 1;
        ctx.haptic('tap');
        if (flipped === notes.length) ctx.reveal();
      };
      notes.push(
        stickyNote({
          color: 'mint',
          seed: 3320,
          question: '来了多少天？',
          onFlip,
          reveal: () => stickyContent(`${groupDigits(user.activity.activeDays)}天`, '今年和我聊过天', null, 3321),
        }).el,
      );
      const streak = user.activity.longestStreak;
      if (streak !== null) {
        const from = localDate(streak.start);
        const to = localDate(streak.end);
        notes.push(
          stickyNote({
            color: 'yellow',
            seed: 3322,
            question: '最长连续？',
            onFlip,
            reveal: () => stickyContent(`${groupDigits(streak.days)}天`, '一天都没落下', `${from.month}.${from.day} 到 ${to.month}.${to.day}`, 3323),
          }).el,
        );
      }
      if (busiest !== null) {
        const date = localDate(busiest.date);
        notes.push(
          stickyNote({
            color: 'pink',
            seed: 3324,
            question: '聊得最多那天？',
            onFlip,
            reveal: () => stickyContent(`${date.month}月${date.day}日`, `说了 ${groupDigits(busiest.messages)} 句`, null, 3325),
          }).el,
        );
      }
      board.append(...notes);
      frame.stage.append(wrap, legend, board);

      let completed = false;
      const windowCells = cells.filter((cell) => cell.inWindow);
      const complete = async () => {
        completed = true;
        ctx.haptic('success');
        // 剩下没涂到的格子按日期顺序快速补齐。
        const rest = windowCells.filter((cell) => !cell.painted);
        await new Promise<void>((resolve) => {
          let index = 0;
          const stop = onTick(() => {
            for (let batch = 0; batch < 9 && index < rest.length; batch += 1) {
              const cell = rest[index];
              paint(cell);
              index += 1;
            }
            if (index >= rest.length) {
              stop();
              resolve();
            }
          });
        });
        const rect = canvas.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 26 });
        void play(legend, [{ opacity: 0 }, { opacity: 1 }], { duration: 400, easing: 'ease-out' });
        board.style.display = '';
        await Promise.all(notes.map((note, index) => popIn(note, index * 110, 0)));
        ctx.prompt('翻开便利贴，也可以点日历上的某一天', 'tap');
      };

      let painted = 0;
      onDrag(canvas, {
        onMove(point) {
          if (completed) return;
          const scale = CANVAS_W / canvas.getBoundingClientRect().width;
          const x = point.x * scale;
          const y = point.y * scale;
          for (const cell of windowCells) {
            if (cell.painted) continue;
            const dx = cell.x + CELL / 2 - x;
            const dy = cell.y + CELL / 2 - y;
            if (dx * dx + dy * dy < BRUSH * BRUSH) {
              paint(cell);
              painted += 1;
            }
          }
          if (painted / windowCells.length > 0.32) void complete();
        },
      });

      canvas.addEventListener('click', (event) => {
        if (!completed) return;
        const rect = canvas.getBoundingClientRect();
        const scale = CANVAS_W / rect.width;
        const x = (event.clientX - rect.left) * scale;
        const y = (event.clientY - rect.top) * scale;
        const cell = cells.find((item) => x >= item.x - 1 && x <= item.x + CELL + 1 && y >= item.y - 1 && y <= item.y + CELL + 1);
        if (cell === undefined || !cell.inWindow) {
          tooltip.style.display = 'none';
          return;
        }
        const date = localDate(cell.key);
        const detail = cell.day === undefined ? '这天没来' : `${groupDigits(cell.day.messages)} 句${cell.day.tokens > 0 ? ` · ${compactCount(cell.day.tokens)} tokens` : ''}`;
        tooltip.replaceChildren(el('strong', '', [`${date.month} 月 ${date.day} 日`]), el('span', '', [detail]));
        tooltip.style.display = '';
        tooltip.style.left = `${((cell.x + CELL / 2) / CANVAS_W) * 100}%`;
        tooltip.style.top = `${((cell.y - 4) / CANVAS_H) * 100}%`;
        ctx.haptic('tick');
        void play(tooltip, [{ opacity: 0, transform: 'translate(-50%, -90%) scale(.8)' }, { opacity: 1, transform: 'translate(-50%, -100%) scale(1)' }], { duration: 260, easing: EASE.outBack });
      });

      ctx.prompt('用手指在日历上来回涂', 'rub');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          drawGrid();
          void frame.intro();
          void play(wrap, [{ opacity: 0 }, { opacity: 1 }], { duration: 520, delay: 240, easing: 'ease-out' });
        },
        leave() {},
        nudge() {
          if (!completed) void shake(wrap, 5);
          else for (const note of notes) void shake(note, 4);
        },
      };
    },
  };
}
