import { defineConfig, type Plugin, type ViteDevServer } from 'vite';
import type { IncomingMessage, ServerResponse } from 'node:http';

// 开发时用 fixtures/ 下的模拟数据模拟 App 内的原生拦截路径。
// 生产构建不包含这些文件：/report-data/ 由 AnnualReportActivity 的 PathHandler 返回真实数据。
// 通过页面地址的查询参数切换夹具，例如 ?theme=dark&fixture=empty&market=error。
function devReportData(): Plugin {
  const fixtureQuery = (req: IncomingMessage): URLSearchParams => {
    const referer = req.headers.referer;
    if (referer === undefined) return new URLSearchParams();
    return new URL(referer).searchParams;
  };

  const sendJson = (res: ServerResponse, status: number, value: unknown): void => {
    res.statusCode = status;
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify(value));
  };

  const loadFixture = async (server: ViteDevServer, path: string, exportName: string): Promise<unknown> => {
    const module = await server.ssrLoadModule(path);
    return module[exportName];
  };

  return {
    name: 'operit-annual-report-dev-data',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/report-data/user.json', (req, res) => {
        const query = fixtureQuery(req);
        const fixture = query.get('fixture');
        const delay = Number.parseInt(query.get('delay') ?? '600', 10);
        setTimeout(() => {
          if (fixture === 'error') {
            sendJson(res, 500, { message: '模拟错误：读取 messages 表失败' });
            return;
          }
          const exportName = fixture === 'empty' ? 'emptyUserReport' : fixture === 'guest' ? 'guestUserReport' : 'userReport';
          loadFixture(server, '/fixtures/user-2026.ts', exportName)
            .then((value) => sendJson(res, 200, value))
            .catch((error: unknown) => {
              console.error(error);
              sendJson(res, 500, { message: String(error) });
            });
        }, delay);
      });
      server.middlewares.use('/report-data/market.json', (req, res) => {
        const query = fixtureQuery(req);
        setTimeout(() => {
          if (query.get('market') === 'error') {
            sendJson(res, 500, { message: '网络不可用，无法读取插件市场' });
            return;
          }
          loadFixture(server, '/fixtures/user-2026.ts', 'personalMarket')
            .then((value) => sendJson(res, 200, value))
            .catch((error: unknown) => {
              console.error(error);
              sendJson(res, 500, { message: String(error) });
            });
        }, 700);
      });
    },
    transformIndexHtml: {
      order: 'pre',
      handler(html) {
        return html.replace(
          '<script type="module" src="./src/main.ts"></script>',
          '<script type="module" src="/fixtures/dev-bridge.ts"></script>\n    <script type="module" src="./src/main.ts"></script>',
        );
      },
    },
  };
}

export default defineConfig({
  base: './',
  plugins: [devReportData()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    target: 'es2020',
    assetsInlineLimit: 0,
  },
  server: {
    host: '127.0.0.1',
    port: 5180,
    strictPort: true,
  },
});
