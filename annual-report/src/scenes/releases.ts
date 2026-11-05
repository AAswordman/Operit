import type { SceneSpec } from '../deck/deck.ts';
import { daysBetween, localDate } from '../data/format.ts';
import type { ProjectReport } from '../data/types.ts';
import { onDrag } from '../interact/drag.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

const ROAD_W = 340;
const ROAD_H = 250;

interface Milestone {
  t: number;
  x: number;
  y: number;
  node: SVGGElement;
  kind: 'formal' | 'patch';
  shown: boolean;
}

function dayOfYear(text: string, year: number): number {
  return daysBetween({ year, month: 1, day: 1 }, localDate(text));
}

export function releasesScene(project: ProjectReport): SceneSpec {
  const year = project.year;
  const yearDays = daysBetween({ year, month: 1, day: 1 }, { year: year + 1, month: 1, day: 1 });
  const windowDays = daysBetween({ year, month: 1, day: 1 }, localDate(project.window.end)) + 1;
  const endT = Math.min(1, windowDays / yearDays);
  const formal = project.releases.formal;
  const patchCount = project.releases.patchCount;
  return {
    id: 'releases',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '项目篇 · 02', title: '一路走来，\n{{一个版本}}接一个版本', seed: 1301 });
      const x0 = 18;
      const x1 = ROAD_W - 18;
      const midY = ROAD_H * 0.56;
      const roadX = (t: number) => x0 + (x1 - x0) * t;
      const roadY = (t: number) => midY + Math.sin(t * Math.PI * 2.6 + 0.4) * 34;
      const roadPoints = (from: number, to: number, steps: number): [number, number][] =>
        Array.from({ length: steps + 1 }, (_, index) => {
          const t = from + ((to - from) * index) / steps;
          return [roadX(t), roadY(t)];
        });

      const svg = svgRoot(ROAD_W, ROAD_H, 'road');
      svg.append(
        pen.curve(roadPoints(0, endT, 40), { seed: 1310, stroke: 'paper-deep', strokeWidth: 22, roughness: 0.4, multiStroke: false }),
        pen.curve(roadPoints(0, endT, 40), { seed: 1311, stroke: 'pencil', strokeWidth: 1.4, roughness: 0.9 }),
        pen.curve(roadPoints(endT, 1, 10), { seed: 1312, stroke: 'pencil', strokeWidth: 1.2, roughness: 1.6, multiStroke: false }),
      );
      const future = svgEl('text', { x: roadX(Math.min(0.97, endT + 0.06)), y: roadY(Math.min(0.97, endT + 0.06)) + 30, class: 'road-future' });
      future.textContent = '未完待续';
      svg.append(future);
      for (let month = 0; month < 12; month += 1) {
        const t = month / 12;
        const label = svgEl('text', { x: roadX(t + 1 / 24), y: ROAD_H - 6, class: 'chart-label' });
        label.textContent = `${month + 1}`;
        svg.append(label);
      }

      const milestones: Milestone[] = [];
      project.releases.patchByMonth.forEach((count, month) => {
        for (let index = 0; index < count; index += 1) {
          const t = (month + (index + 0.5) / count) / 12;
          const x = roadX(t);
          const y = roadY(t);
          const dot = svgEl('g', { class: 'patch-dot' });
          dot.append(pen.circle(x, y, 4, { seed: 1400 + month * 40 + index, stroke: 'mint', strokeWidth: 1, fill: 'mint', fillStyle: 'solid', roughness: 0.5 }));
          dot.style.opacity = '0';
          dot.style.transformOrigin = `${x}px ${y}px`;
          svg.append(dot);
          milestones.push({ t, x, y, node: dot, kind: 'patch', shown: false });
        }
      });
      // 版本号在顶部分两行均匀排开，每个版本号用一条铅笔引线连到路上的小旗。
      // 版本号和小旗都按时间从左到右排列，引线单调向右，不会交叉；
      // 年初几个版本挨得很近时也不会叠在一起。
      const LABEL_ROWS = [14, 36];
      const POLE = 18;
      const slotLeft = 26;
      const slotRight = ROAD_W - 26;
      formal.forEach((release, index) => {
        const t = Math.min(endT, dayOfYear(release.publishedAt, year) / yearDays);
        const x = roadX(t);
        const y = roadY(t);
        const topY = y - POLE;
        const slotX = formal.length === 1 ? ROAD_W / 2 : slotLeft + ((slotRight - slotLeft) * index) / (formal.length - 1);
        const rowY = LABEL_ROWS[index % LABEL_ROWS.length];
        const flag = svgEl('g', { class: 'release-flag' });
        flag.append(
          pen.curve(
            [
              [slotX, rowY + 5],
              [slotX + (x - slotX) * 0.35, rowY + 5 + (topY - rowY) * 0.55],
              [x, topY - 2],
            ],
            { seed: 1540 + index, stroke: 'pencil', strokeWidth: 1, roughness: 0.6, multiStroke: false },
          ),
          pen.line(x, y, x, topY, { seed: 1500 + index, stroke: 'ink', strokeWidth: 1.6, roughness: 0.5, multiStroke: false }),
          pen.polygon(
            [
              [x, topY],
              [x + 13, topY + 4],
              [x, topY + 8],
            ],
            { seed: 1520 + index, stroke: 'ink', strokeWidth: 1.2, fill: index % 2 === 0 ? 'coral' : 'lavender', fillStyle: 'solid', roughness: 0.6 },
          ),
        );
        const label = svgEl('text', { x: slotX, y: rowY, class: 'flag-label' });
        label.textContent = release.tag.replace(/^v/, '');
        flag.append(label);
        flag.style.opacity = '0';
        flag.style.transformOrigin = `${x}px ${y}px`;
        svg.append(flag);
        milestones.push({ t, x, y, node: flag, kind: 'formal', shown: false });
      });
      milestones.sort((a, b) => a.t - b.t);

      const plane = svgEl('g', { class: 'plane' });
      plane.append(
        pen.polygon([[-14, -9], [16, 0], [-14, 9], [-7, 0]], { seed: 1600, stroke: 'ink', strokeWidth: 1.6, fill: 'paper', fillStyle: 'solid', roughness: 0.6 }),
        pen.line(-7, 0, 16, 0, { seed: 1601, stroke: 'ink', strokeWidth: 1.2, roughness: 0.4, multiStroke: false }),
      );
      const trail = svgEl('path', { class: 'plane-trail', d: '' });
      svg.append(trail, plane);

      const summary = el('div', 'release-summary');
      const formalCount = written(String(formal.length), { size: 44, seed: 1700, ink: 'coral', unit: '个正式版' });
      const patchNumber = written(String(patchCount), { size: 44, seed: 1701, ink: 'mint', unit: '个补丁版本' });
      const updates = formal.length + patchCount;
      const cadence = updates === 0 ? '' : `平均每 ${(windowDays / updates).toFixed(1)} 天，就有一次更新`;
      const cadenceLine = el('p', 'lead release-cadence', [cadence]);
      summary.append(el('div', 'release-numbers', [formalCount.el, patchNumber.el]), cadenceLine);
      summary.style.visibility = 'hidden';
      cadenceLine.style.opacity = '0';
      const roadWrap = el('div', 'road-wrap', [svg]);
      frame.stage.append(roadWrap, summary);

      let progress = 0;
      let completed = false;
      const place = (t: number) => {
        const x = roadX(t);
        const y = roadY(t);
        const ahead = Math.min(1, t + 0.004);
        const angle = (Math.atan2(roadY(ahead) - y, roadX(ahead) - x) * 180) / Math.PI;
        plane.setAttribute('transform', `translate(${x} ${y - 16}) rotate(${angle})`);
        trail.setAttribute(
          'd',
          roadPoints(0, t, Math.max(2, Math.round(t * 60)))
            .map(([px, py], index) => `${index === 0 ? 'M' : 'L'} ${px.toFixed(1)} ${(py - 16).toFixed(1)}`)
            .join(' '),
        );
      };
      place(0);

      const complete = async () => {
        completed = true;
        ctx.haptic('success');
        const box = svg.getBoundingClientRect();
        burst(box.left + (roadX(endT) / ROAD_W) * box.width, box.top + (roadY(endT) / ROAD_H) * box.height, { count: 24 });
        summary.style.visibility = 'visible';
        await formalCount.write(0, 1.2);
        await patchNumber.write(0, 1.2);
        await play(cadenceLine, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: EASE.outExpo });
        ctx.reveal();
      };

      onDrag(roadWrap, {
        onMove(point) {
          if (completed) return;
          const box = svg.getBoundingClientRect();
          const scale = ROAD_W / box.width;
          const t = Math.max(0, Math.min(endT, ((point.x * scale - x0) / (x1 - x0))));
          if (t <= progress) return;
          progress = t;
          place(t);
          for (const milestone of milestones) {
            if (milestone.shown || milestone.t > t) continue;
            milestone.shown = true;
            if (milestone.kind === 'formal') {
              ctx.haptic('tick');
              void play(milestone.node, [
                { opacity: 0, transform: 'scaleY(0)' },
                { opacity: 1, transform: 'scaleY(1.15)', offset: 0.6 },
                { opacity: 1, transform: 'scaleY(1)' },
              ], { duration: 420, easing: EASE.out });
            } else {
              void popIn(milestone.node, 0, 0);
            }
          }
          if (t >= endT - 0.002) void complete();
        },
      });

      ctx.prompt('拖着纸飞机往前飞', 'drag');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(roadWrap, [{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 600, delay: 260, easing: EASE.outExpo });
        },
        leave() {},
        nudge() {
          void shake(plane, 6);
        },
      };
    },
  };
}
