import type { Deck, SceneSpec } from '../deck/deck.ts';
import type { ProjectReport, UserReport } from '../data/types.ts';
import { calendarScene } from './calendar.ts';
import { clockScene } from './clock.ts';
import { commitsScene } from './commits.ts';
import { contributorsScene } from './contributors.ts';
import { coverScene } from './cover.ts';
import { githubScene } from './github.ts';
import { interludeScene } from './interlude.ts';
import { marketScene } from './market.ts';
import { meetScene } from './meet.ts';
import { modelsScene } from './models.ts';
import { posterScene, quietScene } from './poster.ts';
import { releasesScene } from './releases.ts';
import { starsScene } from './stars.ts';
import { tokensScene } from './tokens.ts';
import { aiTimeScene, creationsScene, toolsScene } from './work.ts';
import { chatsScene, firstWordsScene } from './words.ts';

export interface UserSource {
  current(): Promise<UserReport>;
  retry(): Promise<UserReport>;
  onReady(user: UserReport): void;
}

export function buildProjectScenes(project: ProjectReport, user: UserSource): SceneSpec[] {
  return [
    coverScene(project),
    commitsScene(project),
    releasesScene(project),
    starsScene(project),
    contributorsScene(project),
    marketScene(project),
    interludeScene(user),
  ];
}

/**
 * 个人篇由数据决定：某个指标为空就不出现对应的页，页码按实际出现的顺序编号。
 * 今年完全没有对话和 token 记录的用户，只看到相识、空白页和海报。
 */
export function buildPersonalScenes(project: ProjectReport, user: UserReport, deck: Deck): SceneSpec[] {
  const makers: ((kicker: string) => SceneSpec)[] = [(kicker) => meetScene(user, kicker)];
  const chatted = user.messages.user > 0;
  if (!chatted && user.tokens.total === 0) {
    makers.push((kicker) => quietScene(kicker));
  } else {
    if (user.tokens.total > 0) makers.push((kicker) => tokensScene(user, kicker));
    if (user.tokens.topModels.length > 0) makers.push((kicker) => modelsScene(user, kicker));
    if (user.activity.activeDays > 0) makers.push((kicker) => calendarScene(user, kicker));
    if (user.hours.byHour.some((count) => count > 0)) makers.push((kicker) => clockScene(user, kicker));
    const first = user.firstMessage;
    if (first !== null) makers.push((kicker) => firstWordsScene(user, first, kicker));
    if (chatted) makers.push((kicker) => chatsScene(user, kicker));
    if (user.tools.total > 0) makers.push((kicker) => toolsScene(user, kicker));
    if (user.aiWork.durationMs > 0) makers.push((kicker) => aiTimeScene(user, kicker));
  }
  const creations = user.creations;
  if (creations.memories + creations.characterCards + creations.workflows > 0) {
    makers.push((kicker) => creationsScene(user, kicker));
  }
  const github = user.github;
  if (github !== null) makers.push((kicker) => githubScene(project, user, github, kicker));
  makers.push((kicker) => posterScene(project, user, deck, kicker));
  return makers.map((make, index) => make(`你的篇 · ${String(index + 1).padStart(2, '0')}`));
}
