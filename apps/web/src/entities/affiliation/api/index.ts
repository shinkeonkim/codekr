import { request } from "@/shared/api";
import type { MyAffiliations } from "../model/types";

export const affiliationApi = {
  /** 붙은 것과 붙일 수 있는 것을 함께 준다 (#398). 로그인해야 답이 있다. */
  mine: () => request<MyAffiliations>("/api/v1/users/me/affiliations", { auth: true }),

  /** 붙인다. **화면이 보내는 값을 서버가 믿지 않는다** — 자격은 서버가 다시 확인한다. */
  attach: (affiliationId: number) =>
    request<void>(`/api/v1/users/me/affiliations/${affiliationId}`, { method: "POST", auth: true }),

  /** 뗀다. 프로필에서 사라지고 그 소속 랭킹(#399)에서도 빠진다. */
  detach: (affiliationId: number) =>
    request<void>(`/api/v1/users/me/affiliations/${affiliationId}`, { method: "DELETE", auth: true }),
};
