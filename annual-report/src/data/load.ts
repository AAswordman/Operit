import {
  parseErrorMessage,
  parsePersonalMarket,
  parseProjectReport,
  parseStargazers,
  parseUserReport,
} from './parse.ts';
import type { PersonalMarket, ProjectReport, StargazerSnapshot, UserReport } from './types.ts';

// 每一年的项目快照由采集脚本生成并提交进仓库，构建时打包。
const PROJECT_DATA: Record<number, () => Promise<unknown>> = {
  2026: () => import('../../data/project-2026.json').then((module) => module.default),
};

// stargazer 名单只在"找到我的星星"时用到，单独分包按需加载。
const STARGAZER_DATA: Record<number, () => Promise<unknown>> = {
  2026: () => import('../../data/stargazers-2026.json').then((module) => module.default),
};

export async function loadProject(year: number): Promise<ProjectReport> {
  const loader = PROJECT_DATA[year];
  if (loader === undefined) throw new Error(`这个版本没有打包 ${year} 年的项目数据`);
  const report = parseProjectReport(await loader());
  if (report.year !== year) throw new Error(`项目数据年份不符：期望 ${year}，实际 ${report.year}`);
  return report;
}

export async function loadStargazers(year: number): Promise<StargazerSnapshot> {
  const loader = STARGAZER_DATA[year];
  if (loader === undefined) throw new Error(`这个版本没有打包 ${year} 年的 stargazer 名单`);
  return parseStargazers(await loader());
}

async function fetchReportData(path: string): Promise<unknown> {
  const response = await fetch(path, { cache: 'no-store' });
  const body: unknown = await response.json();
  if (!response.ok) throw new Error(parseErrorMessage(body));
  return body;
}

/** 用户数据由原生在本机生成，PathHandler 在采集完成后才响应。 */
export async function loadUser(year: number): Promise<UserReport> {
  const report = parseUserReport(await fetchReportData('/report-data/user.json'));
  if (report.year !== year) throw new Error(`用户数据年份不符：期望 ${year}，实际 ${report.year}`);
  return report;
}

export async function loadPersonalMarket(): Promise<PersonalMarket> {
  return parsePersonalMarket(await fetchReportData('/report-data/market.json'));
}
