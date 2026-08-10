import type { DifficultyTier } from "@/entities/problem";

/** 서버가 내려주는 사용자 표현. */

export type UserRole = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  nickname: string;
  role: UserRole;
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
