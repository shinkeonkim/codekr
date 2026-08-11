import type { SkillTier } from "../model/types";

/** 실력 티어 눈금. 문제 난이도와 **다른 색 계열**을 써서 한눈에 구분되게 한다. */
const GROUP_CLASSES = [
  "border-amber-700/40 bg-amber-700/10 text-amber-700 dark:text-amber-500",
  "border-slate-400/40 bg-slate-400/10 text-slate-500 dark:text-slate-300",
  "border-yellow-500/40 bg-yellow-500/10 text-yellow-600 dark:text-yellow-400",
  "border-teal-500/40 bg-teal-500/10 text-teal-600 dark:text-teal-400",
  "border-sky-500/40 bg-sky-500/10 text-sky-600 dark:text-sky-400",
  "border-rose-500/40 bg-rose-500/10 text-rose-600 dark:text-rose-400",
];

/**
 * 실력 티어 뱃지 (#58).
 *
 * 라벨에 **"실력"을 붙인다.** 문제 난이도 티어와 이름이 같아서, 이름만 두면
 * "골드 5 문제를 푼 사람"인지 "골드 5 인 사람"인지 구분되지 않는다.
 */
export function SkillTierBadge({ tier }: { tier: SkillTier | null }) {
  if (!tier) {
    return <span className="text-xs text-ink-muted">아직 티어 없음</span>;
  }
  const group = GROUP_CLASSES[Math.min(Math.floor((tier.level - 1) / 5), GROUP_CLASSES.length - 1)];

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium ${group}`}
    >
      <span className="opacity-70">실력</span>
      {tier.name}
    </span>
  );
}
