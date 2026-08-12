/** 서버의 페이지 응답 규약. 도메인과 무관하므로 여기 둔다. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * 질의 문자열 값.
 *
 * 배열이면 **같은 이름으로 여러 번** 붙는다 (`?tag=dp&tag=graph`). 값 하나에 쉼표로
 * 이어 붙이지 않는 이유: 값 자체에 쉼표가 들어가면 갈라지는 곳이 달라진다 (#232).
 */
export type Query = Record<string, string | number | string[] | undefined>;
