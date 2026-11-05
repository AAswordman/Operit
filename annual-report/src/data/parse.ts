// 运行时校验两份数据，把 Kotlin 与 TypeScript 之间的字段漂移暴露成明确的错误，
// 而不是在某个场景里悄悄显示 undefined。

import type {
  ChatHighlight,
  CharacterHighlight,
  CostCurrency,
  DailyActivity,
  FirstMessage,
  GitHubIdentity,
  MarketTopEntry,
  MarketType,
  MarketTypeCounts,
  ModelUsage,
  PersonalMarket,
  ProjectReport,
  ReleaseItem,
  ReportWindow,
  StargazerSnapshot,
  StreakRange,
  ToolUsage,
  UserReport,
} from './types.ts';

type Record_ = Record<string, unknown>;

class SchemaError extends Error {
  constructor(path: string, expected: string, value: unknown) {
    super(`数据字段 ${path} 应为 ${expected}，实际为 ${JSON.stringify(value)}`);
    this.name = 'SchemaError';
  }
}

function obj(value: unknown, path: string): Record_ {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new SchemaError(path, '对象', value);
  return value as Record_;
}

function num(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new SchemaError(path, '数字', value);
  return value;
}

function str(value: unknown, path: string): string {
  if (typeof value !== 'string') throw new SchemaError(path, '字符串', value);
  return value;
}

function arr<T>(value: unknown, path: string, item: (entry: unknown, path: string) => T): T[] {
  if (!Array.isArray(value)) throw new SchemaError(path, '数组', value);
  return value.map((entry, index) => item(entry, `${path}[${index}]`));
}

function fixed<T>(value: unknown, path: string, item: (entry: unknown, path: string) => T, length: number): T[] {
  const list = arr(value, path, item);
  if (list.length !== length) throw new SchemaError(path, `长度为 ${length} 的数组`, value);
  return list;
}

function nullable<T>(value: unknown, path: string, item: (entry: unknown, path: string) => T): T | null {
  return value === null ? null : item(value, path);
}

function schema(value: unknown, path: string): number {
  const version = num(value, path);
  if (version !== 1) throw new SchemaError(path, '1', value);
  return version;
}

const MARKET_TYPES: MarketType[] = ['script', 'package', 'skill', 'mcp'];

function marketType(value: unknown, path: string): MarketType {
  const text = str(value, path);
  const found = MARKET_TYPES.find((type) => type === text);
  if (found === undefined) throw new SchemaError(path, MARKET_TYPES.join(' | '), value);
  return found;
}

function currency(value: unknown, path: string): CostCurrency {
  const text = str(value, path);
  if (text === 'CNY' || text === 'USD') return text;
  throw new SchemaError(path, 'CNY | USD', value);
}

function window_(value: unknown, path: string): ReportWindow {
  const o = obj(value, path);
  return { start: str(o.start, `${path}.start`), end: str(o.end, `${path}.end`), timeZone: str(o.timeZone, `${path}.timeZone`) };
}

function typeCounts(value: unknown, path: string): MarketTypeCounts {
  const o = obj(value, path);
  return {
    script: num(o.script, `${path}.script`),
    package: num(o.package, `${path}.package`),
    skill: num(o.skill, `${path}.skill`),
    mcp: num(o.mcp, `${path}.mcp`),
  };
}

