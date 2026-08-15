import type { Difficulty, DifficultyStep, DifficultyTier, ProblemCategory } from "./types";

export const CATEGORY_LABELS: Record<ProblemCategory, string> = {
  ALGORITHM: "알고리즘",
  DATA_STRUCTURE: "자료구조",
  SQL: "SQL",
  REDIS: "Redis",
  MONGODB: "MongoDB",
  SHELL: "셸 스크립트",
  NETWORK: "네트워크",
  LANGUAGE: "프로그래밍 언어",
  OS: "운영체제",
  SYSTEM_DESIGN: "시스템 설계",
};

export const TIER_LABELS: Record<DifficultyTier, string> = {
  BRONZE: "브론즈",
  SILVER: "실버",
  GOLD: "골드",
  PLATINUM: "플래티넘",
  DIAMOND: "다이아몬드",
  RUBY: "루비",
};

export const TIERS = Object.keys(TIER_LABELS) as DifficultyTier[];

/** 각 티어 안의 단계. 5가 가장 쉽고 1이 가장 어렵다. */
export const DIFFICULTY_STEPS: DifficultyStep[] = [5, 4, 3, 2, 1];

/** 선택 목록에 쓰는 30단계 전체. 쉬운 것부터 나열한다. */
export const ALL_DIFFICULTIES: Difficulty[] = TIERS.flatMap((tier) =>
  DIFFICULTY_STEPS.map((step) => `${tier}_${step}` as Difficulty),
);

export function difficultyLabel(difficulty: Difficulty): string {
  const [tier, step] = difficulty.split("_") as [DifficultyTier, string];
  return `${TIER_LABELS[tier]} ${step}`;
}

export function tierOf(difficulty: Difficulty): DifficultyTier {
  return difficulty.split("_")[0] as DifficultyTier;
}

/** 티어별 뱃지 색. 실제 색 값은 globals.css 의 토큰에 있다. */
export const TIER_BADGE_CLASSES: Record<DifficultyTier, string> = {
  BRONZE: "bg-tier-bronze/15 text-tier-bronze border-tier-bronze/35",
  SILVER: "bg-tier-silver/15 text-tier-silver border-tier-silver/35",
  GOLD: "bg-tier-gold/15 text-tier-gold border-tier-gold/35",
  PLATINUM: "bg-tier-platinum/15 text-tier-platinum border-tier-platinum/35",
  DIAMOND: "bg-tier-diamond/15 text-tier-diamond border-tier-diamond/35",
  RUBY: "bg-tier-ruby/15 text-tier-ruby border-tier-ruby/35",
};
