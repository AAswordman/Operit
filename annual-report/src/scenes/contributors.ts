import type { SceneSpec } from '../deck/deck.ts';
import { groupDigits } from '../data/format.ts';
import type { ProjectReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { artText } from '../sketch/artText.ts';
import { heart, sparkle, tape } from '../sketch/doodles.ts';
import { between, createRandom } from '../sketch/random.ts';
import { el, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

const NAME_COLORS = ['yellow', 'pink', 'mint', 'blue', 'lavender'] as const;

export function contributorsScene(project: ProjectReport): SceneSpec {
  const contributors = project.contributors;
  return {
    id: 'contributors',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker: '项目篇 · 04', title: '今年，{{新朋友}}\n在留言簿上签了名', seed: 1901 });
      const book = el('div', 'guestbook tappable');
      const pages = el('div', 'book-pages');
      const names = el('div', 'book-names');
      const random = createRandom(1902);
      contributors.newcomers.forEach((newcomer, index) => {
        const color = NAME_COLORS[index % NAME_COLORS.length];
        const tag = el('span', `name-tag name-${color}`, [`@${newcomer.login}`]);
        tag.style.setProperty('--tilt', `${between(random, -6, 6).toFixed(1)}deg`);
        tag.style.opacity = '0';
        names.append(tag);
      });
      pages.append(names);
      const cover = el('div', 'book-cover');
      const coverArt = svgRoot(120, 60, 'book-cover-art');
      coverArt.append(heart(30, 30, 18, 'coral', 1903), sparkle(70, 22, 7, 'yellow', 1904), sparkle(94, 40, 5, 'paper', 1905));
      cover.append(
        tape(70, 18, -6, 1906, 'book-tape'),
        artText('留言簿', { seed: 1907, className: 'book-title' }),
        el('span', 'book-year', [String(project.year)]),
        coverArt,
      );
      book.append(pages, cover);

      const summary = el('div', 'contrib-summary');
      const newcomerCount = written(String(contributors.newcomers.length), { size: 50, seed: 1910, ink: 'coral', unit: '位新朋友' });
      const line = el('p', 'lead', [
        `今年一共 `,
        el('span', 'em', [String(contributors.total)]),
        ` 位贡献者`,
        el('br'),
        `合并 `,
        el('span', 'em', [groupDigits(project.pullRequests.merged)]),
        ` 个 PR，关闭 `,
        el('span', 'em', [groupDigits(project.issues.closed)]),
        ` 个 Issue`,
      ]);
      line.style.opacity = '0';
      summary.append(newcomerCount.el, line);
      summary.style.visibility = 'hidden';
      frame.stage.append(book, summary);

      let opened = false;
      const open = async () => {
        opened = true;
        book.classList.remove('tappable');
        ctx.haptic('tap');
        await play(cover, [
          { transform: 'rotateY(0deg)' },
          { transform: 'rotateY(-172deg)' },
        ], { duration: 820, easing: EASE.inOut });
        cover.style.visibility = 'hidden';
        const box = book.getBoundingClientRect();
        burst(box.left + box.width / 2, box.top + 30, { count: 18, speed: 0.45 });
        const tags = Array.from(names.children);
        await Promise.all(tags.map((tag, index) => popIn(tag, index * 55, Number.parseFloat(getComputedStyle(tag).getPropertyValue('--tilt')))));
        summary.style.visibility = 'visible';
        await newcomerCount.write(0, 1.3);
        await play(line, [{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: EASE.outExpo });
        ctx.haptic('success');
        ctx.reveal();
      };
      onTap(book, () => {
        if (!opened) void open();
      }, { once: true, pressScale: 0.97 });

      ctx.prompt('翻开留言簿', 'open');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(book, [{ opacity: 0, transform: 'translateY(16px) rotate(-2deg)' }, { opacity: 1, transform: 'translateY(0) rotate(0)' }], { duration: 640, delay: 280, easing: EASE.outExpo });
        },
        leave() {},
        nudge() {
          if (!opened) void shake(book, 6);
        },
      };
    },
  };
}
