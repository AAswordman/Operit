import type { SceneSpec } from '../deck/deck.ts';
import type { ProjectReport } from '../data/types.ts';
import { EASE, play, popIn, wait } from '../motion/anim.ts';
import { boilable } from '../sketch/boil.ts';
import { drawIn, hideStrokes } from '../sketch/drawIn.ts';
import { circleMark, sparkle, squiggle, star, tape } from '../sketch/doodles.ts';
import { handText, hideHand, writeHand } from '../sketch/handwriting.ts';
import { sketchLogo } from '../sketch/logo.ts';
import { el, pathsOf, svgRoot } from '../sketch/svg.ts';

export function coverScene(project: ProjectReport): SceneSpec {
  return {
    id: 'cover',
    surface: 'cover',
    nextLabel: '上滑，翻开这一年',
    build(ctx) {
      const root = el('div', 'cover');
      const band = el('div', 'cover-band');
      const logo = svgRoot(72, 72, 'cover-logo');
      const logoArt = sketchLogo(72, 41);
      logo.append(logoArt);
      const eyebrow = el('div', 'cover-eyebrow', ['OPERIT · ANNUAL REPORT']);
      const brand = handText('Operit', { size: 50, seed: 7, ink: 'lavender', weight: 0.1, slant: 0.18 });
      const heading = handText('年度报告', { size: 58, seed: 19, ink: 'ink', weight: 0.095, tracking: 6 });
      const year = handText(String(project.year), { size: 104, seed: 2026, ink: 'coral', weight: 0.085, tracking: 2 });
      const yearWrap = el('div', 'cover-year', [year.svg]);
      const ring = svgRoot(year.width, year.height, 'cover-ring');
      ring.append(circleMark(year.width / 2, year.height * 0.4, year.width * 0.56, year.height * 0.46, 'ink', 77, 2.2));
      yearWrap.append(ring);
      const motto = el('p', 'cover-motto', ['一本只属于这一年的手账']);
      const tapeA = tape(92, 24, -32, 5, 'cover-tape cover-tape-a');
      const tapeB = tape(80, 22, 28, 9, 'cover-tape cover-tape-b');
      root.append(band, tapeA, tapeB, logo, eyebrow, brand.svg, heading.svg, yearWrap, motto);
      ctx.body.append(root);

      // 装饰按实际排版的位置摆放，避开文字。页面此时已挂进文档，可以测量。
      const box = root.getBoundingClientRect();
      const local = (element: Element) => {
        const rect = element.getBoundingClientRect();
        return { left: rect.left - box.left, top: rect.top - box.top, right: rect.right - box.left, bottom: rect.bottom - box.top };
      };
      const brandBox = local(brand.svg);
      const headingBox = local(heading.svg);
      const logoBox = local(logo);
      const yearBox = local(yearWrap);
      const mottoBox = local(motto);
      const decor = svgRoot(box.width, box.height, 'cover-decor');
      const sparkles = [
        boilable((seed) => sparkle(brandBox.left - 16, brandBox.top + 12, 11, 'yellow', seed), 101),
        boilable((seed) => sparkle(logoBox.right + 46, logoBox.top + 22, 8, 'lavender', seed), 202),
        boilable((seed) => sparkle(Math.min(box.width - 26, headingBox.right + 16), headingBox.top - 2, 12, 'coral', seed), 303),
        boilable((seed) => star(yearBox.left + 18, yearBox.bottom + 16, 9, 'mint', seed), 404),
        boilable((seed) => squiggle(mottoBox.left + (mottoBox.right - mottoBox.left) / 2 - 32, mottoBox.bottom + 16, 64, 4, 'pencil', seed), 505),
      ];
      for (const item of sparkles) decor.append(item.group);
      root.prepend(decor);

      hideStrokes(pathsOf(logoArt));
      hideHand(brand);
      hideHand(heading);
      hideHand(year);
      hideStrokes(pathsOf(ring));
      for (const element of [eyebrow, motto, tapeA, tapeB]) element.style.opacity = '0';
      decor.style.opacity = '0';

      let played = false;
      return {
        enter() {
          for (const item of sparkles) item.start();
          if (played) return;
          played = true;
          void (async () => {
            const logoDone = drawIn(pathsOf(logoArt), { msPerPx: 0.5, maxDuration: 520, gap: -40 });
            void play(eyebrow, [{ opacity: 0, letterSpacing: '0.6em' }, { opacity: 1, letterSpacing: '0.32em' }], {
              duration: 900,
              delay: 200,
              easing: EASE.outExpo,
            });
            await wait(380);
            await writeHand(brand, 1.5);
            await writeHand(heading, 1.6);
            await logoDone;
            ctx.haptic('tap');
            await writeHand(year, 1.2);
            await drawIn(pathsOf(ring), { msPerPx: 0.9, maxDuration: 700 });
            ctx.haptic('tick');
            void popIn(tapeA, 0, -32);
            void popIn(tapeB, 120, 28);
            void play(decor, [{ opacity: 0 }, { opacity: 1 }], { duration: 600, easing: 'ease-out' });
            void play(motto, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'translateY(0)' }], {
              duration: 600,
              delay: 160,
              easing: EASE.outExpo,
            });
            ctx.reveal();
          })();
        },
        leave() {
          for (const item of sparkles) item.stop();
        },
        nudge() {
          void play(yearWrap, [{ transform: 'rotate(0)' }, { transform: 'rotate(-4deg)' }, { transform: 'rotate(3deg)' }, { transform: 'rotate(0)' }], {
            duration: 420,
            easing: EASE.out,
          });
        },
      };
    },
  };
}
