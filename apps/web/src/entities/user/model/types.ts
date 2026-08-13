import type { CollectionSummary } from "@/entities/collection";
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
  /** 주소가 되는 이름 (#307). **바뀌지 않는다** — 링크가 끊기지 않는다. */
  handle: string;
  roles: UserRole[];
  /** 어드민 영역 진입 가능 여부. 역할 목록을 화면이 매번 해석하지 않게 서버가 함께 내린다. */
  isAdmin: boolean;
  /** 아바타 주소 (#116). 올리지 않았으면 null — 화면이 기본 표현을 그린다. */
  avatarUrl: string | null;
  /** 소개 문구 (#310). 설정 화면이 지금 쓴 것을 보여주고 고치는 데 쓴다. */
  bio: string | null;
  /**
   * 이메일을 확인했는가 (#233).
   *
   * 언제 확인했는지는 사용자에게 쓸모가 없다 — 안내를 띄울지 정하는 값이다.
   */
  emailVerified: boolean;
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
  /** 주소가 되는 이름 (#307). */
  handle: string;
  /**
   * 이 사람이 만든 **공개** 문제집 (#209).
   *
   * 비공개는 오지 않는다 — 내 프로필에서도 같다.
   */
  collections: CollectionSummary[];
  nickname: string;
  avatarUrl: string | null;
  /**
   * 소개 문구 (#310). 안 썼으면 null — **빈 문자열이 오지 않는다.**
   *
   * 빈 자리를 그리면 "안 쓴 사람" 이 아니라 "고장 난 화면" 으로 보인다.
   */
  bio: string | null;
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
  /** 계정에 저장된 화면 테마 (#274). `null` 이면 고른 적이 없다 — 이 기기의 선택을 쓴다. */
  theme: "LIGHT" | "DARK" | "SYSTEM" | null;
  /** 전체 카테고리와 라벨. 화면이 목록을 하드코딩하지 않게 서버가 알려준다. */
  notificationCategories: NotificationCategoryOption[];
}

/** 어드민 회원 목록의 한 줄 (#223). id 와 이메일을 함께 보여 사람을 특정할 수 있게 한다. */
export interface AdminUserSummary {
  id: number;
  email: string;
  nickname: string;
  roles: UserRole[];
  createdAt: string;
  /** 탈퇴한 회원이면 그 시각. 목록에서 기본으로 빠진다. */
  withdrawnAt: string | null;
  /**
   * 지금 걸려 있는 정지의 범위 (#224). 비어 있으면 정지가 없다.
   *
   * **목록에서 보여야 한다** — 상세를 하나씩 열어 봐야 안다면 이미 정지된 사람을
   * 또 정지시킨다.
   */
  suspendedScopes: string[];
}

/** 한 사람의 상태를 한 화면에서 (#223). */
export interface AdminUserDetail extends AdminUserSummary {
  score: number;
  solvedCount: number;
  submissionCount: number;
  lastSubmittedAt: string | null;
}

/** 지금 걸려 있는 정지 하나 (#224). */
export interface Suspension {
  id: number;
  scope: "WRITE" | "SUBMIT" | "ALL";
  scopeLabel: string;
  reason: string;
  /** null 이면 기한 없음 — 어드민이 풀기 전까지 이어진다. */
  endsAt: string | null;
  createdAt: string;
}

/** 어드민 관리 기록 한 줄 (#225). 고치거나 지울 수 없다 — 덧붙이기만 된다. */
export interface AdminAuditLog {
  id: number;
  actorId: number;
  actorNickname: string | null;
  action: string;
  actionLabel: string;
  targetId: number;
  /** **그때의** 대상 이름. 강제 탈퇴가 닉네임을 지운 뒤에도 남는다 (#140). */
  targetLabel: string | null;
  reason: string | null;
  detail: string | null;
  createdAt: string;
}

/**
 * 시행 중인 약관 한 판 (#235).
 *
 * **버전이 붙어 있다** — 동의는 종류가 아니라 판에 붙는다. 그러지 않으면 개정 뒤에
 * "무엇에 동의했는지" 가 사라진다.
 */
export interface TermSummary {
  id: number;
  kind: "SERVICE" | "PRIVACY";
  kindLabel: string;
  title: string;
  version: string;
  required: boolean;
  effectiveAt: string;
}

/** 내가 동의한 기록 (#235). */
export interface TermAgreement {
  documentId: number;
  kind: "SERVICE" | "PRIVACY";
  title: string;
  version: string;
  agreedAt: string;
}

/** 확인을 마친 추가 메일 주소 (#396). 로그인 주소는 이 목록에 없다. */
export interface UserEmail {
  id: number;
  email: string;
  verifiedAt: string;
}
