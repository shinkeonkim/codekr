/** 대회 (#61, #63). */

/**
 * 진행 단계.
 *
 * **서버가 조회 시점에 판정한다.** 화면이 시작·종료 시각을 보고 스스로 정하면,
 * 사용자 시계가 틀린 만큼 대회가 일찍 또는 늦게 시작한 것으로 보인다.
 */
export type ContestPhase =
  | "DRAFT"
  | "SCHEDULED"
  | "RUNNING"
  | "ENDED"
  | "ARCHIVED"
  | "CANCELED";

export interface ContestSummary {
  slug: string;
  title: string;
  startsAt: string;
  endsAt: string;
  phase: ContestPhase;
  phaseLabel: string;
  participantCount: number;
  /** 순위가 동결돼 있는가 (#86). 화면이 크게 알려야 한다. */
  frozen: boolean;
}

export interface ContestProblem {
  /** 대회 안에서의 표기. A, B, C… */
  label: string;
  slug: string;
  title: string;
  score: number;
  excluded: boolean;
}

export interface ContestDetail {
  summary: ContestSummary;
  description: string;
  freezeAt: string | null;
  registered: boolean;
  canRegister: boolean;
  /** **시작 전에는 비어 있다** — 참가자도 볼 수 없다. */
  problems: ContestProblem[];
}

/** 순위표 (#63). 일반 랭킹과 다른 화면이다 — 그 대회 안에서의 순위다. */
export interface Scoreboard {
  contestSlug: string;
  frozen: boolean;
  frozenAt: string | null;
  /** 재채점 중이면 순위가 바뀔 수 있다. 화면이 알려야 한다. */
  rejudgeInProgress: boolean;
  problems: ScoreboardProblem[];
  rows: ScoreboardRow[];
}

export interface ScoreboardProblem extends ContestProblem {
  solvedCount: number;
}

export interface ScoreboardRow {
  rank: number;
  nickname: string;
  totalScore: number;
  solvedCount: number;
  lastSolvedAt: string | null;
  cells: ScoreboardCell[];
}

export interface ScoreboardCell {
  solved: boolean;
  /** 맞힌 시각까지 걸린 분. 못 맞혔으면 null. */
  solvedMinutes: number | null;
  attempts: number;
  /** 동결 이후의 시도 수. 결과는 감춰지고 시도 사실만 보인다. */
  pending: number;
}

/** 대회 공지 (#147). */
export interface ContestNotice {
  id: number;
  title: string;
  body: string;
  createdAt: string;
  edited: boolean;
}

/**
 * 대회 질의 (#147).
 *
 * 목록에는 **볼 수 있는 것만** 온다 — 남의 비공개 답변은 아예 내려오지 않는다.
 */
export interface ContestQuestion {
  id: number;
  problemLabel: string | null;
  body: string;
  answer: string | null;
  answerPublic: boolean;
  answeredAt: string | null;
  createdAt: string;
  /** 내가 낸 질문인가. 목록에서 내 것을 찾을 수 있어야 한다. */
  mine: boolean;
}
