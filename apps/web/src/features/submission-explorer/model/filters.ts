import type { Verdict } from "@/entities/submission";

/**
 * 전체 제출 목록의 필터 (#34, #76).
 *
 * URL 쿼리에 그대로 담는다 — 새로고침·뒤로가기·링크 공유 후에도 같은 목록이 나와야 한다.
 */
export const FILTER_KEYS = [
  "problemSlug",
  "nickname",
  "runtimeId",
  "verdict",
  "from",
  "to",
  "sort",
  "page",
] as const;

export type FilterKey = (typeof FILTER_KEYS)[number];
export type Filters = Partial<Record<FilterKey, string>>;

/**
 * 상시 노출하는 필터.
 *
 * **거의 항상 쓰이는 것만 밖에 둔다.** 전부 밖에 두면 지금과 같은 문제가 반복되고,
 * 전부 안에 넣으면 흔한 조작에 클릭이 하나 더 든다 (#76 리서치).
 */
export const PRIMARY_KEYS: FilterKey[] = ["verdict", "runtimeId", "sort"];

/** 접어 두는 필터. 칩으로 걸린 사실이 보이므로 감춰도 잊히지 않는다. */
export const SECONDARY_KEYS: FilterKey[] = ["problemSlug", "nickname", "from", "to"];

export const FILTER_LABELS: Record<FilterKey, string> = {
  problemSlug: "문제",
  nickname: "제출자",
  runtimeId: "언어",
  verdict: "판정",
  from: "시작일",
  to: "종료일",
  sort: "정렬",
  page: "페이지",
};

export const VERDICTS: Verdict[] = [
  "ACCEPTED",
  "WRONG_ANSWER",
  "TIME_LIMIT_EXCEEDED",
  "MEMORY_LIMIT_EXCEEDED",
  "RUNTIME_ERROR",
  "COMPILE_ERROR",
  "OUTPUT_LIMIT_EXCEEDED",
  "SYSTEM_ERROR",
];

export const SORTS = [
  { value: "LATEST", label: "최신순" },
  { value: "OLDEST", label: "오래된순" },
  { value: "RUNTIME", label: "실행 시간 짧은순" },
  { value: "MEMORY", label: "메모리 적은순" },
];

/**
 * 칩에 보여줄 필터들.
 *
 * `page` 는 필터가 아니라 위치다. `sort` 는 늘 값이 있어서(기본 최신순) 칩으로 두면
 * 지울 수 없는 칩이 하나 늘 붙어 있게 된다 — 둘 다 뺀다.
 */
export function activeChips(filters: Filters, hidden: FilterKey[] = []): FilterKey[] {
  return FILTER_KEYS.filter(
    (key) => key !== "page" && key !== "sort" && !hidden.includes(key) && filters[key],
  );
}

export function hasActiveFilters(filters: Filters, hidden: FilterKey[] = []): boolean {
  return activeChips(filters, hidden).length > 0;
}
