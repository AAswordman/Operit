// 项目级年度数据采集脚本。数据全部取自线上，不读取本地仓库：
// 协作者的 remote 配置各不相同，本地分支也可能包含尚未提交上游的改动。
//
// 用法：
//   GH_TOKEN=$(gh auth token) node ./collector/collect-project.ts --year 2026 --snapshot 2026-12-10T00:00:00+08:00
//
// 只对传输层错误重试同一个请求；HTTP 错误、分页不完整或数据校验失败时直接退出，不写出不完整的文件。

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import type {
  DayCount,
  MarketTopEntry,
  MarketType,
  MarketTypeCounts,
  Newcomer,
  ProjectReport,
  ReleaseItem,
  StargazerSnapshot,
} from '../src/data/types.ts';

const OWNER = 'AAswordman';
const REPO = 'Operit';
const PATCH_REPO = 'OperitNightlyRelease';
const DEFAULT_BRANCH = 'main';
const TIME_ZONE = 'Asia/Shanghai';
const ZONE_OFFSET = '+08:00';
const MARKET_BASE = 'https://static.operit.app/market/v2';
const MARKET_TYPES: MarketType[] = ['script', 'package', 'skill', 'mcp'];
const PATCH_TAG = /^v\d+\.\d+\.\d+\+\d+$/;
const TRANSPORT_ATTEMPTS = 4;
const STATS_POLL_ATTEMPTS = 12;

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

interface CliOptions {
  year: number;
  snapshot: Date;
}

function parseArgs(argv: string[]): CliOptions {
  let year: number | null = null;
  let snapshot: Date | null = null;
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (flag === '--year') {
      if (value === undefined) throw new Error('--year 需要一个年份');
      year = Number.parseInt(value, 10);
      index += 1;
    } else if (flag === '--snapshot') {
      if (value === undefined) throw new Error('--snapshot 需要一个带时区的 ISO 时间');
      snapshot = new Date(value);
      if (Number.isNaN(snapshot.getTime())) throw new Error(`无法解析快照时间：${value}`);
      index += 1;
    } else {
      throw new Error(`未知参数：${flag}`);
    }
  }
  if (year === null || !Number.isInteger(year)) throw new Error('缺少 --year');
  if (snapshot === null) throw new Error('缺少 --snapshot');
  return { year, snapshot };
}

function requireToken(): string {
  const token = process.env.GH_TOKEN;
  if (token === undefined || token.trim() === '') {
    throw new Error('需要 GH_TOKEN：未认证的每小时 60 次额度不够拉完 Stargazers');
  }
  return token.trim();
}

const sleep = (ms: number) => new Promise<void>((done) => setTimeout(done, ms));

/** 只有 fetch 本身抛出的传输层错误会重试；拿到任何 HTTP 响应都交给调用方判断。 */
async function fetchWithTransportRetry(url: string, init: RequestInit): Promise<Response> {
  let lastError: unknown = null;
  for (let attempt = 1; attempt <= TRANSPORT_ATTEMPTS; attempt += 1) {
    try {
      return await fetch(url, init);
    } catch (error) {
      lastError = error;
      console.error(`[transport] ${url} 第 ${attempt} 次失败：${String(error)}`);
      await sleep(1000 * 2 ** (attempt - 1));
    }
  }
  throw new Error(`请求 ${url} 连续 ${TRANSPORT_ATTEMPTS} 次传输失败：${String(lastError)}`);
}

class GitHubClient {
  private readonly token: string;
  requests = 0;

  constructor(token: string) {
    this.token = token;
  }

  private headers(accept: string): Record<string, string> {
    return {
      Accept: accept,
      Authorization: `Bearer ${this.token}`,
      'User-Agent': 'operit-annual-report-collector',
      'X-GitHub-Api-Version': '2022-11-28',
    };
  }

