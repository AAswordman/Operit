import type { SceneSpec } from '../deck/deck.ts';
import { groupDigits } from '../data/format.ts';
import type { ProjectReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { drawIn, hideStrokes } from '../sketch/drawIn.ts';
import { sparkle, star, underline } from '../sketch/doodles.ts';
import { pen } from '../sketch/pen.ts';
import { between, createRandom } from '../sketch/random.ts';
import { el, pathsOf, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

const SKY_W = 340;
const SKY_H = 300;
const TAPS = 3;

export function starsScene(project: ProjectReport): SceneSpec {
  const stars = project.stars;
  return {
    id: 'stars',
    surface: 'night',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '项目篇 · 03', title: '天上的{{星星}}，\n越来越多了', seed: 1801 });
      const sky = svgRoot(SKY_W, SKY_H, 'sky');
      const random = createRandom(1802);
      const background = svgEl('g', { class: 'sky-dust' });
      for (let index = 0; index < 46; index += 1) {
        const dot = svgEl('circle', { cx: between(random, 4, SKY_W - 4), cy: between(random, 4, SKY_H - 40), r: between(random, 0.6, 1.6) });
        dot.style.animationDelay = `${(between(random, 0, 3)).toFixed(2)}s`;
        background.append(dot);
      }
      sky.append(background);

      // 增长曲线：年初的存量加上每月新增，只画到最后一个有数据的月份。
      let lastMonth = 0;
      stars.byMonth.forEach((value, month) => {
        if (value > 0) lastMonth = month;
      });
      const cumulative: number[] = [];
      let running = stars.atStart;
      for (let month = 0; month <= lastMonth; month += 1) {
        running += stars.byMonth[month];
        cumulative.push(running);
      }
      const minValue = stars.atStart;
      const maxValue = stars.atEnd;
      const left = 26;
      const right = SKY_W - 20;
      const top = 34;
      const bottom = SKY_H - 46;
      const px = (month: number) => left + ((right - left) * (month + 1)) / (lastMonth + 1);
      const py = (value: number) => bottom - ((bottom - top) * (value - minValue)) / Math.max(1, maxValue - minValue);
      const points: [number, number][] = [[left, py(minValue)], ...cumulative.map((value, month): [number, number] => [px(month), py(value)])];
      const segments = [0, 1, 2].map((part) => {
        const from = Math.floor((points.length - 1) * (part / TAPS));
        const to = Math.floor((points.length - 1) * ((part + 1) / TAPS));
        const slice = points.slice(from, to + 1);
        const group = svgEl('g');
        group.append(pen.curve(slice, { seed: 1810 + part, stroke: 'yellow', strokeWidth: 2.6, roughness: 0.7, multiStroke: false }));
        for (let index = 1; index < slice.length; index += 1) {
          const point = slice[index];
          group.append(star(point[0], point[1], 6, 'star', 1820 + part * 20 + index));
        }
        return group;
      });
      for (const segment of segments) {
        sky.append(segment);
        hideStrokes(pathsOf(segment));
      }
      const axis = pen.line(left, bottom + 10, right, bottom + 10, { seed: 1830, stroke: 'pencil', strokeWidth: 1, roughness: 1 });
      sky.append(axis);
      for (let month = 0; month <= lastMonth; month += 1) {
        const label = svgEl('text', { x: px(month), y: bottom + 28, class: 'chart-label' });
        label.textContent = `${month + 1}`;
        sky.append(label);
      }
      const sparks = svgEl('g');
      sky.append(sparks);

      const counter = el('div', 'tap-counter');
      const counterDots = Array.from({ length: TAPS }, (_, index) => {
        const holder = svgRoot(26, 26, 'tap-dot');
        holder.append(star(13, 13, 10, 'pencil', 1840 + index, false));
        counter.append(holder);
        return holder;
      });

      const summary = el('div', 'stars-summary');
      const from = written(groupDigits(stars.atStart), { size: 28, seed: 1850, ink: 'ink-soft' });
      const to = written(groupDigits(stars.atEnd), { size: 48, seed: 1851, ink: 'star', unit: '颗星' });
      const arrowSvg = svgRoot(40, 24, 'stars-arrow');
      arrowSvg.append(pen.curve([[2, 14], [18, 8], [36, 12]], { seed: 1852, stroke: 'ink-soft', strokeWidth: 2, roughness: 0.6, multiStroke: false }), pen.polyline([[28, 5], [37, 12], [28, 18]], { seed: 1853, stroke: 'ink-soft', strokeWidth: 2, roughness: 0.5, multiStroke: false }));
      const gained = stars.atEnd - stars.atStart;
      const gainedLine = el('p', 'lead stars-gained', [`今年新增 `, el('span', 'em', [groupDigits(gained)]), ` 颗，是年初的 ${(stars.atEnd / Math.max(1, stars.atStart)).toFixed(1)} 倍`]);
      const gainedMark = svgRoot(220, 10, 'stars-underline');
      gainedMark.append(underline(4, 6, 210, 'yellow', 1854, 3));
      summary.append(el('div', 'stars-numbers', [from.el, arrowSvg, to.el]), gainedLine, gainedMark);
      summary.style.visibility = 'hidden';
      gainedLine.style.opacity = '0';
      hideStrokes(pathsOf(gainedMark));
      arrowSvg.style.opacity = '0';

      const skyWrap = el('div', 'sky-wrap', [sky, counter]);
      frame.stage.append(skyWrap, summary);

      let taps = 0;
      const complete = async () => {
        ctx.haptic('success');
        summary.style.visibility = 'visible';
        await from.write(0, 1.6);
        void play(arrowSvg, [{ opacity: 0, transform: 'translateX(-8px)' }, { opacity: 1, transform: 'translateX(0)' }], { duration: 300, easing: EASE.out });
        await to.write(80, 1.2);
        await play(gainedLine, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 400, easing: EASE.outExpo });
        await drawIn(pathsOf(gainedMark), { msPerPx: 1.2 });
        ctx.reveal();
      };

      onTap(skyWrap, () => {
        if (taps >= TAPS) return;
        const index = taps;
        taps += 1;
        ctx.haptic(taps === TAPS ? 'heavy' : 'tap');
        const dot = counterDots[index];
        dot.replaceChildren(star(13, 13, 10, 'yellow', 1860 + index, true));
        void popIn(dot, 0, 0);
        const segment = segments[index];
        void drawIn(pathsOf(segment), { msPerPx: 2.2, maxDuration: 900, gap: -40 });
        const box = sky.getBoundingClientRect();
        const lastPoint = points[Math.floor((points.length - 1) * ((index + 1) / TAPS))];
        burst(box.left + (lastPoint[0] / SKY_W) * box.width, box.top + (lastPoint[1] / SKY_H) * box.height, {
          count: 14,
          inks: ['yellow', 'star', 'lavender'],
          speed: 0.42,
        });
        const sparkleRandom = createRandom(1870 + index);
        for (let count = 0; count < 4; count += 1) {
          const node = sparkle(between(sparkleRandom, 20, SKY_W - 20), between(sparkleRandom, 20, SKY_H - 70), between(sparkleRandom, 4, 8), 'star', 1880 + index * 10 + count);
          sparks.append(node);
          void popIn(node, count * 70, 0);
        }
        if (taps === TAPS) void complete();
      }, { pressScale: 0.99 });

      ctx.prompt('点三下，点亮星空', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
        },
        leave() {},
        nudge() {
          void shake(counter, 6);
        },
      };
    },
  };
}
