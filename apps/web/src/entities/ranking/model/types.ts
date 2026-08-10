/** 랭킹 (#57, #85, #58). */

/**
 * 지표와 기간은 **서버가 알려준다.**
 *
 * 값의 종류를 화면이 하드코딩하면 축이 늘어날 때마다 화면을 같이 고쳐야 한다.
 * 그래서 `string` 으로 받고 목록은 `/rankings/metrics` 에서 가져온다.
 */
export interface RankingOption {
  value: string;
  label: string;
  description?: string;
}

export interface RankingOptions {
  metrics: RankingOption[];
  periods: RankingOption[];
}

export interface RankingEntry {
  rank: number;
  nickname: string;
  score: number;
  solvedCount: number;
  lastSolvedAt: string | null;
}

/**
 * 실력 티어 (#58).
 *
 * **문제 난이도 티어와 이름은 같지만 다른 개념이다.** 화면에서 반드시 구분해 표기한다 —
 * "골드 5 사용자"와 "골드 5 문제"는 서로 다른 말이다.
 */
export interface SkillTier {
  level: number;
  name: string;
  /** 다음 티어에 필요한 점수. 최고 티어면 null. */
  nextLevelScore: number | null;
}

export interface AwardedBadge {
  code: string;
  label: string;
  description: string;
  awardedAt: string;
}
