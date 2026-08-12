/** 문제의 알고리즘 분류 (#232). */
export interface Tag {
  id: number;
  slug: string;
  name: string;
  description: string | null;
  /** 이 태그가 붙은 **공개** 문제 수. 없으면 고를 때 빈 결과를 계속 만난다. */
  problemCount: number;
}

/** 문제에 붙은 태그. 문제 화면에서는 개수가 필요 없다. */
export interface ProblemTag {
  id: number;
  slug: string;
  name: string;
}
