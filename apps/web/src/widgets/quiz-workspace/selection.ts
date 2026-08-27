/**
 * 보기를 고르고 푸는 규칙 (#650).
 *
 * **화면에서 떼어 둔다.** 여기가 틀리면 조용히 틀린다 — 하나만 고르는 문제에서 둘이
 * 담기면 서버는 "정확히 일치" 로 채점하므로 **정답을 골라도 틀린다.** 오류는 나지
 * 않고 사용자는 자기가 틀린 줄 안다.
 */
export function toggleChoice(selected: number[], seq: number, single: boolean): number[] {
  // 하나만 고르는 문제는 **바꿔 끼운다.** 체크를 풀고 다시 고르게 하면 한 걸음이 늘고,
  // 그 사이에 아무것도 안 고른 상태가 생긴다.
  if (single) return [seq];
  return selected.includes(seq) ? selected.filter((it) => it !== seq) : [...selected, seq];
}

/** 낼 것이 있는가. 없으면 서버가 거부하므로 버튼을 먼저 막는다. */
export function isAnswerEmpty(selected: number[], text: string, short: boolean): boolean {
  return short ? text.trim() === "" : selected.length === 0;
}