export function parseProjectReport(value: unknown): ProjectReport {
  const o = obj(value, 'project');
  const repository = obj(o.repository, 'project.repository');
  const commits = obj(o.commits, 'project.commits');
  const busiest = obj(commits.busiestDay, 'project.commits.busiestDay');
  const code = obj(o.codeFrequency, 'project.codeFrequency');
  const releases = obj(o.releases, 'project.releases');
  const contributors = obj(o.contributors, 'project.contributors');
  const stars = obj(o.stars, 'project.stars');
  const market = obj(o.market, 'project.market');
  return {
    schemaVersion: 1,
    year: num(o.year, 'project.year'),
    generatedAt: str(o.generatedAt, 'project.generatedAt'),
    window: window_(o.window, 'project.window'),
    repository: {
      owner: str(repository.owner, 'project.repository.owner'),
      name: str(repository.name, 'project.repository.name'),
      createdAt: str(repository.createdAt, 'project.repository.createdAt'),
    },
    commits: {
      total: num(commits.total, 'project.commits.total'),
      byMonth: fixed(commits.byMonth, 'project.commits.byMonth', num, 12),
      weekdayHour: fixed(commits.weekdayHour, 'project.commits.weekdayHour', (row, path) => fixed(row, path, num, 24), 7),
      lateNight: num(commits.lateNight, 'project.commits.lateNight'),
      busiestDay: { date: str(busiest.date, 'project.commits.busiestDay.date'), count: num(busiest.count, 'project.commits.busiestDay.count') },
    },
    codeFrequency: { additions: num(code.additions, 'project.codeFrequency.additions'), deletions: num(code.deletions, 'project.codeFrequency.deletions') },
    releases: {
      formal: arr(releases.formal, 'project.releases.formal', (entry, path): ReleaseItem => {
        const item = obj(entry, path);
        return { tag: str(item.tag, `${path}.tag`), publishedAt: str(item.publishedAt, `${path}.publishedAt`) };
      }),
      patchCount: num(releases.patchCount, 'project.releases.patchCount'),
      patchByMonth: fixed(releases.patchByMonth, 'project.releases.patchByMonth', num, 12),
    },
    contributors: {
      total: num(contributors.total, 'project.contributors.total'),
      logins: arr(contributors.logins, 'project.contributors.logins', str),
      newcomers: arr(contributors.newcomers, 'project.contributors.newcomers', (entry, path) => {
        const item = obj(entry, path);
        return { login: str(item.login, `${path}.login`), firstCommitAt: str(item.firstCommitAt, `${path}.firstCommitAt`) };
      }),
    },
    pullRequests: { merged: num(obj(o.pullRequests, 'project.pullRequests').merged, 'project.pullRequests.merged') },
    issues: {
      opened: num(obj(o.issues, 'project.issues').opened, 'project.issues.opened'),
      closed: num(obj(o.issues, 'project.issues').closed, 'project.issues.closed'),
    },
    stars: {
      atStart: num(stars.atStart, 'project.stars.atStart'),
      atEnd: num(stars.atEnd, 'project.stars.atEnd'),
      byMonth: fixed(stars.byMonth, 'project.stars.byMonth', num, 12),
    },
    market: {
      newEntries: typeCounts(market.newEntries, 'project.market.newEntries'),
      originals: typeCounts(market.originals, 'project.market.originals'),
      publishers: num(market.publishers, 'project.market.publishers'),
      newPublishers: num(market.newPublishers, 'project.market.newPublishers'),
      downloads: num(market.downloads, 'project.market.downloads'),
      topEntries: arr(market.topEntries, 'project.market.topEntries', (entry, path): MarketTopEntry => {
        const item = obj(entry, path);
        return {
          title: str(item.title, `${path}.title`),
          type: marketType(item.type, `${path}.type`),
          downloads: num(item.downloads, `${path}.downloads`),
          author: str(item.author, `${path}.author`),
        };
      }),
    },
  };
}

export function parseStargazers(value: unknown): StargazerSnapshot {
  const o = obj(value, 'stargazers');
  schema(o.schemaVersion, 'stargazers.schemaVersion');
  const logins = arr(o.logins, 'stargazers.logins', str);
  const days = arr(o.days, 'stargazers.days', num);
  if (logins.length !== days.length) throw new SchemaError('stargazers.days', `长度 ${logins.length}`, days.length);
  return {
    schemaVersion: 1,
    repository: str(o.repository, 'stargazers.repository'),
    epoch: str(o.epoch, 'stargazers.epoch'),
    logins,
    days,
  };
}

function modelUsage(value: unknown, path: string): ModelUsage {
  const o = obj(value, path);
  return { model: str(o.model, `${path}.model`), tokens: num(o.tokens, `${path}.tokens`), requests: num(o.requests, `${path}.requests`) };
}

function daily(value: unknown, path: string): DailyActivity {
  const o = obj(value, path);
  return { date: str(o.date, `${path}.date`), messages: num(o.messages, `${path}.messages`), tokens: num(o.tokens, `${path}.tokens`) };
}

function streak(value: unknown, path: string): StreakRange {
  const o = obj(value, path);
  return { days: num(o.days, `${path}.days`), start: str(o.start, `${path}.start`), end: str(o.end, `${path}.end`) };
}

function github(value: unknown, path: string): GitHubIdentity {
  const o = obj(value, path);
  return { login: str(o.login, `${path}.login`), id: num(o.id, `${path}.id`) };
}

function firstMessage(value: unknown, path: string): FirstMessage {
  const o = obj(value, path);
  return { at: str(o.at, `${path}.at`), text: str(o.text, `${path}.text`), chatTitle: str(o.chatTitle, `${path}.chatTitle`) };
}

function chatHighlight(value: unknown, path: string): ChatHighlight {
  const o = obj(value, path);
  return { title: str(o.title, `${path}.title`), messages: num(o.messages, `${path}.messages`) };
}

function characterHighlight(value: unknown, path: string): CharacterHighlight {
  const o = obj(value, path);
  return { name: str(o.name, `${path}.name`), messages: num(o.messages, `${path}.messages`), chats: num(o.chats, `${path}.chats`) };
}

