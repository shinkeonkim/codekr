import type { NotificationCategory, NotificationCategoryOption } from "@/entities/notification";
import type { SubmissionVisibility } from "@/entities/submission";
import type { AwardedBadge, SkillTier } from "@/entities/ranking";
import type { DifficultyTier } from "@/entities/problem";

/** 서버가 내려주는 사용자 표현. */

/** 전역 역할 (#103). 한 사람이 여럿을 가질 수 있다. */
export type UserRole =
  | "USER"
  | "SUPERUSER"
  | "ADMIN"
  | "PROBLEM_SETTER"
  | "CONTEST_MANAGER"
  | "BOARD_MANAGER";

export interface User {
  id: number;
  email: string;
  nickname: string;
  roles: UserRole[];
  /** 어드민 영역 진입 가능 여부. 역할 목록을 화면이 매번 해석하지 않게 서버가 함께 내린다. */
  isAdmin: boolean;
  /** 아바타 주소 (#116). 올리지 않았으면 null — 화면이 기본 표현을 그린다. */
  avatarUrl: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

/** 티어별로 몇 문제를 풀었는지. */
export interface SolvedByTier {
  tier: DifficultyTier;
  solvedCount: number;
}

/** 태그별로 몇 문제를 풀었는지 (#232). */
export interface SolvedByTag {
  slug: string;
  name: string;
  solved: number;
}

/**
 * 공개 프로필 (#83).
 *
 * **이미 공개된 것만 모은다.** 전체 제출 목록이 누가 어떤 문제를 언제 내서 어떤 결과를
 * 받았는지 이미 보여주므로, 이 화면은 그것을 사람 기준으로 묶은 것이다.
 */
export interface UserProfile {
  nickname: string;
  avatarUrl: string | null;
  joinedAt: string;
  solvedCount: number;
  submissionCount: number;
  solvedByTier: SolvedByTier[];
  solvedByTag: SolvedByTag[];
  /** 전체 기간 기준 (#81). */
  currentStreak: number;
  longestStreak: number;
  /** 랭킹 점수 (#57). 가장 어려운 100문제의 합이다. */
  score: number;
  /**
   * 실력 티어 (#58). 아직 한 문제도 못 풀었으면 null — 브론즈 5 가 아니라 **티어가 없다**.
   *
   * **도달했던 최고 점수로 정한다.** 강등이 없어서 `score` 와 갈라질 수 있다.
   */
  skillTier: SkillTier | null;
  /** 랭킹을 껐거나 푼 문제가 없으면 null — 꼴찌가 아니라 순위가 없는 것이다. */
  rank: number | null;
  badges: AwardedBadge[];
}

/**
 * 내 설정 (#104).
 *
 * 지금은 항목이 하나지만 앞으로 들어올 것들의 자리이기도 하다 —
 * 알림 카테고리별 수신 설정(#106)이 여기 붙는다.
 */
export interface UserSettings {
  defaultSubmissionVisibility: SubmissionVisibility;
  /** 수신을 **끈** 카테고리만 담긴다 (#106). */
  mutedNotificationCategories: NotificationCategory[];
  /** 전체 카테고리와 라벨. 화면이 목록을 하드코딩하지 않게 서버가 알려준다. */
  notificationCategories: NotificationCategoryOption[];
  /**
   * 내 공개 코드를 누가 읽었는지 알림받을지 (#136). **기본은 끔.**
   *
   * 꺼져 있으면 아예 기록하지 않는다 — 켜는 것이 곧 추적에 대한 동의다.
   */
  viewNotificationEnabled: boolean;
}
