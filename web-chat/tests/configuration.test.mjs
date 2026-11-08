import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { clearStoredToken, readInitialToken, readStoredToken, writeStoredToken } from '../src/ui/features/chat/util/ConfigurationStateHolder.ts';

const TOKEN_KEY = 'operit-web-chat-token';

function browser(href, savedToken = 'previous-token') {
  const storage = new Map([[TOKEN_KEY, savedToken]]);
  const historyState = { retained: true };
  globalThis.window = {
    location: new URL(href),
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key)
    },
    history: {
      state: historyState,
      replaceState(state, _title, relativeUrl) {
        assert.equal(state, historyState);
        window.location = new URL(relativeUrl, window.location);
      }
    }
  };
  return storage;
}

afterEach(() => { delete globalThis.window; });

test('URL 登录令牌覆盖旧登录，并仅从地址栏移除 token', () => {
  const token = "new+token&中文?'";
  const storage = browser(`http://127.0.0.1:8080/?mode=chat&token=${encodeURIComponent(token)}#message`);
  assert.equal(readInitialToken(), token);
  assert.equal(window.location.href, 'http://127.0.0.1:8080/?mode=chat#message');
  assert.equal(storage.get(TOKEN_KEY), 'previous-token');
  writeStoredToken(token);
  assert.equal(readStoredToken(), token);
  assert.equal(readInitialToken(), token);
});

test('没有 URL 令牌时继续使用已保存的手动登录', () => {
  browser('http://127.0.0.1:8094/?mode=chat');
  assert.equal(readInitialToken(), 'previous-token');
  assert.equal(window.location.href, 'http://127.0.0.1:8094/?mode=chat');
  clearStoredToken();
  assert.equal(readInitialToken(), '');
});

test('明确传入的空令牌不继承旧账号令牌', () => {
  browser('http://127.0.0.1:8094/?token=');
  assert.equal(readInitialToken(), '');
  assert.equal(window.location.search, '');
});

test('非浏览器环境不读取位置或本地存储', () => {
  assert.equal(readInitialToken(), '');
  assert.equal(readStoredToken(), '');
});
