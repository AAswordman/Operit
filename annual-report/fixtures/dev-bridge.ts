// 开发时代替 AnnualReportActivity 注入的原生桥，只在 vite 开发服务器里加载。

import type { OperitAnnualReportBridge } from '../src/bridge.ts';

const devBridge: OperitAnnualReportBridge = {
  close() {
    console.info('[dev-bridge] close');
  },
  haptic(kind) {
    console.info('[dev-bridge] haptic', kind);
  },
  capturePoster(action, left, top, width, height, ratio) {
    console.info('[dev-bridge] capturePoster', action, { left, top, width, height, ratio });
    setTimeout(() => {
      window.__annualReportPosterResult(action, true, action === 'save' ? '已保存到相册' : '已打开分享');
    }, 500);
  },
};

window.OperitAnnualReport = devBridge;
