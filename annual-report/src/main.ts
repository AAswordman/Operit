import './styles/base.css';
import './styles/components.css';
import './styles/scenes.css';
import './styles/personal.css';
import { bridge, installBridgeCallbacks } from './bridge.ts';
import { loadProject, loadUser } from './data/load.ts';
import type { UserReport } from './data/types.ts';
import { Deck } from './deck/deck.ts';
import { installSketchDefs } from './sketch/defs.ts';
import { el } from './sketch/svg.ts';
import { buildPersonalScenes, buildProjectScenes } from './scenes/index.ts';

function readParams(): { theme: 'light' | 'dark'; year: number } {
  const params = new URLSearchParams(window.location.search);
  const theme = params.get('theme');
  if (theme !== 'light' && theme !== 'dark') throw new Error(`页面地址缺少有效的 theme 参数：${String(theme)}`);
  const yearText = params.get('year');
  if (yearText === null) throw new Error('页面地址缺少 year 参数');
  const year = Number.parseInt(yearText, 10);
  if (!Number.isInteger(year)) throw new Error(`year 参数无效：${yearText}`);
  return { theme, year };
}

/** 启动失败时直接把原因写在纸上，不显示任何替代内容。 */
function renderFatal(host: HTMLElement, error: unknown): void {
  console.error('[annual-report] 启动失败', error);
  const message = error instanceof Error ? error.message : String(error);
  const closeButton = el('button', 'ink-button', ['关闭']);
  closeButton.type = 'button';
  closeButton.addEventListener('click', () => bridge.close());
  host.replaceChildren(
    el('div', 'fatal surface-paper', [
      el('h1', 'fatal-title', ['报告没能打开']),
      el('p', 'fatal-message', [message]),
      closeButton,
    ]),
  );
}

async function boot(): Promise<void> {
  const host = document.getElementById('app');
  if (host === null) throw new Error('缺少 #app 容器');
  try {
    const { theme, year } = readParams();
    document.documentElement.dataset.theme = theme;
    installSketchDefs();
    installBridgeCallbacks();
    const project = await loadProject(year);
    // 用户数据在原生侧采集需要时间，和项目篇并行加载；读到过渡页时再等待它。
    const request = (): Promise<UserReport> => {
      const pending = loadUser(year);
      pending.catch((error: unknown) => console.error('[annual-report] 用户数据加载失败', error));
      return pending;
    };
    let pendingUser = request();
    const deck = new Deck(host, {
      onClose: () => bridge.close(),
      haptic: (kind) => bridge.haptic(kind),
    });
    deck.append(
      buildProjectScenes(project, {
        current: () => pendingUser,
        retry: () => {
          pendingUser = request();
          return pendingUser;
        },
        onReady: (user) => deck.append(buildPersonalScenes(project, user, deck)),
      }),
    );
    deck.start();
  } catch (error) {
    renderFatal(host, error);
  }
}

void boot();
