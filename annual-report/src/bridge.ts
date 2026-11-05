// 与 AnnualReportActivity 通信的原生桥。App 内由 addJavascriptInterface 注入，
// 开发时由 fixtures/dev-bridge.ts 注入同名对象，页面代码只有这一条调用路径。

export type HapticKind = 'tick' | 'tap' | 'success' | 'heavy';
export type PosterAction = 'save' | 'share';

export interface OperitAnnualReportBridge {
  close(): void;
  haptic(kind: string): void;
  capturePoster(action: string, left: number, top: number, width: number, height: number, ratio: number): void;
}

declare global {
  interface Window {
    OperitAnnualReport: OperitAnnualReportBridge;
    __annualReportPosterResult: (action: string, ok: boolean, message: string) => void;
  }
}

export interface PosterResult {
  ok: boolean;
  message: string;
}

let pendingPoster: ((result: PosterResult) => void) | null = null;

export function installBridgeCallbacks(): void {
  window.__annualReportPosterResult = (action, ok, message) => {
    if (pendingPoster === null) {
      console.error('[bridge] 收到未请求的海报结果', action, ok, message);
      return;
    }
    const resolve = pendingPoster;
    pendingPoster = null;
    resolve({ ok, message });
  };
}

export const bridge = {
  close(): void {
    window.OperitAnnualReport.close();
  },
  haptic(kind: HapticKind): void {
    window.OperitAnnualReport.haptic(kind);
  },
  capturePoster(action: PosterAction, rect: DOMRect): Promise<PosterResult> {
    if (pendingPoster !== null) return Promise.reject(new Error('上一张海报还在处理中'));
    return new Promise((resolve) => {
      pendingPoster = resolve;
      window.OperitAnnualReport.capturePoster(
        action,
        rect.left,
        rect.top,
        rect.width,
        rect.height,
        window.devicePixelRatio,
      );
    });
  },
};
