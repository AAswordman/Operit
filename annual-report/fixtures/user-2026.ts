// 开发用的模拟用户数据，只由 vite.config.ts 的开发中间件返回，不进入构建产物。
// 每日活跃度用固定 seed 生成，保证每次预览看到的是同一份"年度"。

import type { DailyActivity, PersonalMarket, UserReport } from '../src/data/types.ts';

function random(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

const COVERAGE_START = '2026-04-20';

function buildDays(): DailyActivity[] {
  const next = random(2026);
  const days: DailyActivity[] = [];
  const start = Date.UTC(2026, 0, 1);
  const end = Date.UTC(2026, 10, 5);
  for (let time = start; time <= end; time += 86_400_000) {
    const date = new Date(time);
    const key = `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
    const weekday = date.getUTCDay();
    const season = 0.6 + 0.4 * Math.sin((date.getUTCMonth() / 12) * Math.PI * 2 - 1);
    const active = next() < (weekday === 0 || weekday === 6 ? 0.82 : 0.68);
    if (!active) continue;
    const messages = Math.max(1, Math.round((6 + next() * 54) * season * (next() < 0.06 ? 3.2 : 1)));
    const tokens = key >= COVERAGE_START ? Math.round(messages * (1800 + next() * 3600)) : 0;
    days.push({ date: key, messages, tokens });
  }
  return days;
}

function summarize(days: DailyActivity[]) {
  let longest = { days: 0, start: '', end: '' };
  let runStart = '';
  let runLength = 0;
  let previous = Number.NaN;
  let busiest = { date: '', messages: 0 };
  for (const day of days) {
    const [y, m, d] = day.date.split('-').map(Number);
    const ordinal = Date.UTC(y, m - 1, d) / 86_400_000;
    if (ordinal === previous + 1) {
      runLength += 1;
    } else {
      runLength = 1;
      runStart = day.date;
    }
    previous = ordinal;
    if (runLength > longest.days) longest = { days: runLength, start: runStart, end: day.date };
    if (day.messages > busiest.messages) busiest = { date: day.date, messages: day.messages };
  }
  return { longest, busiest };
}

const days = buildDays();
const summary = summarize(days);
const tokenByMonth = Array.from({ length: 12 }, () => 0);
for (const day of days) {
  const month = Number(day.date.slice(5, 7)) - 1;
  tokenByMonth[month] += day.tokens;
}
const tokenTotal = tokenByMonth.reduce((sum, value) => sum + value, 0);

export const userReport: UserReport = {
  schemaVersion: 1,
  year: 2026,
  generatedAt: '2026-11-05T21:30:00+08:00',
  window: { start: '2026-01-01T00:00:00+08:00', end: '2026-11-05T21:30:00+08:00', timeZone: 'Asia/Shanghai' },
  install: { firstInstallAt: '2025-08-14T21:37:12+08:00' },
  github: { login: 'luojiaping', id: 10000001 },
  tokens: {
    coverageStart: `${COVERAGE_START}T09:12:44+08:00`,
    total: tokenTotal,
    input: Math.round(tokenTotal * 0.84),
    output: Math.round(tokenTotal * 0.16),
    cachedInput: Math.round(tokenTotal * 0.33),
    requests: 4812,
    cost: { currency: 'CNY', known: 86.42, unpricedRequests: 312 },
    byMonth: tokenByMonth,
    topModels: [
      { model: 'deepseek-chat', tokens: Math.round(tokenTotal * 0.52), requests: 2402 },
      { model: 'gemini-2.5-pro', tokens: Math.round(tokenTotal * 0.27), requests: 1120 },
      { model: 'claude-sonnet-4.5', tokens: Math.round(tokenTotal * 0.12), requests: 640 },
    ],
  },
  messages: { user: 6840, ai: 7412, chatsCreated: 486, regenerations: 214, favorites: 37 },
  activity: {
    days,
    activeDays: days.filter((day) => day.messages > 0).length,
    longestStreak: summary.longest,
    busiestDay: summary.busiest,
  },
  hours: {
    byHour: [62, 31, 14, 6, 2, 1, 4, 18, 96, 210, 302, 288, 196, 240, 318, 342, 300, 268, 230, 346, 498, 612, 704, 410],
    latestNight: '2026-03-14T03:42:18+08:00',
  },
  firstMessage: {
    at: '2026-01-01T00:07:31+08:00',
    text: '新年快乐！帮我把今年的目标整理成一张清单吧：读完 24 本书、学会游泳、把 Operit 的工作流全部用起来。',
    chatTitle: '2026 新年计划',
  },
  longestChat: { title: '毕业论文陪跑计划', messages: 1284 },
  topCharacter: { name: '小夜', messages: 2316, chats: 58 },
  aiWork: { durationMs: 149_160_000, longestReplyTokens: 12804, coverageStart: `${COVERAGE_START}T09:12:44+08:00` },
  tools: {
    total: 9436,
    top: [
      { name: 'read_file', count: 2140 },
      { name: 'web_search', count: 1804 },
      { name: 'execute_shell', count: 1212 },
      { name: 'visit_web', count: 903 },
      { name: 'tap', count: 611 },
    ],
  },
  creations: { memories: 214, characterCards: 6, workflows: 9 },
};

export const guestUserReport: UserReport = { ...userReport, github: null };

export const emptyUserReport: UserReport = {
  ...userReport,
  install: { firstInstallAt: '2026-11-03T10:12:00+08:00' },
  github: null,
  tokens: {
    coverageStart: null,
    total: 0,
    input: 0,
    output: 0,
    cachedInput: 0,
    requests: 0,
    cost: { currency: 'CNY', known: 0, unpricedRequests: 0 },
    byMonth: Array.from({ length: 12 }, () => 0),
    topModels: [],
  },
  messages: { user: 0, ai: 0, chatsCreated: 0, regenerations: 0, favorites: 0 },
  activity: { days: [], activeDays: 0, longestStreak: null, busiestDay: null },
  hours: { byHour: Array.from({ length: 24 }, () => 0), latestNight: null },
  firstMessage: null,
  longestChat: null,
  topCharacter: null,
  aiWork: { durationMs: 0, longestReplyTokens: 0, coverageStart: null },
  tools: { total: 0, top: [] },
  creations: { memories: 0, characterCards: 0, workflows: 0 },
};

export const personalMarket: PersonalMarket = {
  entries: 3,
  downloads: 12480,
  likes: 96,
  titles: ['夜间护眼助手', '番茄钟工作流', '每日一句'],
};
