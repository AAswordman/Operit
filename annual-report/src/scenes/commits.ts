import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, groupDigits, localDate } from '../data/format.ts';
import type { ProjectReport } from '../data/types.ts';
import { holdToFill } from '../interact/hold.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { drawIn, hideStrokes } from '../sketch/drawIn.ts';
import { arrow, circleMark } from '../sketch/doodles.ts';
import { pen } from '../sketch/pen.ts';
import { el, pathsOf, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';
import { stickyNote } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const CHART_W = 340;
const CHART_H = 168;

export function commitsScene(project: ProjectReport): SceneSpec {
  const commits = project.commits;
  return {
    id: 'commits',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '项目篇 · 01', title: '这一年，{{Operit}} 写下了', seed: 1201 });
      const total = written(groupDigits(commits.total), { size: 70, seed: 1202, ink: 'ink', unit: '次提交' });
      const totalRow = el('div', 'commit-total', [total.el]);
      totalRow.style.visibility = 'hidden';

      const chart = svgRoot(CHART_W, CHART_H, 'commit-chart');
      const max = Math.max(...commits.byMonth);
      const busiestMonth = commits.byMonth.indexOf(max);
      const baseY = CHART_H - 22;
      const barW = 17;
      const step = CHART_W / 12;
      chart.append(pen.line(4, baseY, CHART_W - 4, baseY + 1, { seed: 1210, stroke: 'pencil', strokeWidth: 1.4, roughness: 0.8 }));
      const bars = new Map<number, SVGGElement>();
      const ghosts = svgEl('g', { class: 'chart-ghosts' });
      chart.append(ghosts);
      commits.byMonth.forEach((value, month) => {
        const x = step * month + (step - barW) / 2;
        const label = svgEl('text', { x: x + barW / 2, y: CHART_H - 4, class: 'chart-label' });
        label.textContent = String(month + 1);
        chart.append(label);
        // 交互前先用铅笔虚线占位，高度统一，不提前泄露数据。
        const ghost = pen.rect(x, baseY - 54, barW, 54, { seed: 1280 + month, stroke: 'pencil', strokeWidth: 1, roughness: 1.4, multiStroke: false });
        ghost.dataset.month = String(month);
        ghosts.append(ghost);
        if (value === 0) return;
        const height = Math.max(4, ((baseY - 18) * value) / max);
        const bar = pen.rect(x, baseY - height, barW, height, {
          seed: 1220 + month,
          stroke: 'ink',
          strokeWidth: 1.6,
          fill: month === busiestMonth ? 'coral' : 'lavender',
          fillStyle: 'hachure',
          hachureGap: 4,
          fillWeight: 1.4,
          hachureAngle: -41,
          roughness: 1,
        });
        bar.dataset.month = String(month);
        bars.set(month, bar);
        chart.append(bar);
      });
      const busiestX = step * busiestMonth + step / 2;
      const busiestTop = baseY - (baseY - 18);
      const annotation = svgEl('g', { class: 'commit-annotation' });
      annotation.append(
        circleMark(busiestX, busiestTop + 6, 20, 14, 'coral', 1240, 2.2),
        arrow([[busiestX - 58, busiestTop - 4], [busiestX - 40, busiestTop - 10], [busiestX - 24, busiestTop]], 'ink-soft', 1241, 1.8, 8),
      );
      const annotationText = svgEl('text', { x: busiestX - 62, y: busiestTop - 2, class: 'chart-note', 'text-anchor': 'end' });
      annotationText.textContent = `最忙的 ${busiestMonth + 1} 月`;
      annotation.append(annotationText);
      annotation.style.opacity = '0';
      chart.append(annotation);

      const holdZone = el('div', 'hold-zone');
      const holdButton = el('button', 'hold-button tappable');
      holdButton.type = 'button';
      holdButton.setAttribute('aria-label', '按住，写下这一年');
      const holdArt = svgRoot(112, 112);
      const ring = svgEl('circle', { cx: 56, cy: 56, r: 50, class: 'hold-ring' });
      const circumference = 2 * Math.PI * 50;
      ring.style.strokeDasharray = `${circumference}`;
      ring.style.strokeDashoffset = `${circumference}`;
      holdArt.append(
        pen.circle(56, 56, 84, { seed: 1250, stroke: 'ink', strokeWidth: 2, fill: 'lavender', fillStyle: 'hachure', hachureGap: 5, fillWeight: 1.6, roughness: 1.2 }),
        // 钢笔笔尖
        pen.polygon([[56, 30], [70, 58], [56, 80], [42, 58]], { seed: 1251, stroke: 'ink', strokeWidth: 2.2, fill: 'paper', fillStyle: 'solid', roughness: 0.6 }),
        pen.line(56, 46, 56, 70, { seed: 1252, stroke: 'ink', strokeWidth: 1.8, roughness: 0.4, multiStroke: false }),
        pen.circle(56, 58, 6, { seed: 1253, stroke: 'ink', strokeWidth: 1.6, fill: 'ink', fillStyle: 'solid', roughness: 0.4 }),
        ring,
      );
      holdButton.append(holdArt);
      const holdLabel = el('span', 'hold-label', ['']);
      holdZone.append(holdButton, holdLabel);

      const board = el('div', 'sticky-board compact');
      board.style.display = 'none';
      frame.stage.append(totalRow, chart, holdZone, board);

      const busiest = localDate(commits.busiestDay.date);
      let flipped = 0;
      const onFlip = () => {
        flipped += 1;
        ctx.haptic('tap');
        if (flipped === 3) ctx.reveal();
      };
      board.append(
        stickyNote({
          color: 'yellow',
          seed: 1261,
          question: '删改了多少行？',
          onFlip,
          reveal: () => stickyContent(`+${compactCount(project.codeFrequency.additions)}`, '新增代码行', `删掉 ${compactCount(project.codeFrequency.deletions)} 行`, 1262),
        }).el,
        stickyNote({
          color: 'pink',
          seed: 1263,
          question: '最拼的一天？',
          onFlip,
          reveal: () => stickyContent(`${busiest.month}月${busiest.day}日`, `一天 ${commits.busiestDay.count} 次提交`, null, 1264),
        }).el,
        stickyNote({
          color: 'mint',
          seed: 1265,
          question: '凌晨还在写？',
          onFlip,
          reveal: () => stickyContent(groupDigits(commits.lateNight), '次深夜提交', '0 点到 5 点之间', 1266),
        }).el,
      );

      for (const bar of bars.values()) hideStrokes(pathsOf(bar));
      const drawn = new Set<number>();
      const drawBar = (month: number) => {
        if (drawn.has(month)) return;
        drawn.add(month);
        const ghost = ghosts.querySelector(`[data-month="${month}"]`);
        if (ghost !== null) void play(ghost, [{ opacity: 1 }, { opacity: 0 }], { duration: 240, easing: 'ease-out' });
        const bar = bars.get(month);
        // 当月没有提交就没有柱子，只擦掉占位。
        if (bar === undefined) return;
        void drawIn(pathsOf(bar), { msPerPx: 0.5, maxDuration: 260, gap: -60 });
      };

      let completed = false;
      const complete = async () => {
        completed = true;
        for (let month = 0; month < 12; month += 1) drawBar(month);
        ctx.haptic('success');
        const rect = holdButton.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 30 });
        await play(holdZone, [{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(.6) rotate(-8deg)' }], {
          duration: 320,
          easing: EASE.in,
        });
        holdZone.style.display = 'none';
        totalRow.style.visibility = 'visible';
        await total.write(0, 1.1);
        await play(annotation, [{ opacity: 0 }, { opacity: 1 }], { duration: 400, easing: 'ease-out' });
        board.style.display = '';
        const notes = Array.from(board.children);
        await Promise.all(notes.map((note, index) => popIn(note, index * 110, 0)));
        ctx.prompt('翻开便利贴', 'tap');
      };

      holdToFill(holdButton, {
        duration: 1700,
        onProgress(progress) {
          ring.style.strokeDashoffset = `${circumference * (1 - progress)}`;
          const reach = Math.floor(progress * 12.4);
          for (let month = 0; month < Math.min(12, reach); month += 1) drawBar(month);
        },
        onMilestone() {
          ctx.haptic('tick');
        },
        onEarlyRelease() {
          holdLabel.textContent = '别松手，按住才能写完';
          void shake(holdLabel, 5);
        },
        onComplete() {
          void complete();
        },
      });

      ctx.prompt('按住墨水瓶', 'hold');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(chart, [{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 300, easing: 'ease-out' });
        },
        leave() {},
        nudge() {
          if (!completed) {
            void shake(holdButton, 8);
          } else {
            for (const note of Array.from(board.children)) void shake(note, 4);
          }
        },
      };
    },
  };
}
