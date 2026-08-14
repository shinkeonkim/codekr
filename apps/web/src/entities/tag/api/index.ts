import { request } from "@/shared/api";
import type { ProblemTag, Tag } from "../model/types";

export const tagApi = {
  /** 태그 목록. 공개다 — 무엇으로 문제를 고를 수 있는지는 로그인 전에도 보여야 한다. */
  list: () => request<Tag[]>("/api/v1/tags"),

  /** 문제의 태그를 통째로 바꾼다 (#232). 어드민 전용. */
  replaceProblemTags: (problemId: number, tagIds: number[]) =>
    request<ProblemTag[]>(`/api/v1/admin/problems/${problemId}/tags`, {
      method: "PUT",
      auth: true,
      body: { tagIds },
    }),

  create: (body: { slug: string; name: string; description?: string }) =>
    request<Tag>("/api/v1/admin/tags", { method: "POST", auth: true, body }),

  /**
   * 이름과 설명을 고친다 (#553). **주소(slug)는 못 고친다** — 링크와 필터 파라미터가
   * 그것을 가리키므로 서버가 애초에 받지 않는다.
   */
  update: (id: number, body: { name: string; description?: string }) =>
    request<Tag>(`/api/v1/admin/tags/${id}`, { method: "PUT", auth: true, body }),
};