  async getJson<T>(path: string, accept = 'application/vnd.github+json'): Promise<{ status: number; body: T; next: string | null }> {
    const url = path.startsWith('https://') ? path : `https://api.github.com${path}`;
    this.requests += 1;
    const response = await fetchWithTransportRetry(url, { headers: this.headers(accept) });
    const text = await response.text();
    if (response.status !== 200 && response.status !== 202) {
      throw new Error(`GitHub ${response.status} ${url}\n${text.slice(0, 400)}`);
    }
    const body = (text === '' ? null : JSON.parse(text)) as T;
    return { status: response.status, body, next: parseNextLink(response.headers.get('link')) };
  }

  async paginate<T>(path: string, accept?: string): Promise<T[]> {
    const items: T[] = [];
    let next: string | null = path;
    while (next !== null) {
      const page: { status: number; body: T[]; next: string | null } = await this.getJson<T[]>(next, accept);
      if (!Array.isArray(page.body)) throw new Error(`分页响应不是数组：${next}`);
      items.push(...page.body);
      next = page.next;
    }
    return items;
  }
}

function parseNextLink(header: string | null): string | null {
  if (header === null) return null;
  for (const part of header.split(',')) {
    const match = /<([^>]+)>;\s*rel="next"/.exec(part);
    if (match !== null && match[1] !== undefined) return match[1];
  }
  return null;
}

interface ZonedParts {
  date: string;
  month: number;
  hour: number;
  weekday: number;
}

const zonedFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  hourCycle: 'h23',
  weekday: 'short',
});

const WEEKDAY_INDEX: Record<string, number> = { Mon: 0, Tue: 1, Wed: 2, Thu: 3, Fri: 4, Sat: 5, Sun: 6 };

function zoned(iso: string): ZonedParts {
  const parts = zonedFormatter.formatToParts(new Date(iso));
  const value = (type: Intl.DateTimeFormatPartTypes): string => {
    const part = parts.find((item) => item.type === type);
    if (part === undefined) throw new Error(`时间 ${iso} 缺少 ${type}`);
    return part.value;
  };
  const weekday = WEEKDAY_INDEX[value('weekday')];
  if (weekday === undefined) throw new Error(`无法识别星期：${value('weekday')}`);
  return {
    date: `${value('year')}-${value('month')}-${value('day')}`,
    month: Number.parseInt(value('month'), 10) - 1,
    hour: Number.parseInt(value('hour'), 10),
    weekday,
  };
}

function dayNumber(date: string): number {
  const [year, month, day] = date.split('-').map((part) => Number.parseInt(part, 10));
  if (year === undefined || month === undefined || day === undefined) throw new Error(`日期格式错误：${date}`);
  return Math.round(Date.UTC(year, month - 1, day) / 86_400_000);
}

interface Window {
  start: Date;
  end: Date;
}

function inWindow(iso: string, window: Window): boolean {
  const time = new Date(iso).getTime();
  return time >= window.start.getTime() && time < window.end.getTime();
}

function zeros(length: number): number[] {
  return Array.from({ length }, () => 0);
}

function isBot(login: string): boolean {
  return login.endsWith('[bot]');
}

interface GitHubCommit {
  sha: string;
  commit: { author: { name: string; email: string; date: string } };
  author: { login: string } | null;
  parents: { sha: string }[];
}

