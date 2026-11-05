import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, groupDigits, localDate, localTime, pad2 } from '../data/format.ts';
import type { FirstMessage, UserReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, popIn, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { star } from '../sketch/doodles.ts';
import { handText, hideHand, writeHand } from '../sketch/handwriting.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { stickyNote, type StickyColor } from '../ui/sticky.ts';
import { stickyContent } from './shared.ts';

const ENV_W = 300;
const ENV_H = 190;

/** 年度第一句话：拆开信封的蜡封，信纸滑出来。 */
export function firstWordsScene(user: UserReport, message: FirstMessage, kicker: string): SceneSpec {
  const date = localDate(message.at);
  const time = localTime(message.at);
  return {
    id: 'first-words',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: `${user.year} 年，\n你对我说的{{第一句话}}`, seed: 3501 });
      const envelope = el('div', 'envelope');
      const letter = el('div', 'letter');
      const stamp = handText(`${date.month}月${date.day}日`, { size: 26, seed: 3502, ink: 'coral', tracking: 2, ghost: false });
      const clock = el('span', 'letter-time', [`${pad2(time.hour)}:${pad2(time.minute)}`]);
      const text = el('p', 'letter-text', [message.text]);
      const from = el('p', 'letter-from', [`—— 写在「${message.chatTitle}」里`]);
      letter.append(el('div', 'letter-head', [stamp.svg, clock]), text, from);
      const back = svgRoot(ENV_W, ENV_H, 'envelope-back');
      back.append(pen.rect(4, 4, ENV_W - 8, ENV_H - 8, { seed: 3503, stroke: 'ink', strokeWidth: 2, fill: 'paper-deep', fillStyle: 'solid', roughness: 0.8 }));
      const front = svgRoot(ENV_W, ENV_H, 'envelope-front');
      front.append(
        pen.polygon([[4, 60], [ENV_W / 2, 130], [ENV_W - 4, 60], [ENV_W - 4, ENV_H - 4], [4, ENV_H - 4]], { seed: 3504, stroke: 'ink', strokeWidth: 2, fill: 'paper-deep', fillStyle: 'solid', roughness: 0.8 }),
        pen.line(4, ENV_H - 4, ENV_W / 2, 118, { seed: 3505, stroke: 'pencil', strokeWidth: 1.2, roughness: 1 }),
        pen.line(ENV_W - 4, ENV_H - 4, ENV_W / 2, 118, { seed: 3506, stroke: 'pencil', strokeWidth: 1.2, roughness: 1 }),
      );
      const flap = svgRoot(ENV_W, ENV_H, 'envelope-flap');
      flap.append(pen.polygon([[4, 4], [ENV_W - 4, 4], [ENV_W / 2, 118]], { seed: 3507, stroke: 'ink', strokeWidth: 2, fill: 'paper', fillStyle: 'solid', roughness: 0.8 }));
      const seal = el('button', 'wax-seal tappable');
      seal.type = 'button';
      seal.setAttribute('aria-label', '拆开蜡封');
      const sealArt = svgRoot(64, 64);
      sealArt.append(pen.circle(32, 32, 52, { seed: 3508, stroke: 'ink', strokeWidth: 1.6, fill: 'coral', fillStyle: 'solid', roughness: 1.4 }), star(32, 32, 12, 'paper', 3509));
      seal.append(sealArt);
      envelope.append(back, letter, front, flap, seal);
      frame.stage.append(envelope);
      hideHand(stamp);
      letter.style.visibility = 'hidden';

      let opened = false;
      const open = async () => {
        opened = true;
        seal.classList.remove('tappable');
        ctx.haptic('heavy');
        const rect = seal.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 16, inks: ['coral', 'yellow'], speed: 0.45 });
        await play(seal, [{ transform: 'scale(1) rotate(0)', opacity: 1 }, { transform: 'scale(1.3) rotate(40deg) translateY(-40px)', opacity: 0 }], { duration: 420, easing: EASE.out });
        seal.style.display = 'none';
        await play(flap, [{ transform: 'rotateX(0deg)' }, { transform: 'rotateX(178deg)' }], { duration: 560, easing: EASE.inOut });
        flap.style.zIndex = '0';
        letter.style.visibility = 'visible';
        await play(letter, [{ transform: 'translateY(60px)' }, { transform: 'translateY(-142px)' }], { duration: 720, easing: EASE.outBack });
        await writeHand(stamp, 1.4);
        ctx.haptic('success');
        ctx.reveal();
      };
      onTap(seal, () => {
        if (!opened) void open();
      }, { once: true });

      ctx.prompt('点一下蜡封', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(envelope, [{ opacity: 0, transform: 'translateY(30px) rotate(-6deg)' }, { opacity: 1, transform: 'translateY(0) rotate(-2deg)' }], { duration: 660, delay: 240, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!opened) void shake(seal, 6);
        },
      };
    },
  };
}

interface WallNote {
  color: StickyColor;
  question: string;
  value: string;
  label: string;
  detail: string | null;
}

/** 便利贴墙：一次翻一张，聊天里的几个之最。 */
export function chatsScene(user: UserReport, kicker: string): SceneSpec {
  const notes: WallNote[] = [];
  const messages = user.messages;
  notes.push({ color: 'yellow', question: '开了几个对话？', value: groupDigits(messages.chatsCreated), label: '个新对话', detail: null });
  notes.push({ color: 'blue', question: '你说了多少句？', value: compactCount(messages.user), label: '句是你说的', detail: `AI 回了 ${compactCount(messages.ai)} 句` });
  if (user.longestChat !== null) {
    notes.push({ color: 'pink', question: '聊得最久的是？', value: groupDigits(user.longestChat.messages), label: '条消息', detail: `「${user.longestChat.title}」` });
  }
  if (user.topCharacter !== null) {
    notes.push({ color: 'lavender', question: '最常聊的角色？', value: groupDigits(user.topCharacter.messages), label: `条消息给了「${user.topCharacter.name}」`, detail: `${groupDigits(user.topCharacter.chats)} 个对话` });
  }
  if (messages.regenerations > 0) {
    notes.push({ color: 'mint', question: '重新生成了几次？', value: groupDigits(messages.regenerations), label: '次不满意，再来', detail: null });
  }
  if (messages.favorites > 0) {
    notes.push({ color: 'yellow', question: '收藏了几条？', value: groupDigits(messages.favorites), label: '条舍不得忘', detail: null });
  }
  return {
    id: 'chats',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '聊过的那些，\n都{{贴在这里}}了', seed: 3601 });
      const board = el('div', 'sticky-board wall');
      let flipped = 0;
      const stickies = notes.map((note, index) =>
        stickyNote({
          color: note.color,
          seed: 3610 + index * 3,
          question: note.question,
          onFlip: () => {
            flipped += 1;
            ctx.haptic('tap');
            if (flipped === notes.length) {
              const rect = board.getBoundingClientRect();
              burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 22 });
              ctx.haptic('success');
              ctx.reveal();
            }
          },
          reveal: () => stickyContent(note.value, note.label, note.detail, 3611 + index * 3, 28),
        }).el,
      );
      board.append(...stickies);
      frame.stage.append(board);
      for (const sticky of stickies) sticky.style.opacity = '0';

      ctx.prompt('一张一张翻开', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          stickies.forEach((sticky, index) => {
            void popIn(sticky, 260 + index * 90, Number.parseFloat(sticky.style.getPropertyValue('--tilt')));
          });
        },
        leave() {},
        nudge() {
          for (const sticky of stickies) if (!sticky.classList.contains('is-flipped')) void shake(sticky, 4);
        },
      };
    },
  };
}
