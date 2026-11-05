import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, groupDigits } from '../data/format.ts';
import type { MarketType, ProjectReport } from '../data/types.ts';
import { onDrag } from '../interact/drag.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';
import { stickyNote, type StickyColor } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const TYPE_NAMES: Record<MarketType, string> = {
  script: '脚本',
  package: '工具包',
  skill: 'Skill',
  mcp: 'MCP',
};

const BOX_W = 260;
const BOX_H = 150;

export function marketScene(project: ProjectReport): SceneSpec {
  const market = project.market;
  const newTotal = market.newEntries.script + market.newEntries.package + market.newEntries.skill + market.newEntries.mcp;
  const originalTotal = market.originals.script + market.originals.package + market.originals.skill + market.originals.mcp;
  return {
    id: 'market',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '项目篇 · 05', title: '插件市场的{{包裹}}，\n寄到了', seed: 2001 });

      const parcel = el('div', 'parcel');
      const box = svgRoot(BOX_W, BOX_H, 'parcel-box');
      const flapLeft = svgEl('g', { class: 'flap flap-left' });
      const flapRight = svgEl('g', { class: 'flap flap-right' });
      flapLeft.append(pen.polygon([[10, 40], [BOX_W / 2, 40], [BOX_W / 2 - 8, 10], [18, 14]], { seed: 2010, stroke: 'ink', strokeWidth: 1.8, fill: 'paper-deep', fillStyle: 'solid', roughness: 0.9 }));
      flapRight.append(pen.polygon([[BOX_W / 2, 40], [BOX_W - 10, 40], [BOX_W - 18, 14], [BOX_W / 2 + 8, 10]], { seed: 2011, stroke: 'ink', strokeWidth: 1.8, fill: 'paper-deep', fillStyle: 'solid', roughness: 0.9 }));
      flapLeft.style.transformOrigin = '10px 40px';
      flapRight.style.transformOrigin = `${BOX_W - 10}px 40px`;
      box.append(
        pen.rect(10, 40, BOX_W - 20, BOX_H - 46, { seed: 2012, stroke: 'ink', strokeWidth: 2, fill: 'paper-deep', fillStyle: 'hachure', hachureGap: 7, fillWeight: 1, roughness: 1 }),
        pen.line(BOX_W / 2 - 40, BOX_H - 30, BOX_W / 2 + 40, BOX_H - 30, { seed: 2013, stroke: 'pencil', strokeWidth: 1.4, roughness: 1 }),
        flapLeft,
        flapRight,
      );
      const label = el('span', 'parcel-label', ['易碎 · 内含插件']);
      const tapeStrip = el('div', 'parcel-tape', [el('span', '', ['OPERIT MARKET · OPERIT MARKET · OPERIT MARKET'])]);
      const tapeLeft = el('div', 'parcel-tape-half left');
      const tapeRight = el('div', 'parcel-tape-half right');
      parcel.append(box, label, tapeStrip, tapeLeft, tapeRight);

      const cards = el('div', 'market-cards');
      const cardData: { title: string; value: string; detail: string }[] = [
        { title: '新上架作品', value: groupDigits(newTotal), detail: '今年新上架' },
        { title: '原创作品', value: groupDigits(originalTotal), detail: `来自 ${market.newPublishers} 位新作者` },
        { title: '累计下载', value: compactCount(market.downloads), detail: `${market.publishers} 位作者的心血` },
      ];
      const breakdown = el('p', 'caption market-breakdown', [
        `脚本 ${market.newEntries.script} · 工具包 ${market.newEntries.package} · Skill ${market.newEntries.skill} · MCP ${market.newEntries.mcp}`,
      ]);
      breakdown.style.opacity = '0';
      const writers = cardData.map((item, index) => {
        const number = written(item.value, { size: 30, seed: 2020 + index, ink: 'ink', tracking: 2 });
        const card = el('div', 'market-card sketch-border', [
          el('span', 'market-card-title', [item.title]),
          number.el,
          el('span', 'market-card-detail', [item.detail]),
        ]);
        card.style.opacity = '0';
        cards.append(card);
        return { card, number };
      });

      const board = el('div', 'sticky-board compact market-top');
      board.style.display = 'none';
      const colors: StickyColor[] = ['yellow', 'pink', 'mint'];
      let flipped = 0;
      market.topEntries.forEach((entry, index) => {
        board.append(
          stickyNote({
            color: colors[index % colors.length],
            seed: 2040 + index,
            question: `人气第 ${index + 1} 名？`,
            onFlip: () => {
              flipped += 1;
              ctx.haptic('tap');
              if (flipped === market.topEntries.length) ctx.reveal();
            },
            reveal: () => stickyContent(compactCount(entry.downloads), `${entry.title}`, `${TYPE_NAMES[entry.type]} · @${entry.author}`, 2050 + index, 24),
          }).el,
        );
      });

      frame.stage.append(parcel, cards, breakdown, board);

      let torn = false;
      let pull = 0;
      const tear = async () => {
        torn = true;
        ctx.haptic('heavy');
        tapeStrip.style.visibility = 'hidden';
        tapeLeft.style.visibility = 'visible';
        tapeRight.style.visibility = 'visible';
        const rect = parcel.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + 40, { count: 22 });
        void play(tapeLeft, [{ transform: 'translate(0,0) rotate(0)' }, { transform: 'translate(-140px,-60px) rotate(-38deg)', opacity: 0 }], { duration: 620, easing: EASE.out });
        void play(tapeRight, [{ transform: 'translate(0,0) rotate(0)' }, { transform: 'translate(150px,-40px) rotate(32deg)', opacity: 0 }], { duration: 620, easing: EASE.out });
        void play(flapLeft, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(-104deg)' }], { duration: 700, delay: 120, easing: EASE.outBack });
        void play(flapRight, [{ transform: 'rotate(0deg)' }, { transform: 'rotate(104deg)' }], { duration: 700, delay: 160, easing: EASE.outBack });
        await play(parcel, [{ transform: 'translateY(0) scale(1)' }, { transform: 'translateY(6px) scale(.86)' }], { duration: 520, delay: 200, easing: EASE.out });
        const tilts = [-3, 0, 3];
        for (const [index, item] of writers.entries()) {
          await popIn(item.card, 0, tilts[index]);
          void item.number.write(0, 1.5);
        }
        void play(breakdown, [{ opacity: 0 }, { opacity: 1 }], { duration: 400, easing: 'ease-out' });
        board.style.display = '';
        await Promise.all(Array.from(board.children).map((note, index) => popIn(note, index * 110, 0)));
        ctx.prompt('翻开人气榜', 'tap');
      };

      onDrag(tapeStrip, {
        onMove(point) {
          if (torn) return;
          pull = Math.max(pull, Math.abs(point.dx));
          tapeStrip.style.transform = `translateX(${point.dx * 0.12}px) rotate(${point.dx * 0.01}deg)`;
          tapeStrip.style.setProperty('--peel', `${Math.min(1, pull / 140)}`);
          if (pull > 140) void tear();
        },
        onEnd() {
          if (torn) return;
          tapeStrip.style.transform = '';
          if (pull > 30) void shake(tapeStrip, 4);
        },
      });

      ctx.prompt('划开封箱胶带', 'swipe-side');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(parcel, [
            { opacity: 0, transform: 'translateY(-60px) rotate(-6deg)' },
            { opacity: 1, transform: 'translateY(6px) rotate(2deg)', offset: 0.7 },
            { opacity: 1, transform: 'translateY(0) rotate(0)' },
          ], { duration: 720, delay: 300, easing: EASE.out });
        },
        leave() {},
        nudge() {
          if (!torn) void shake(tapeStrip, 6);
          else for (const note of Array.from(board.children)) void shake(note, 4);
        },
      };
    },
  };
}
