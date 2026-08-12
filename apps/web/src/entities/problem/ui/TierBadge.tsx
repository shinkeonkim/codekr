import { TIER_BADGE_CLASSES, difficultyLabel, tierOf } from "../model/labels";
import type { Difficulty, DifficultyState } from "../model/types";

/**
 * 난이도 뱃지. 티어마다 고유한 색을 써서 목록에서 한눈에 구분된다.
 *
 * **난이도가 없을 수 있다** (#195). 미평가·평가안함은 티어 색이 없으므로 회색으로 둔다 —
 * 아무 티어 색이나 쓰면 그 색이 난이도로 읽힌다.
 */
export function TierBadge({
  difficulty,
  label,
  state,
}: {
  difficulty: Difficulty | null;
  label?: string;
  state?: DifficultyState;
}) {
  const classes = difficulty
    ? TIER_BADGE_CLASSES[tierOf(difficulty)]
    : "border-border bg-surface-muted text-ink-muted";

  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${classes}`}
    >
      {label ?? (difficulty ? difficultyLabel(difficulty) : DIFFICULTY_STATE_LABELS[state ?? "UNRATED"])}
    </span>
  );
}

/** 난이도가 없는 문제의 표기 (#195). 서버도 같은 문구를 준다. */
export const DIFFICULTY_STATE_LABELS: Record<DifficultyState, string> = {
  RATED: "난이도 있음",
  UNRATED: "미평가",
  NO_RATE: "평가 안 함",
};
