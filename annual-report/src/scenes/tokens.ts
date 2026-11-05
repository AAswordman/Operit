import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, groupDigits, localDate, percentText } from '../data/format.ts';
import type { UserReport } from '../data/types.ts';
import { scratchCard } from '../interact/scratch.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { el } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';
import { stickyNote } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const CARD_W = 300;
const CARD_H = 156;

export function tokensScene(user: UserReport, kicker: string): SceneSpec {
  const tokens = user.tokens;
  const windowStart = localDate(user.window.start);
  return {
    id: 'tokens',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '今年，你和 AI\n一共用掉了{{这么多}}', seed: 3101 });
      const card = el('div', 'scratch-card sketch-border');
      card.style.width = `${CARD_W}px`;
      card.style.height = `${CARD_H}px`;
      const total = written(compactCount(tokens.total), { size: 48, seed: 3102, ink: 'ink', unit: 'tokens' });
      const prize = el('div', 'scratch-prize', [total.el]);
      if (tokens.coverageStart !== null) {
        const coverage = localDate(tokens.coverageStart);
        const late = coverage.month !== windowStart.month || coverage.day !== windowStart.day;
        if (late) prize.append(el('span', 'caption', [`逐条记录始于 ${coverage.month} 月 ${coverage.day} 日`]));
      }
      const scratch = scratchCard({
        width: CARD_W,
        height: CARD_H,
        seed: 3103,
        label: '刮开看看',
        onProgress(ratio) {
          if (ratio > 0.2) ctx.haptic('tick');
        },
        onComplete() {
          void complete();
        },
      });
      card.append(prize, scratch.canvas);
      prize.style.visibility = 'hidden';

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
          color: 'yellow',
          seed: 3110,
          question: '调用了几次？',
          onFlip,
          reveal: () => stickyContent(groupDigits(tokens.requests), '次模型调用', null, 3111),
        }).el,
        stickyNote({
          color: 'blue',
          seed: 3112,
          question: '读得多还是写得多？',
          onFlip,
          reveal: () => stickyContent(percentText(tokens.input, tokens.input + tokens.output), '都花在了输入上', `输出 ${compactCount(tokens.output)}`, 3113),
        }).el,
      );
      if (tokens.cost.known > 0) {
        const unit = tokens.cost.currency === 'CNY' ? '元' : '美元';
        notes.push(
          stickyNote({
            color: 'pink',
            seed: 3114,
            question: '花了多少钱？',
            onFlip,
            reveal: () =>
              stickyContent(
                tokens.cost.known.toFixed(2),
                `${unit} · 按你的定价`,
                tokens.cost.unpricedRequests > 0 ? `${groupDigits(tokens.cost.unpricedRequests)} 次没有定价` : null,
                3115,
                22,
              ),
          }).el,
        );
      }
      board.append(...notes);
      frame.stage.append(el('div', 'scratch-zone', [card]), board);

      let completed = false;
      const complete = async () => {
        completed = true;
        ctx.haptic('success');
        prize.style.visibility = 'visible';
        const rect = card.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 30 });
        await play(scratch.canvas, [{ opacity: 1 }, { opacity: 0 }], { duration: 360, easing: 'ease-out' });
        scratch.canvas.style.display = 'none';
        await total.write(0, 1.1);
        board.style.display = '';
        await Promise.all(notes.map((note, index) => popIn(note, index * 110, 0)));
        ctx.prompt('翻开便利贴', 'tap');
      };

      ctx.prompt('用手指刮开涂层', 'scratch');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          scratch.paint();
          void frame.intro();
          void play(card, [{ opacity: 0, transform: 'rotate(-4deg) scale(.9)' }, { opacity: 1, transform: 'rotate(-1.5deg) scale(1)' }], { duration: 620, delay: 280, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!completed) void shake(card, 6);
          else for (const note of notes) void shake(note, 4);
        },
      };
    },
  };
}