async function collectCommits(client: GitHubClient, window: Window) {
  const commits = await client.paginate<GitHubCommit>(
    `/repos/${OWNER}/${REPO}/commits?sha=${DEFAULT_BRANCH}&per_page=100`,
  );
  const nonMerge = commits.filter((commit) => commit.parents.length === 1);
  const firstCommitByLogin = new Map<string, string>();
  for (const commit of nonMerge) {
    const login = commit.author?.login;
    if (login === undefined || isBot(login)) continue;
    const date = commit.commit.author.date;
    const known = firstCommitByLogin.get(login);
    if (known === undefined || date < known) firstCommitByLogin.set(login, date);
  }

  const byMonth = zeros(12);
  const weekdayHour = Array.from({ length: 7 }, () => zeros(24));
  const perDay = new Map<string, number>();
  const contributors = new Set<string>();
  const unlinked = new Map<string, number>();
  let total = 0;
  let lateNight = 0;
  for (const commit of nonMerge) {
    const date = commit.commit.author.date;
    if (!inWindow(date, window)) continue;
    total += 1;
    const parts = zoned(date);
    byMonth[parts.month] = (byMonth[parts.month] ?? 0) + 1;
    const row = weekdayHour[parts.weekday];
    if (row === undefined) throw new Error(`星期越界：${parts.weekday}`);
    row[parts.hour] = (row[parts.hour] ?? 0) + 1;
    if (parts.hour < 5) lateNight += 1;
    perDay.set(parts.date, (perDay.get(parts.date) ?? 0) + 1);
    const login = commit.author?.login;
    if (login === undefined) {
      const key = `${commit.commit.author.name} <${commit.commit.author.email}>`;
      unlinked.set(key, (unlinked.get(key) ?? 0) + 1);
    } else if (!isBot(login)) {
      contributors.add(login);
    }
  }
  if (total === 0) throw new Error('窗口内没有任何非合并提交，请检查年份与快照时间');

  let busiestDay: DayCount = { date: '', count: 0 };
  for (const [date, count] of [...perDay.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    if (count > busiestDay.count) busiestDay = { date, count };
  }

  const byLogin = (a: string, b: string) => a.toLowerCase().localeCompare(b.toLowerCase());
  const logins = [...contributors].sort(byLogin);
  const newcomers: Newcomer[] = logins
    .map((login) => {
      const firstCommitAt = firstCommitByLogin.get(login);
      if (firstCommitAt === undefined) throw new Error(`贡献者 ${login} 缺少首次提交时间`);
      return { login, firstCommitAt };
    })
    .filter((item) => inWindow(item.firstCommitAt, window));

  if (unlinked.size > 0) {
    console.error('以下窗口内提交没有关联 GitHub 账号，未计入贡献者：');
    for (const [author, count] of unlinked) console.error(`  ${author}  ${count} 次`);
  }

  return {
    commits: { total, byMonth, weekdayHour, lateNight, busiestDay },
    contributors: { total: logins.length, logins, newcomers },
  };
}

async function collectCodeFrequency(client: GitHubClient, window: Window) {
  // 统计接口在 GitHub 后台计算完成前返回 202，按文档轮询直到 200。
  for (let attempt = 1; attempt <= STATS_POLL_ATTEMPTS; attempt += 1) {
    const response = await client.getJson<[number, number, number][] | null>(
      `/repos/${OWNER}/${REPO}/stats/code_frequency`,
    );
    if (response.status === 200) {
      if (!Array.isArray(response.body)) throw new Error('code_frequency 响应格式错误');
      let additions = 0;
      let deletions = 0;
      for (const [week, added, deleted] of response.body) {
        const weekStart = week * 1000;
        if (weekStart >= window.start.getTime() && weekStart < window.end.getTime()) {
          additions += added;
          deletions += Math.abs(deleted);
        }
      }
      return { additions, deletions };
    }
    console.error(`[stats] code_frequency 仍在计算，第 ${attempt} 次等待`);
    await sleep(3000);
  }
  throw new Error(`code_frequency 在 ${STATS_POLL_ATTEMPTS} 次轮询后仍未就绪`);
}

interface GitHubRelease {
  tag_name: string;
  published_at: string | null;
  draft: boolean;
  prerelease: boolean;
}

async function collectReleases(client: GitHubClient, window: Window) {
  const formalReleases = await client.paginate<GitHubRelease>(`/repos/${OWNER}/${REPO}/releases?per_page=100`);
  const formal: ReleaseItem[] = [];
  for (const release of formalReleases) {
    if (release.draft || release.prerelease || release.published_at === null) continue;
    if (!inWindow(release.published_at, window)) continue;
    formal.push({ tag: release.tag_name, publishedAt: release.published_at });
  }
  formal.sort((a, b) => a.publishedAt.localeCompare(b.publishedAt));

  const patchReleases = await client.paginate<GitHubRelease>(`/repos/${OWNER}/${PATCH_REPO}/releases?per_page=100`);
  const patchByMonth = zeros(12);
  let patchCount = 0;
  for (const release of patchReleases) {
    if (release.draft || release.published_at === null || !PATCH_TAG.test(release.tag_name)) continue;
    if (!inWindow(release.published_at, window)) continue;
    patchCount += 1;
    const month = zoned(release.published_at).month;
    patchByMonth[month] = (patchByMonth[month] ?? 0) + 1;
  }
  return { formal, patchCount, patchByMonth };
}

interface SearchResponse {
  total_count: number;
  incomplete_results: boolean;
}

async function searchCount(client: GitHubClient, query: string): Promise<number> {
  const response = await client.getJson<SearchResponse>(`/search/issues?per_page=1&q=${encodeURIComponent(query)}`);
  if (response.body.incomplete_results) throw new Error(`搜索结果不完整：${query}`);
  return response.body.total_count;
}

function isoRange(window: Window): string {
  return `${window.start.toISOString()}..${window.end.toISOString()}`;
}

interface Stargazer {
  starred_at: string;
  user: { login: string } | null;
}

async function collectStars(client: GitHubClient, window: Window, repoCreatedAt: string) {
  const stargazers = await client.paginate<Stargazer>(
    `/repos/${OWNER}/${REPO}/stargazers?per_page=100`,
    'application/vnd.github.star+json',
  );
  const kept = stargazers.filter((item) => new Date(item.starred_at).getTime() < window.end.getTime());
  const byMonth = zeros(12);
  let atStart = 0;
  for (const item of kept) {
    if (new Date(item.starred_at).getTime() < window.start.getTime()) {
      atStart += 1;
    } else {
      const month = zoned(item.starred_at).month;
      byMonth[month] = (byMonth[month] ?? 0) + 1;
    }
  }
  const epoch = zoned(repoCreatedAt).date;
  const epochDay = dayNumber(epoch);
  const ordered = [...kept].sort((a, b) => a.starred_at.localeCompare(b.starred_at));
  const snapshot: StargazerSnapshot = {
    schemaVersion: 1,
    repository: `${OWNER}/${REPO}`,
    epoch,
    logins: ordered.map((item) => {
      if (item.user === null) throw new Error('stargazer 缺少用户信息');
      return item.user.login;
    }),
    days: ordered.map((item) => dayNumber(zoned(item.starred_at).date) - epochDay),
  };
  return { stars: { atStart, atEnd: kept.length, byMonth }, snapshot };
}

interface MarketListPage {
  ok: boolean;
  total: number;
  pageSize: number;
  items: MarketListEntry[];
}

interface MarketListEntry {
  id: string;
  type: MarketType;
  title: string;
  authorId: string;
  publisherId: string;
  createdAt: string;
  author: { login: string } | null;
  stats: { downloads: number } | null;
}

async function fetchMarketPage(type: MarketType, page: number): Promise<MarketListPage> {
  const url = `${MARKET_BASE}/lists/type/${type}/updated/page-${page}.json`;
  const response = await fetchWithTransportRetry(url, { headers: { 'User-Agent': 'operit-annual-report-collector' } });
  const text = await response.text();
  if (response.status !== 200) throw new Error(`市场接口 ${response.status} ${url}`);
  const body = JSON.parse(text) as MarketListPage;
  if (!body.ok || !Array.isArray(body.items)) throw new Error(`市场接口返回格式错误：${url}`);
  return body;
}

async function collectMarket(window: Window) {
  const entries: MarketListEntry[] = [];
  for (const type of MARKET_TYPES) {
    const first = await fetchMarketPage(type, 1);
    const pages = Math.ceil(first.total / first.pageSize);
    const typeEntries = [...first.items];
    for (let page = 2; page <= pages; page += 1) {
      typeEntries.push(...(await fetchMarketPage(type, page)).items);
    }
    if (typeEntries.length !== first.total) {
      throw new Error(`市场 ${type} 分页不完整：声明 ${first.total}，实际 ${typeEntries.length}`);
    }
    entries.push(...typeEntries);
  }

  const emptyCounts = (): MarketTypeCounts => ({ script: 0, package: 0, skill: 0, mcp: 0 });
  const newEntries = emptyCounts();
  const originals = emptyCounts();
  const firstEntryByPublisher = new Map<string, string>();
  let downloads = 0;
  const created: MarketTopEntry[] = [];
  for (const entry of entries) {
    if (entry.stats === null) throw new Error(`市场条目 ${entry.id} 缺少 stats`);
    if (entry.author === null) throw new Error(`市场条目 ${entry.id} 缺少作者`);
    downloads += entry.stats.downloads;
    const known = firstEntryByPublisher.get(entry.publisherId);
    if (known === undefined || entry.createdAt < known) firstEntryByPublisher.set(entry.publisherId, entry.createdAt);
    if (!inWindow(entry.createdAt, window)) continue;
    created.push({ title: entry.title, type: entry.type, downloads: entry.stats.downloads, author: entry.author.login });
    newEntries[entry.type] += 1;
    if (entry.authorId === entry.publisherId) originals[entry.type] += 1;
  }

  const topEntries = [...created].sort((a, b) => b.downloads - a.downloads).slice(0, 3);

  let newPublishers = 0;
  for (const createdAt of firstEntryByPublisher.values()) {
    if (inWindow(createdAt, window)) newPublishers += 1;
  }

  return {
    newEntries,
    originals,
    publishers: firstEntryByPublisher.size,
    newPublishers,
    downloads,
    topEntries,
  };
}

function writeJson(path: string, value: unknown): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

async function main(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const client = new GitHubClient(requireToken());
  const start = new Date(`${options.year}-01-01T00:00:00${ZONE_OFFSET}`);
  const nextYear = new Date(`${options.year + 1}-01-01T00:00:00${ZONE_OFFSET}`);
  if (options.snapshot.getTime() <= start.getTime()) throw new Error('快照时间早于窗口开始');
  const end = options.snapshot.getTime() < nextYear.getTime() ? options.snapshot : nextYear;
  const window: Window = { start, end };

  const repository = (await client.getJson<{ created_at: string }>(`/repos/${OWNER}/${REPO}`)).body;
  console.error('采集提交与贡献者…');
  const commitData = await collectCommits(client, window);
  console.error('采集代码增删…');
  const codeFrequency = await collectCodeFrequency(client, window);
  console.error('采集版本…');
  const releases = await collectReleases(client, window);
  console.error('采集 PR 与 Issue…');
  const range = isoRange(window);
  const merged = await searchCount(client, `repo:${OWNER}/${REPO} is:pr is:merged merged:${range}`);
  const opened = await searchCount(client, `repo:${OWNER}/${REPO} is:issue created:${range}`);
  const closed = await searchCount(client, `repo:${OWNER}/${REPO} is:issue is:closed closed:${range}`);
  console.error('采集 Stars…');
  const starData = await collectStars(client, window, repository.created_at);
  console.error('采集插件市场…');
  const market = await collectMarket(window);

  const toZonedIso = (date: Date): string => {
    const shifted = new Date(date.getTime() + 8 * 3_600_000).toISOString().slice(0, 19);
    return `${shifted}${ZONE_OFFSET}`;
  };

  const report: ProjectReport = {
    schemaVersion: 1,
    year: options.year,
    generatedAt: toZonedIso(new Date()),
    window: { start: toZonedIso(start), end: toZonedIso(end), timeZone: TIME_ZONE },
    repository: { owner: OWNER, name: REPO, createdAt: repository.created_at },
    commits: commitData.commits,
    codeFrequency,
    releases,
    contributors: commitData.contributors,
    pullRequests: { merged },
    issues: { opened, closed },
    stars: starData.stars,
    market,
  };

  const reportPath = resolve(projectRoot, 'data', `project-${options.year}.json`);
  const stargazerPath = resolve(projectRoot, 'data', `stargazers-${options.year}.json`);
  writeJson(reportPath, report);
  writeJson(stargazerPath, starData.snapshot);
  console.error(`完成，共 ${client.requests} 次 GitHub 请求`);
  console.error(`写入 ${reportPath}`);
  console.error(`写入 ${stargazerPath}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
