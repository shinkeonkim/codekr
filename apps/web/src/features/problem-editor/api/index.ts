import { request } from "@/shared/api";

/** 지문에서 뽑아낸 초안 (#230). 서버가 값의 범위까지 검사해 돌려준 것이다. */
export interface ProblemDraft {
  title: string;
  inputDescription: string;
  outputDescription: string;
  examples: { input: string; output: string }[];
  category: string | null;
  difficulty: string | null;
  timeLimitMs: number | null;
  memoryLimitMb: number | null;
  /** 지문에서 찾지 못한 것. 화면이 그대로 보여 준다 — 지어낸 값보다 낫다. */
  missing: string[];
}

export const problemDraftApi = {
  /** 키가 없으면 서버가 404 를 준다 (#115 와 같은 규칙). 화면은 그것을 실패로 다룬다. */
  fromStatement: (statement: string) =>
    request<ProblemDraft>("/api/v1/admin/problems/draft", {
      method: "POST",
      auth: true,
      body: { statement },
    }),
};
