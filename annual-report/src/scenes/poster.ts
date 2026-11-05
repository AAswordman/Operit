import { bridge, type PosterAction } from '../bridge.ts';
import type { Deck, SceneSpec } from '../deck/deck.ts';
import { compactCount, daysBetween, groupDigits, localDate } from '../data/format.ts';
import type { ProjectReport, UserReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, nextFrame, play, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { artText, prepareStamp, stampIn } from '../sketch/artText.ts';
import { sparkle, star, tape } from '../sketch/doodles.ts';
import { handText, hideHand, writeHand } from '../sketch/handwriting.ts';
import { sketchLogo } from '../sketch/logo.ts';
import { pen } from '../sketch/pen.ts';
import { el, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { hourPersona, peakHour } from './clock.ts';

interface PosterStat {
  value: string;
  label: string;
}

function personaFor(user: UserReport): { name: string; line: string } {
  if (user.messages.user === 0) return { name: '今年还在热身', line: '等你来写下第一句话' };
  return hourPersona(peakHour(user.hours.byHour));
}

function statsFor(project: ProjectReport, user: UserReport): PosterStat[] {
  if (user.messages.user === 0 && user.tokens.total === 0) {
    return [
      { value: groupDigits(project.commits.total), label: 'Operit 的提交' },
      { value: groupDigits(project.stars.atEnd), label: 'GitHub Stars' },
      { value: groupDigits(project.releases.formal.length + project.releases.patchCount), label: '次版本更新' },
      { value: compactCount(project.market.downloads), label: '次插件下载' },
    ];
  }
  const stats: PosterStat[] = [];
  if (user.tokens.total > 0) stats.push({ value: compactCount(user.tokens.total), label: 'tokens' });
  stats.push({ value: compactCount(user.messages.user), label: '句话' });
  if (user.activity.activeDays > 0) stats.push({ value: groupDigits(user.activity.activeDays), label: '天在线' });
  if (user.tools.total > 0) stats.push({ value: compactCount(user.tools.total), label: '次工具调用' });
  if (stats.length < 4 && user.messages.chatsCreated > 0) stats.push({ value: groupDigits(user.messages.chatsCreated), label: '个对话' });
  return stats.slice(0, 4);
}

export function posterScene(project: ProjectReport, user: UserReport, deck: Deck, kicker: string): SceneSpec {
  const persona = personaFor(user);
  const stats = statsFor(project, user);
  const install = localDate(user.install.firstInstallAt);
  const together = daysBetween(install, localDate(user.window.end)) + 1;
  const topModel = user.tokens.topModels[0];
  return {
    id: 'poster',
    surface: 'paper',
    nextLabel: '这就是你的 2026',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '最后，\n盖个章{{留作纪念}}', seed: 4101 });
      const stampButton = el('button', 'rubber-stamp tappable');
      stampButton.type = 'button';
      stampButton.setAttribute('aria-label', '盖章生成海报');
      const stampArt = svgRoot(120, 120, 'stamp-art');
      stampArt.append(
        pen.rect(38, 8, 44, 46, { seed: 4102, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'hachure', hachureGap: 5, roughness: 0.8 }),
        pen.ellipse(60, 14, 50, 14, { seed: 4103, stroke: 'ink', strokeWidth: 2, fill: 'coral', fillStyle: 'solid', roughness: 0.6 }),
        pen.rect(18, 54, 84, 22, { seed: 4104, stroke: 'ink', strokeWidth: 2, fill: 'ink-soft', fillStyle: 'hachure', hachureGap: 4, roughness: 0.8 }),
        pen.rect(14, 76, 92, 26, { seed: 4105, stroke: 'ink', strokeWidth: 2.2, fill: 'paper-deep', fillStyle: 'solid', roughness: 0.7 }),
      );
      stampButton.append(stampArt, el('span', 'stamp-hint', ['按下印章']));

      // 海报本体：保存图片时原生只截取这个区域。
      const poster = el('div', 'poster');
      const posterTape = tape(90, 22, -4, 4106, 'poster-tape');
      const head = el('div', 'poster-head');
      const logo = svgRoot(40, 40, 'poster-logo');
      logo.append(sketchLogo(40, 4107));
      const headTitle = handText(`${user.year}年度报告`, { size: 22, seed: 4108, ink: 'ink', tracking: 3, ghost: false });
      head.append(logo, headTitle.svg);
      const personaName = artText(`{{${persona.name}}}`, { seed: 4109, className: 'poster-persona' });
      const personaLine = el('p', 'poster-line', [persona.line]);
      const grid = el('div', 'poster-grid');
      const statHands = stats.map((stat, index) => {
        const hand = handText(stat.value, { size: 30, seed: 4110 + index, ink: index % 2 === 0 ? 'coral' : 'lavender', tracking: 2, ghost: false });
        grid.append(el('div', 'poster-stat', [hand.svg, el('span', 'poster-stat-label', [stat.label])]));
        return hand;
      });
      const lines = el('div', 'poster-lines');
      if (topModel !== undefined) lines.append(el('p', '', ['最常找的模型 ', el('strong', '', [topModel.model])]));
      if (user.longestChat !== null) lines.append(el('p', '', ['聊得最久 ', el('strong', '', [`「${user.longestChat.title}」`])]));
      if (user.topCharacter !== null) lines.append(el('p', '', ['最常陪你的 ', el('strong', '', [user.topCharacter.name])]));
      const foot = el('div', 'poster-foot', [el('span', '', [`和 Operit 一起走过 ${groupDigits(together)} 天`]), el('span', 'poster-brand', ['OPERIT'])]);
      const decor = svgRoot(300, 60, 'poster-decor');
      decor.append(sparkle(20, 20, 8, 'yellow', 4120), star(270, 30, 9, 'coral', 4121), sparkle(240, 12, 5, 'lavender', 4122));
      poster.append(posterTape, decor, head, personaName, personaLine, grid, lines, foot);
      poster.style.display = 'none';

      const actions = el('div', 'poster-actions');
      const save = el('button', 'ink-button primary', ['保存图片']);
      const share = el('button', 'ink-button', ['分享']);
      const replay = el('button', 'ink-button ghost', ['从头再看']);
      for (const button of [save, share, replay]) button.type = 'button';
      actions.append(save, share, replay);
      const toast = el('p', 'caption poster-toast', ['']);
      actions.style.display = 'none';
      frame.stage.append(stampButton, poster, actions, toast);
      prepareStamp(personaName);
      hideHand(headTitle);
      for (const hand of statHands) hideHand(hand);

      let stamped = false;
      const stamp = async () => {
        stamped = true;
        stampButton.classList.remove('tappable');
        await play(stampButton, [
          { transform: 'translateY(0) scale(1)' },
          { transform: 'translateY(-30px) scale(1.1)', offset: 0.4 },
          { transform: 'translateY(40px) scale(.9)', offset: 0.75 },
          { transform: 'translateY(40px) scale(.9)', opacity: 0 },
        ], { duration: 620, easing: EASE.inOut });
        ctx.haptic('heavy');
        stampButton.style.display = 'none';
        poster.style.display = '';
        void shake(ctx.page, 5);
        const rect = poster.getBoundingClientRect();
        burst(rect.left + rect.width / 2, rect.top + 60, { count: 40, spread: Math.PI * 1.4 });
        await play(poster, [
          { opacity: 0, transform: 'scale(1.25) rotate(-6deg)' },
          { opacity: 1, transform: 'scale(.97) rotate(1deg)', offset: 0.7 },
          { opacity: 1, transform: 'scale(1) rotate(-1deg)' },
        ], { duration: 520, easing: EASE.out });
        await writeHand(headTitle, 2.6);
        await stampIn(personaName, 0, 50);
        await Promise.all(statHands.map((hand, index) => writeHand(hand, 3, index * 140)));
        actions.style.display = '';
        await play(actions, [{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'translateY(0)' }], { duration: 420, easing: EASE.outExpo });
        ctx.haptic('success');
        ctx.reveal();
      };
      onTap(stampButton, () => {
        if (!stamped) void stamp();
      }, { once: true });

      let capturing = false;
      const capture = async (action: PosterAction) => {
        if (capturing) return;
        capturing = true;
        ctx.page.classList.add('is-capturing');
        // 等两帧，确保按钮和提示已经从画面上消失再截图。
        await nextFrame();
        await nextFrame();
        try {
          const result = await bridge.capturePoster(action, poster.getBoundingClientRect());
          toast.textContent = result.message;
          if (result.ok) ctx.haptic('success');
        } catch (error) {
          console.error('[poster] 截图失败', error);
          toast.textContent = error instanceof Error ? error.message : String(error);
        } finally {
          ctx.page.classList.remove('is-capturing');
          capturing = false;
          void play(toast, [{ opacity: 0 }, { opacity: 1 }], { duration: 300, easing: 'ease-out' });
        }
      };
      onTap(save, () => void capture('save'));
      onTap(share, () => void capture('share'));
      onTap(replay, () => deck.jumpTo(0));

      ctx.prompt('按下印章', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          void play(stampButton, [{ opacity: 0, transform: 'translateY(-40px) rotate(-10deg)' }, { opacity: 1, transform: 'translateY(0) rotate(0)' }], { duration: 620, delay: 300, easing: EASE.outBack });
        },
        leave() {},
        nudge() {
          if (!stamped) void shake(stampButton, 6);
        },
      };
    },
  };
}

