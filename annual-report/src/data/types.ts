// 年度报告的数据契约。字段含义与口径见 docs/TODO/annual_report_20261105/1_data_contract.md。
// Kotlin 侧 data/annualreport/AnnualReportModels.kt 与本文件一一对应，增删字段必须两侧同步。

/** 左闭右开的统计窗口，时间均为带时区偏移的 ISO-8601 字符串。 */
export interface ReportWindow {
  start: string;
  end: string;
  timeZone: string;
}

export type MarketType = 'script' | 'package' | 'skill' | 'mcp';

export interface MarketTypeCounts {
  script: number;
  package: number;
  skill: number;
  mcp: number;
}

export interface ProjectReport {
  schemaVersion: 1;
  year: number;
  generatedAt: string;
  window: ReportWindow;
  repository: ProjectRepository;
  commits: ProjectCommits;
  codeFrequency: ProjectCodeFrequency;
  releases: ProjectReleases;
  contributors: ProjectContributors;
  pullRequests: ProjectPullRequests;
  issues: ProjectIssues;
  stars: ProjectStars;
  market: ProjectMarket;
}

export interface ProjectRepository {
  owner: string;
  name: string;
  createdAt: string;
}

export interface ProjectCommits {
  /** `main` 上作者时间落在窗口内的非合并提交数。 */
  total: number;
  /** 12 个月的提交数。 */
  byMonth: number[];
  /** 7 行 24 列，行 0 为周一，列为窗口时区的小时。 */
  weekdayHour: number[][];
  /** 窗口时区 0:00 到 4:59 之间的提交数。 */
  lateNight: number;
  busiestDay: DayCount;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface ProjectCodeFrequency {
  additions: number;
  deletions: number;
}

export interface ReleaseItem {
  tag: string;
  publishedAt: string;
}

export interface ProjectReleases {
  /** 主仓库的正式 Release，按发布时间升序。 */
  formal: ReleaseItem[];
  /** 补丁仓库中 `vX.Y.Z+N` 形式的补丁版本数。 */
  patchCount: number;
  patchByMonth: number[];
}

export interface Newcomer {
  login: string;
  firstCommitAt: string;
}

export interface ProjectContributors {
  /** 窗口内在 `main` 上有提交、且关联 GitHub 账号的人数。 */
  total: number;
  /** 窗口内全部贡献者的 GitHub 账号，按字母序排列，不表示排名。 */
  logins: string[];
  /** 首次提交落在窗口内的账号，按字母序排列，不表示排名。 */
  newcomers: Newcomer[];
}

export interface ProjectPullRequests {
  merged: number;
}

export interface ProjectIssues {
  opened: number;
  closed: number;
}

export interface ProjectStars {
  atStart: number;
  atEnd: number;
  /** 窗口内每个月新增并保留到快照时的 star 数。 */
  byMonth: number[];
}

export interface MarketTopEntry {
  title: string;
  type: MarketType;
  downloads: number;
  author: string;
}

export interface ProjectMarket {
  newEntries: MarketTypeCounts;
  /** 新上架条目中 `authorId` 等于 `publisherId` 的条目数。 */
  originals: MarketTypeCounts;
  publishers: number;
  newPublishers: number;
  /** 快照时全部公开条目的累计下载量。 */
  downloads: number;
  /** 窗口内新上架条目中下载最多的 3 个。 */
  topEntries: MarketTopEntry[];
}

/** 用于"你是第几颗星"的公开 stargazer 快照，按 star 时间排序。 */
export interface StargazerSnapshot {
  schemaVersion: 1;
  repository: string;
  /** `days` 的起点日期，窗口时区。 */
  epoch: string;
  logins: string[];
  /** 与 `logins` 等长，为距离 `epoch` 的天数。 */
  days: number[];
}

export interface UserReport {
  schemaVersion: 1;
  year: number;
  generatedAt: string;
  window: ReportWindow;
  install: UserInstall;
  /** 用户在 App 内登录的 GitHub 账号；未登录时为 null。 */
  github: GitHubIdentity | null;
  tokens: UserTokens;
  messages: UserMessages;
  activity: UserActivity;
  hours: UserHours;
  firstMessage: FirstMessage | null;
  longestChat: ChatHighlight | null;
  topCharacter: CharacterHighlight | null;
  aiWork: UserAiWork;
  tools: UserTools;
  creations: UserCreations;
}

export interface UserInstall {
  firstInstallAt: string;
}

export interface GitHubIdentity {
  login: string;
  id: number;
}

export type CostCurrency = 'CNY' | 'USD';

export interface UserCost {
  currency: CostCurrency;
  /** 按用户当前价格设置算出的已知金额。 */
  known: number;
  /** 没有价格设置的调用数。 */
  unpricedRequests: number;
}

export interface ModelUsage {
  model: string;
  tokens: number;
  requests: number;
}

export interface UserTokens {
  /** 窗口内最早一条 token 记录的时间；窗口内没有记录时为 null。 */
  coverageStart: string | null;
  total: number;
  input: number;
  output: number;
  cachedInput: number;
  requests: number;
  cost: UserCost;
  byMonth: number[];
  /** 按 token 总量降序，最多 3 个。 */
  topModels: ModelUsage[];
}

export interface UserMessages {
  user: number;
  ai: number;
  chatsCreated: number;
  regenerations: number;
  favorites: number;
}

export interface DailyActivity {
  date: string;
  messages: number;
  tokens: number;
}

export interface StreakRange {
  days: number;
  start: string;
  end: string;
}

export interface BusiestDay {
  date: string;
  messages: number;
}

export interface UserActivity {
  /** 只包含有用户消息或 token 记录的日期，按日期升序。 */
  days: DailyActivity[];
  activeDays: number;
  longestStreak: StreakRange | null;
  busiestDay: BusiestDay | null;
}

export interface UserHours {
  /** 24 个小时的用户消息数，设备时区。 */
  byHour: number[];
  /** 0 点到 5 点之间时刻最晚的一条用户消息，带时区偏移；没有时为 null。 */
  latestNight: string | null;
}

export interface FirstMessage {
  at: string;
  text: string;
  chatTitle: string;
}

export interface ChatHighlight {
  title: string;
  messages: number;
}

export interface CharacterHighlight {
  name: string;
  messages: number;
  chats: number;
}

export interface UserAiWork {
  durationMs: number;
  longestReplyTokens: number;
  /** 窗口内最早一条带耗时记录的 AI 消息时间；没有时为 null。 */
  coverageStart: string | null;
}

export interface ToolUsage {
  name: string;
  count: number;
}

export interface UserTools {
  total: number;
  /** 按调用次数降序，最多 5 个。 */
  top: ToolUsage[];
}

export interface UserCreations {
  memories: number;
  characterCards: number;
  workflows: number;
}

/** 登录 GitHub 的用户在插件市场的作品，打开对应场景时向原生按需请求。 */
export interface PersonalMarket {
  entries: number;
  downloads: number;
  likes: number;
  titles: string[];
}

/** 原生请求失败时返回的错误体。 */
export interface ReportDataError {
  message: string;
}
