import { EMPTY_FILTERS } from "./AdminProblemFilters";
import type { AdminProblemFilterValues } from "./AdminProblemFilters";

/**
 * 거르개 값을 질의로 (#626).
 *
 * **빈 값은 아예 보내지 않는다.** `published=""` 를 보내면 서버는 그것을 "빈 문자열을
 * Boolean 으로" 로 읽어 400 을 내고, `category=""` 도 마찬가지다 — 조건을 지우는 순간
 * 목록이 통째로 깨진다. "안 걸었다" 는 **키가 없는 것**이지 빈 값이 아니다.
 */
export function toQuery(filters: AdminProblemFilterValues): Record<string, string> {
  const query: Record<string, string> = {};
  for (const [key, value] of Object.entries(filters)) {
    if (value) query[key] = value;
  }
  return query;
}

/**
 * 조건을 하나라도 걸었는가.
 *
 * 빈 목록이 **"아직 없다" 인지 "못 찾았다" 인지**를 가르는 데 쓴다. 정렬은 조건이
 * 아니다 — 정렬만 바꿔서 결과가 0건이 되는 일은 없다.
 */
export function isFiltered(filters: AdminProblemFilterValues): boolean {
  return (Object.keys(filters) as (keyof AdminProblemFilterValues)[])
    .filter((key) => key !== "sort")
    .some((key) => filters[key] !== EMPTY_FILTERS[key]);
}
