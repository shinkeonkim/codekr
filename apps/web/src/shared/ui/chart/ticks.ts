/**
 * 눈금을 정하는 계산 (#579).
 *
 * 그림에서 떼어 둔다 — **눈금은 값의 문제이지 그리기의 문제가 아니다.** 여기서만
 * 정하면 "왜 4가 아니라 5로 끊었나" 를 시험으로 물을 수 있다.
 */

export interface SeriesPoint {
  label: string;
  value: number;
}

/** 눈금 간격의 모양. 사람이 머릿속으로 나누는 단위다 — 3이나 7로 끊으면 읽는 데 힘이 든다. */
const SHAPES = [1, 2, 5];

/**
 * 세로축의 꼭대기와 눈금들.
 *
 * **정수로만 끊는다.** 여기 오는 값은 제출 수·가입 수라 `0.5건` 이라는 것이 없다.
 * 소수 눈금은 "반 건" 을 읽게 만든다.
 *
 * 꼭대기는 최댓값보다 **위로 올린다** (7이면 8). 선이 천장에 닿아 있으면 그 점이
 * 최댓값인지 잘린 것인지 알 수 없다.
 *
 * @param max 자료의 최댓값
 * @param target 눈금을 몇 칸으로 나눌지의 목표. 정확히 이 수가 되지는 않는다
 */
export function axis(max: number, target = 5): { top: number; values: number[] } {
  // 값이 다 0이어도 축은 있어야 한다 — 빈 그림과 0인 그림은 다른 이야기다.
  const highest = Math.max(1, Math.ceil(max));

  let step = 1;
  for (let index = 0; ; index += 1) {
    step = SHAPES[index % SHAPES.length] * 10 ** Math.floor(index / SHAPES.length);
    if (highest / step <= target) break;
  }

  const top = Math.ceil(highest / step) * step;
  const values: number[] = [];
  for (let value = 0; value <= top; value += step) values.push(value);
  return { top, values };
}

/**
 * 가로축에서 이름표를 붙일 자리들.
 *
 * 30일 치를 다 적으면 글자가 서로 겹쳐 **하나도 못 읽는다.** 겹치느니 비운다.
 *
 * **마지막 날은 반드시 적는다.** 오른쪽 끝이 "언제까지" 인데, 간격에만 맡기면 30일을
 * 다섯 칸으로 끊을 때 마지막 날이 빠진다 — 그러면 이 그림이 어제까지인지 오늘까지인지
 * 알 수 없다. 대신 바로 앞 이름표와 너무 가까우면 그것을 뺀다.
 */
export function labelIndices(count: number, maxLabels: number): number[] {
  if (count === 0) return [];
  const stride = count <= maxLabels ? 1 : Math.ceil(count / maxLabels);
  const last = count - 1;
  const picked: number[] = [];
  for (let index = 0; index < last; index += stride) picked.push(index);
  // 반 칸보다 가까우면 글자가 붙는다. 둘 중 **마지막 날**을 남긴다.
  while (picked.length > 0 && last - picked[picked.length - 1] < stride / 2) picked.pop();
  picked.push(last);
  return picked;
}

/**
 * 가로 위치(0~1)에 해당하는 점의 자리.
 *
 * **가장 가까운 점으로 붙인다.** 점과 점 사이에서는 아무것도 안 보이는 것보다
 * 가까운 쪽을 보여 주는 편이 낫다 — 손가락은 픽셀 단위로 정확하지 않다.
 */
export function nearestIndex(ratio: number, count: number): number {
  if (count <= 1) return 0;
  const index = Math.round(ratio * (count - 1));
  return Math.min(count - 1, Math.max(0, index));
}