function toolUsage(value: unknown, path: string): ToolUsage {
  const o = obj(value, path);
  return { name: str(o.name, `${path}.name`), count: num(o.count, `${path}.count`) };
}

export function parseUserReport(value: unknown): UserReport {
  const o = obj(value, 'user');
  schema(o.schemaVersion, 'user.schemaVersion');
  const tokens = obj(o.tokens, 'user.tokens');
  const cost = obj(tokens.cost, 'user.tokens.cost');
  const messages = obj(o.messages, 'user.messages');
  const activity = obj(o.activity, 'user.activity');
  const busiest = activity.busiestDay;
  const hours = obj(o.hours, 'user.hours');
  const aiWork = obj(o.aiWork, 'user.aiWork');
  const tools = obj(o.tools, 'user.tools');
  const creations = obj(o.creations, 'user.creations');
  return {
    schemaVersion: 1,
    year: num(o.year, 'user.year'),
    generatedAt: str(o.generatedAt, 'user.generatedAt'),
    window: window_(o.window, 'user.window'),
    install: { firstInstallAt: str(obj(o.install, 'user.install').firstInstallAt, 'user.install.firstInstallAt') },
    github: nullable(o.github, 'user.github', github),
    tokens: {
      coverageStart: nullable(tokens.coverageStart, 'user.tokens.coverageStart', str),
      total: num(tokens.total, 'user.tokens.total'),
      input: num(tokens.input, 'user.tokens.input'),
      output: num(tokens.output, 'user.tokens.output'),
      cachedInput: num(tokens.cachedInput, 'user.tokens.cachedInput'),
      requests: num(tokens.requests, 'user.tokens.requests'),
      cost: {
        currency: currency(cost.currency, 'user.tokens.cost.currency'),
        known: num(cost.known, 'user.tokens.cost.known'),
        unpricedRequests: num(cost.unpricedRequests, 'user.tokens.cost.unpricedRequests'),
      },
      byMonth: fixed(tokens.byMonth, 'user.tokens.byMonth', num, 12),
      topModels: arr(tokens.topModels, 'user.tokens.topModels', modelUsage),
    },
    messages: {
      user: num(messages.user, 'user.messages.user'),
      ai: num(messages.ai, 'user.messages.ai'),
      chatsCreated: num(messages.chatsCreated, 'user.messages.chatsCreated'),
      regenerations: num(messages.regenerations, 'user.messages.regenerations'),
      favorites: num(messages.favorites, 'user.messages.favorites'),
    },
    activity: {
      days: arr(activity.days, 'user.activity.days', daily),
      activeDays: num(activity.activeDays, 'user.activity.activeDays'),
      longestStreak: nullable(activity.longestStreak, 'user.activity.longestStreak', streak),
      busiestDay: nullable(busiest, 'user.activity.busiestDay', (entry, path) => {
        const item = obj(entry, path);
        return { date: str(item.date, `${path}.date`), messages: num(item.messages, `${path}.messages`) };
      }),
    },
    hours: {
      byHour: fixed(hours.byHour, 'user.hours.byHour', num, 24),
      latestNight: nullable(hours.latestNight, 'user.hours.latestNight', str),
    },
    firstMessage: nullable(o.firstMessage, 'user.firstMessage', firstMessage),
    longestChat: nullable(o.longestChat, 'user.longestChat', chatHighlight),
    topCharacter: nullable(o.topCharacter, 'user.topCharacter', characterHighlight),
    aiWork: {
      durationMs: num(aiWork.durationMs, 'user.aiWork.durationMs'),
      longestReplyTokens: num(aiWork.longestReplyTokens, 'user.aiWork.longestReplyTokens'),
      coverageStart: nullable(aiWork.coverageStart, 'user.aiWork.coverageStart', str),
    },
    tools: { total: num(tools.total, 'user.tools.total'), top: arr(tools.top, 'user.tools.top', toolUsage) },
    creations: {
      memories: num(creations.memories, 'user.creations.memories'),
      characterCards: num(creations.characterCards, 'user.creations.characterCards'),
      workflows: num(creations.workflows, 'user.creations.workflows'),
    },
  };
}

export function parsePersonalMarket(value: unknown): PersonalMarket {
  const o = obj(value, 'market');
  return {
    entries: num(o.entries, 'market.entries'),
    downloads: num(o.downloads, 'market.downloads'),
    likes: num(o.likes, 'market.likes'),
    titles: arr(o.titles, 'market.titles', str),
  };
}

export function parseErrorMessage(value: unknown): string {
  return str(obj(value, 'error').message, 'error.message');
}
