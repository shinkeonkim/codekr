/**
 * 되감기지 않는 트랙의 셈 (#523).
 *
 * 화면 없이 확인할 수 있게 **자리 계산만 떼어 놓았다.** 이 셈이 틀리면 마지막 장에서
 * 첫 장으로 갈 때 되감기거나, 사본 위에 머물러 빈 곳이 보인다.
 *
 * 트랙은 양 끝에 사본을 한 장씩 덧댄 모양이다 — `[n'] [1] [2] … [n] [1']`.
 * 그래서 **자리 1 이 첫 장**이고, 자리 0 과 n+1 이 사본이다.
 */

/** 사본을 덧댄 트랙. 한 장뿐이면 덧대지 않는다 — 넘길 곳이 없다. */
export function buildTrack<T>(names: readonly T[]): T[] {
  if (names.length < 2) return [...names];
  return [names[names.length - 1], ...names, names[0]];
}

/**
 * 사본 자리에 있으면 **옮겨 가야 할 진짜 자리**, 아니면 `null`.
 *
 * `null` 일 때 옮기면 멀쩡한 자리에서 화면이 튄다.
 */
export function settleTarget(position: number, count: number): number | null {
  if (count < 2) return null;
  if (position === 0) return count; // 앞쪽 사본 = 마지막 장
  if (position === count + 1) return 1; // 뒤쪽 사본 = 첫 장
  return null;
}

/**
 * 지금 보이는 **진짜 장**의 번호 (0부터).
 *
 * 사본 위에서도 그것이 흉내 내는 장을 가리킨다 — 점이 잠깐 엉뚱한 곳을 가리키면
 * 어디쯤인지 알 수 없게 된다.
 */
export function dotIndex(position: number, count: number): number {
  if (count < 2) return 0;
  return (((position - 1) % count) + count) % count;
}
