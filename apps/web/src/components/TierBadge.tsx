import { TIER_BADGE_CLASSES, difficultyLabel, tierOf } from "@/lib/labels";
import type { Difficulty } from "@/lib/types";

/** 난이도 뱃지. 티어마다 고유한 색을 써서 목록에서 한눈에 구분된다. */
export function TierBadge({ difficulty, label }: { difficulty: Difficulty; label?: string }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${
        TIER_BADGE_CLASSES[tierOf(difficulty)]
      }`}
    >
      {label ?? difficultyLabel(difficulty)}
    </span>
  );
}
