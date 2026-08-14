import { describe, expect, test } from "bun:test";
import { axis, labelIndices, nearestIndex } from "./ticks";

describe("세로축 눈금 (#579)", () => {
  test("사람이 나누는 단위로 끊는다", () => {
    // 37 을 4칸으로 나누면 9.25 인데, 그 숫자를 축에 적으면 읽는 데 힘이 든다.
    expect(axis(37).values).toEqual([0, 10, 20, 30, 40]);
    expect(axis(8).values).toEqual([0, 2, 4, 6, 8]);
    expect(axis(120).values).toEqual([0, 50, 100, 150]);
  });

  test("꼭대기가 최댓값에서 너무 멀지 않다", () => {
    // 44를 60으로 받으면 그림 위쪽 3분의 1이 빈다 — 선이 아래에 눌려 변화가 안 보인다.
    for (const max of [7, 44, 137, 980]) {
      expect(axis(max).top).toBeLessThan(max * 1.5);
    }
  });

  test("꼭대기는 최댓값보다 위다", () => {
    // 선이 천장에 닿아 있으면 그 점이 최댓값인지 잘린 것인지 알 수 없다.
    expect(axis(7).top).toBeGreaterThan(7);
    expect(axis(30).top).toBeGreaterThanOrEqual(30);
  });

  test("정수로만 끊는다", () => {
    // `0.5건` 이라는 것은 없다. 소수 눈금은 "반 건" 을 읽게 만든다.
    for (const max of [1, 2, 3, 5, 9]) {
      for (const value of axis(max).values) expect(Number.isInteger(value)).toBe(true);
    }
  });

  test("자료가 전부 0이어도 축이 있다", () => {
    // 빈 그림과 0인 그림은 다른 이야기다 — 0인 날이 이어졌다는 것도 자료다.
    expect(axis(0).values).toEqual([0, 1]);
  });

  test("첫 눈금은 0이고 마지막은 꼭대기다", () => {
    const { top, values } = axis(23);
    expect(values[0]).toBe(0);
    expect(values[values.length - 1]).toBe(top);
  });
});

describe("가로축 이름표 (#579)", () => {
  test("적으면 다 적는다", () => {
    expect(labelIndices(5, 7)).toEqual([0, 1, 2, 3, 4]);
  });

  test("많으면 건너뛴다", () => {
    // 30일 치를 다 적으면 글자가 겹쳐 하나도 못 읽는다.
    expect(labelIndices(30, 7).length).toBeLessThanOrEqual(7);
  });

  test("마지막 날은 언제나 적는다", () => {
    // 오른쪽 끝이 "언제까지" 다. 간격에만 맡기면 30일·다섯 칸에서 이것이 빠진다.
    for (const count of [7, 12, 30, 31, 90]) {
      expect(labelIndices(count, 7).at(-1)).toBe(count - 1);
    }
  });

  test("마지막 날에 붙는 이름표는 뺀다", () => {
    // 31일이면 마지막 간격이 한 칸뿐이라, 두 글자가 겹쳐 둘 다 못 읽게 된다.
    const picked = labelIndices(31, 7);
    expect(picked.at(-1)! - picked.at(-2)!).toBeGreaterThanOrEqual(3);
  });

  test("자료가 없으면 이름표도 없다", () => {
    expect(labelIndices(0, 7)).toEqual([]);
  });
});

describe("가리킨 자리에서 가장 가까운 점 (#579)", () => {
  test("양 끝을 넘지 않는다", () => {
    expect(nearestIndex(-0.4, 10)).toBe(0);
    expect(nearestIndex(1.4, 10)).toBe(9);
  });

  test("가운데는 가운데다", () => {
    expect(nearestIndex(0.5, 11)).toBe(5);
  });

  test("점이 하나뿐이면 그 하나다", () => {
    // 나누기가 0이 되는 자리다 — 여기서 NaN 이 나오면 툴팁이 조용히 사라진다.
    expect(nearestIndex(0.7, 1)).toBe(0);
  });
});
