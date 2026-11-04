const TOKEN_KEY = 'operit-web-chat-token';

export function readStoredToken() {
  if (typeof window === 'undefined') {
    return '';
  }
  return window.localStorage.getItem(TOKEN_KEY) ?? '';
}

export function writeStoredToken(token: string) {
  if (typeof window === 'undefined') {
    return;
  }

  if (!token.trim()) {
    window.localStorage.removeItem(TOKEN_KEY);
    return;
  }

  window.localStorage.setItem(TOKEN_KEY, token.trim());
}

export function clearStoredToken() {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(TOKEN_KEY);
}

export function readInitialToken(): string {
  if (typeof window === 'undefined') {
    return '';
  }

  const url = new URL(window.location.href);
  const suppliedToken = url.searchParams.get('token');
  if (suppliedToken !== null) {
    // 登录参数只消费一次，保留其他查询参数与锚点，并清除地址栏中的凭据。
    url.searchParams.delete('token');
    window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
    return suppliedToken.trim();
  }
  return readStoredToken();
}
