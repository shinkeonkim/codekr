import type { NotificationCategory, NotificationCategoryOption } from "@/entities/notification";
import type { SubmissionVisibility } from "@/entities/submission";
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

/**
 * 공개 프로필 (#83).
 *
 * **이미 공개된 것만 모은다.** 전체 제출 목록이 누가 어떤 문제를 언제 내서 어떤 결과를
 * 받았는지 이미 보여주므로, 이 화면은 그것을 사람 기준으로 묶은 것이다.
 */
export interface UserProfile {
  nickname: string;
  joinedAt: string;
  solvedCount: number;
  submissionCount: number;
  solvedByTier: SolvedByTier[];
  /** 전체 기간 기준 (#81). */
  currentStreak: number;
  longestStreak: number;
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
}
