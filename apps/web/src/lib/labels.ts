import type {
  Difficulty,
  DifficultyStep,
  DifficultyTier,
  ProblemCategory,
  SubmissionStatus,
  Verdict,
} from "./types";

export const CATEGORY_LABELS: Record<ProblemCategory, string> = {
  ALGORITHM: "알고리즘",
  DATA_STRUCTURE: "자료구조",
  SQL: "SQL",
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

export const VERDICT_LABELS: Record<Verdict, string> = {
  ACCEPTED: "정답",
  WRONG_ANSWER: "오답",
  TIME_LIMIT_EXCEEDED: "시간 초과",
  MEMORY_LIMIT_EXCEEDED: "메모리 초과",
  RUNTIME_ERROR: "런타임 에러",
  COMPILE_ERROR: "컴파일 에러",
  OUTPUT_LIMIT_EXCEEDED: "출력 초과",
  SYSTEM_ERROR: "채점 오류",
};

export const STATUS_LABELS: Record<SubmissionStatus, string> = {
  PENDING: "대기 중",
  JUDGING: "채점 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

/** 판정에 대응하는 시맨틱 색. 정답만 초록, 나머지는 실패 계열로 구분한다. */
export function verdictTone(verdict: Verdict | null | undefined): "ok" | "danger" | "warn" | "muted" {
  if (!verdict) return "muted";
  if (verdict === "ACCEPTED") return "ok";
  if (verdict === "SYSTEM_ERROR") return "warn";
  return "danger";
}

export function formatMemory(kilobytes: number): string {
  if (kilobytes <= 0) return "-";
  if (kilobytes < 1024) return `${kilobytes} KB`;
  return `${(kilobytes / 1024).toFixed(1)} MB`;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
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
