/** 서버의 페이지 응답 규약. 도메인과 무관하므로 여기 둔다. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Query = Record<string, string | number | undefined>;
