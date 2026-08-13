import { request } from "@/shared/api";
import type { AffiliationKind } from "../model/types";

export interface AffiliationDomain {
  id: number;
  domain: string;
}

/** 어드민이 보는 소속 한 줄 (#397). 도메인까지 함께 온다 — 화면이 두 번 묻지 않게. */
export interface Affiliation {
  id: number;
  name: string;
  kind: AffiliationKind;
  kindLabel: string;
  domains: AffiliationDomain[];
}

export const adminAffiliationApi = {
  list: () => request<Affiliation[]>("/api/v1/admin/affiliations", { auth: true }),

  create: (body: { name: string; kind: AffiliationKind }) =>
    request<Affiliation>("/api/v1/admin/affiliations", { method: "POST", auth: true, body }),

  /** 내린다. 붙어 있던 사람들의 소속도 함께 뜻을 잃는다. */
  remove: (id: number) =>
    request<void>(`/api/v1/admin/affiliations/${id}`, { method: "DELETE", auth: true }),

  addDomain: (id: number, domain: string) =>
    request<AffiliationDomain>(`/api/v1/admin/affiliations/${id}/domains`, {
      method: "POST",
      auth: true,
      body: { domain },
    }),

  removeDomain: (id: number, domainId: number) =>
    request<void>(`/api/v1/admin/affiliations/${id}/domains/${domainId}`, {
      method: "DELETE",
      auth: true,
    }),
};
