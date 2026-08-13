import { request } from "@/shared/api";
import type { MyAffiliations } from "../model/types";

export const affiliationApi = {
  /** 붙은 것과 붙일 수 있는 것을 함께 준다 (#398). 로그인해야 답이 있다. */
  mine: () => request<MyAffiliations>("/api/v1/users/me/affiliations", { auth: true }),
};
