import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, percentText, share } from '../data/format.ts';
import type { UserReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { drawIn, hideStrokes } from '../sketch/drawIn.ts';
import { star } from '../sketch/doodles.ts';
import { handText } from '../sketch/handwriting.ts';
import { pen, type Ink } from '../sketch/pen.ts';
import { el, pathsOf, svgEl, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

const INKS: Ink[] = ['coral', 'lavender', 'mint'];
const DONUT = 150;

export function modelsScene(user: UserReport, kicker: string): SceneSpec {
  const models = user.tokens.topModels;
  const total = user.tokens.total;
  return {
    id: 'models',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '你最常找的{{模型}}', seed: 3201 });
      const hand = el('div', 'model-hand');
      const tilts = models.length === 1 ? [0] : models.length === 2 ? [-6, 6] : [-9, 0, 9];
      let flipped = 0;
      const cards = models.map((model, index) => {
        const card = el('div', `model-card rank-${index + 1} tappable`);
        card.style.setProperty('--tilt', `${tilts[index]}deg`);
        const inner = el('div', 'model-inner');
        const back = el('div', 'model-face model-back');
        back.append(handText('?', { size: 46, seed: 3210 + index, ink: 'paper', ghost: false }).svg, el('span', 'model-back-label', ['翻开看看']));
        const front = el('div', 'model-face model-front');
        const badge = svgRoot(44, 44, 'model-rank');
        badge.append(pen.circle(22, 22, 36, { seed: 3220 + index, stroke: INKS[index], strokeWidth: 2.2, fill: INKS[index], fillStyle: 'hachure', hachureGap: 4, roughness: 1 }));
        const rankText = svgEl('text', { x: 22, y: 28, class: 'model-rank-text' });
        rankText.textContent = String(index + 1);
        badge.append(rankText);
        const number = written(compactCount(model.tokens), { size: 30, seed: 3230 + index, ink: 'ink', tracking: 2 });
        front.append(
          badge,
          el('span', 'model-name', [model.model]),
          number.el,
          el('span', 'model-share', [`占全年 ${percentText(model.tokens, total)}`]),
        );
        inner.append(back, front);
        card.append(inner);
        onTap(card, () => {
          if (card.classList.contains('is-flipped')) return;
          card.classList.add('is-flipped');
          card.classList.remove('tappable');
          ctx.haptic(index === 0 ? 'heavy' : 'tap');
          void play(inner, [
            { transform: 'rotateY(0deg) translateY(0)' },
            { transform: 'rotateY(90deg) translateY(-18px)', offset: 0.45 },
            { transform: 'rotateY(190deg) translateY(-6px)', offset: 0.8 },
            { transform: 'rotateY(180deg) translateY(0)' },
          ], { duration: 680, easing: EASE.inOut }).then(() => {
            void number.write(0, 1.4);
            if (index === 0) {
              const rect = card.getBoundingClientRect();
              burst(rect.left + rect.width / 2, rect.top + 20, { count: 20, inks: ['yellow', 'coral'] });
            }
          });
          flipped += 1;
          if (flipped === models.length) void finish();
        }, { once: true, pressScale: 0.96 });
        hand.append(card);
        return card;
      });

      const donutWrap = el('div', 'model-donut');
      const donut = svgRoot(DONUT, DONUT);
      const cx = DONUT / 2;
      const cy = DONUT / 2;
      let start = -Math.PI / 2;
      const slices: SVGGElement[] = [];
      const outerR = (DONUT - 20) / 2;
      const innerR = 30;
      const ring = (from: number, to: number): [number, number][] => {
        const steps = Math.max(4, Math.ceil(((to - from) / (Math.PI * 2)) * 48));
        const outer: [number, number][] = [];
        const inner: [number, number][] = [];
        for (let index = 0; index <= steps; index += 1) {
          const angle = from + ((to - from) * index) / steps;
          outer.push([cx + outerR * Math.cos(angle), cy + outerR * Math.sin(angle)]);
          inner.push([cx + innerR * Math.cos(angle), cy + innerR * Math.sin(angle)]);
        }
        return [...outer, ...inner.reverse()];
      };
      const covered = models.reduce((sum, model) => sum + model.tokens, 0);
      const parts = [...models.map((model, index) => ({ value: model.tokens, ink: INKS[index] })), { value: Math.max(0, total - covered), ink: 'pencil' as Ink }];
      parts.forEach((part, index) => {
        const angle = share(part.value, total) * Math.PI * 2;
        if (angle <= 0.01) return;
        const slice = pen.polygon(ring(start, start + angle), {
          seed: 3240 + index,
          stroke: 'ink',
          strokeWidth: 1.6,
          fill: part.ink,
          fillStyle: 'hachure',
          hachureGap: 4.5,
          fillWeight: 1.4,
          roughness: 0.9,
        });
        slices.push(slice);
        donut.append(slice);
        start += angle;
      });
      donut.append(star(cx, cy, 13, 'yellow', 3251));
      const caption = el('p', 'caption model-caption', [`前 ${models.length} 名占了你全年用量的 ${percentText(covered, total)}`]);
      donutWrap.append(donut, caption);
      donutWrap.style.visibility = 'hidden';
      frame.stage.append(hand, donutWrap);

      const finish = async () => {
        await new Promise((resolve) => setTimeout(resolve, 900));
        donutWrap.style.visibility = 'visible';
        for (const slice of slices) hideStrokes(pathsOf(slice));
        await popIn(donut, 0, 0);
        for (const slice of slices) await drawIn(pathsOf(slice), { msPerPx: 0.25, maxDuration: 260, gap: -80 });
        await play(caption, [{ opacity: 0 }, { opacity: 1 }], { duration: 360, easing: 'ease-out' });
        ctx.haptic('success');
        ctx.reveal();
      };

      ctx.prompt('点一下，翻开每张牌', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          cards.forEach((card, index) => {
            void play(card, [
              { opacity: 0, transform: `translateY(60px) rotate(0deg)` },
              { opacity: 1, transform: `translateY(0) rotate(${tilts[index]}deg)` },
            ], { duration: 560, delay: 300 + index * 120, easing: EASE.outBack });
          });
        },
        leave() {},
        nudge() {
          for (const card of cards) if (!card.classList.contains('is-flipped')) void shake(card, 5);
        },
      };
    },
  };
}
