import type { SceneSpec } from '../deck/deck.ts';
import { compactCount, groupDigits, localDate } from '../data/format.ts';
import { loadPersonalMarket, loadStargazers } from '../data/load.ts';
import type { GitHubIdentity, ProjectReport, UserReport } from '../data/types.ts';
import { onTap } from '../interact/press.ts';
import { EASE, play, shake } from '../motion/anim.ts';
import { burst } from '../motion/particles.ts';
import { star } from '../sketch/doodles.ts';
import { el, svgRoot } from '../sketch/svg.ts';
import { sceneFrame } from '../ui/frame.ts';
import { written } from '../ui/number.ts';

interface Item {
  root: HTMLElement;
  done: boolean;
}

function addDays(epoch: string, days: number): { year: number; month: number; day: number } {
  const start = localDate(epoch);
  const date = new Date(Date.UTC(start.year, start.month - 1, start.day) + days * 86_400_000);
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

/** 登录了 GitHub 的用户才有这一页：你在 stargazer 名单里的位置、贡献者身份、市场作品。 */
export function githubScene(project: ProjectReport, user: UserReport, github: GitHubIdentity, kicker: string): SceneSpec {
  const login = github.login;
  const isContributor = project.contributors.logins.some((name) => name.toLowerCase() === login.toLowerCase());
  return {
    id: 'github',
    surface: 'paper',
    build(ctx) {
      const frame = sceneFrame(ctx, { kicker, title: `@${login}，\n你和 Operit 的{{缘分}}`, seed: 4001 });
      const list = el('div', 'bond-list');
      const items: Item[] = [];
      const finishOne = (item: Item) => {
        item.done = true;
        item.root.classList.remove('tappable');
        if (items.every((entry) => entry.done)) {
          ctx.haptic('success');
          ctx.reveal();
        }
      };

      // 1. 你是第几颗星
      const starCard = el('div', 'bond-card tappable');
      const starArt = svgRoot(64, 64, 'bond-icon');
      starArt.append(star(32, 32, 26, 'pencil', 4002, false));
      const starBody = el('div', 'bond-body', [el('span', 'bond-title', ['你是第几颗星？']), el('span', 'caption', ['点一下，在 stargazer 名单里找找你'])]);
      starCard.append(starArt, starBody);
      const starItem: Item = { root: starCard, done: false };
      items.push(starItem);
      onTap(starCard, () => {
        if (starItem.done || starCard.classList.contains('is-busy')) return;
        starCard.classList.add('is-busy');
        ctx.haptic('tap');
        loadStargazers(user.year).then(
          (snapshot) => {
            starCard.classList.remove('is-busy');
            const index = snapshot.logins.findIndex((name) => name.toLowerCase() === login.toLowerCase());
            if (index < 0) {
              starBody.replaceChildren(el('span', 'bond-title', ['名单里还没有你的星星']), el('span', 'caption', [`截至 ${localDate(project.window.end).month} 月 ${localDate(project.window.end).day} 日的 ${groupDigits(snapshot.logins.length)} 位 stargazer 里没找到 @${login}`]));
              finishOne(starItem);
              return;
            }
            const date = addDays(snapshot.epoch, snapshot.days[index]);
            const rank = written(groupDigits(index + 1), { size: 34, seed: 4003, ink: 'coral', prefix: '第', unit: '颗星' });
            starArt.replaceChildren(star(32, 32, 26, 'yellow', 4004, true));
            starBody.replaceChildren(rank.el, el('span', 'caption', [`${date.year} 年 ${date.month} 月 ${date.day} 日，你点亮了它`]));
            const rect = starArt.getBoundingClientRect();
            burst(rect.left + rect.width / 2, rect.top + rect.height / 2, { count: 18, inks: ['yellow', 'coral'] });
            void rank.write(0, 1.3);
            finishOne(starItem);
          },
          (error: unknown) => {
            console.error('[github] stargazer 名单加载失败', error);
            starCard.classList.remove('is-busy');
            starBody.replaceChildren(el('span', 'bond-title', ['名单没能打开']), el('span', 'caption', [error instanceof Error ? error.message : String(error)]));
            finishOne(starItem);
          },
        );
      });
      list.append(starCard);

      // 2. 贡献者
      if (isContributor) {
        const badge = el('div', 'bond-card tappable');
        const badgeArt = svgRoot(64, 64, 'bond-icon');
        badgeArt.append(star(32, 32, 24, 'lavender', 4010, false));
        const badgeBody = el('div', 'bond-body', [el('span', 'bond-title', ['你也写过 Operit 的代码？']), el('span', 'caption', ['点一下盖个章'])]);
        badge.append(badgeArt, badgeBody);
        const badgeItem: Item = { root: badge, done: false };
        items.push(badgeItem);
        onTap(badge, () => {
          if (badgeItem.done) return;
          ctx.haptic('heavy');
          badgeArt.replaceChildren(star(32, 32, 24, 'lavender', 4011, true));
          badgeBody.replaceChildren(el('span', 'bond-title', [`今年的 ${project.contributors.total} 位贡献者里，有你`]), el('span', 'caption', ['谢谢你让 Operit 变得更好']));
          void play(badge, [{ transform: 'scale(1.08) rotate(-2deg)' }, { transform: 'scale(1) rotate(0)' }], { duration: 420, easing: EASE.outBack });
          finishOne(badgeItem);
        }, { once: true });
        list.append(badge);
      }

      // 3. 插件市场作品：需要联网，按需向原生请求，失败时就地显示原因并允许重试。
      const market = el('div', 'bond-card tappable');
      const marketArt = svgRoot(64, 64, 'bond-icon');
      marketArt.append(star(32, 32, 24, 'mint', 4020, false));
      const marketBody = el('div', 'bond-body', [el('span', 'bond-title', ['你在插件市场的作品']), el('span', 'caption', ['点一下，向市场打听打听'])]);
      market.append(marketArt, marketBody);
      const marketItem: Item = { root: market, done: false };
      items.push(marketItem);
      const askMarket = () => {
        if (market.classList.contains('is-busy')) return;
        market.classList.add('is-busy');
        marketBody.replaceChildren(el('span', 'bond-title', ['正在打听……']));
        ctx.haptic('tap');
        loadPersonalMarket().then(
          (result) => {
            market.classList.remove('is-busy');
            if (result.entries === 0) {
              marketBody.replaceChildren(el('span', 'bond-title', ['你还没有在市场发布作品']), el('span', 'caption', ['也许明年，这里会写上你的名字']));
            } else {
              const downloads = written(compactCount(result.downloads), { size: 30, seed: 4021, ink: 'mint', unit: '次下载' });
              marketBody.replaceChildren(
                el('span', 'bond-title', [`${result.entries} 个作品 · ${groupDigits(result.likes)} 个赞`]),
                downloads.el,
                el('span', 'caption', [result.titles.join('、')]),
              );
              marketArt.replaceChildren(star(32, 32, 24, 'mint', 4022, true));
              void downloads.write(0, 1.3);
            }
            finishOne(marketItem);
          },
          (error: unknown) => {
            console.error('[github] 个人市场数据加载失败', error);
            market.classList.remove('is-busy');
            const retry = el('button', 'ink-button small', ['再试一次']);
            retry.type = 'button';
            onTap(retry, askMarket, { once: true });
            marketBody.replaceChildren(el('span', 'bond-title', ['没能连上插件市场']), el('span', 'caption', [error instanceof Error ? error.message : String(error)]), retry);
            finishOne(marketItem);
          },
        );
      };
      onTap(market, () => {
        if (!marketItem.done) askMarket();
      });
      list.append(market);
      frame.stage.append(list);
      for (const item of items) item.root.style.opacity = '0';

      ctx.prompt('一项一项点开', 'tap');
      let entered = false;
      return {
        enter() {
          if (entered) return;
          entered = true;
          void frame.intro();
          items.forEach((item, index) => {
            void play(item.root, [{ opacity: 0, transform: 'translateX(-24px)' }, { opacity: 1, transform: 'translateX(0)' }], { duration: 520, delay: 300 + index * 120, easing: EASE.outExpo });
          });
        },
        leave() {},
        nudge() {
          for (const item of items) if (!item.done) void shake(item.root, 5);
        },
      };
    },
  };
}