/** 今年几乎没有用过的用户：不硬凑数字，留一页安静的话。 */
export function quietScene(kicker: string): SceneSpec {
  return {
    id: 'quiet',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: '今年，我们还没\n{{好好聊过}}', seed: 4201 });
      const moon = svgRoot(160, 140, 'quiet-moon');
      moon.append(
        pen.path('M 96 20 C 60 24, 40 56, 48 88 C 56 118, 90 132, 120 118 C 92 116, 70 96, 70 66 C 70 44, 80 28, 96 20 Z', { seed: 4202, stroke: 'ink', strokeWidth: 2, fill: 'yellow', fillStyle: 'hachure', hachureGap: 4, roughness: 0.8 }),
        sparkle(30, 30, 7, 'lavender', 4203),
        sparkle(132, 60, 5, 'coral', 4204),
        star(24, 104, 6, 'mint', 4205),
      );
      const lead = el('p', 'lead quiet-lead', ['这一页先空着。等你来写下第一句话，明年的这本手账，会被写满的。']);
      frame.stage.append(el('div', 'quiet-zone', [moon, lead]));
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro().then(() => ctx.reveal());
          void play(moon, [{ opacity: 0, transform: 'translateY(10px) rotate(-8deg)' }, { opacity: 1, transform: 'translateY(0) rotate(0)' }], { duration: 800, delay: 300, easing: EASE.outBack });
        },
        leave() {},
        nudge() {},
      };
    },
  };
}
