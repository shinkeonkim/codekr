import { request } from "@/shared/api";

export interface BadgeDefinition {
  code: string;
  label: string;
  description: string;
  visible: boolean;
  sortOrder: number;
  ruleKey: string;
}

export interface BadgeCondition {
  measure: string;
  op: string;
  value: number | boolean;
}

export interface BadgeRule {
  ruleKey: string;
  event: string;
  code: string;
  groupBy: string | null;
  conditions: BadgeCondition[];
  enabled: boolean;
}

/** 화면이 고를 목록 (#203). **서버가 내려준다** — 늘 때마다 화면을 고치지 않는다. */
export interface BadgeVocabulary {
  events: string[];
  measures: { name: string; label: string; type: string; events: string[] }[];
  operators: string[];
  groupBys: string[];
}

export interface BadgeDryRun {
  valid: boolean;
  errors: string[];
  matched: number;
  sampled: number;
  matchesUser: boolean | null;
}

export const badgeApi = {
  /** 공개 목록 — 무슨 뱃지가 있는지 (#201). */
  all: () => request<BadgeDefinition[]>("/api/v1/badges"),

  adminAll: () => request<BadgeDefinition[]>("/api/v1/admin/badges", { auth: true }),

  updateDefinition: (
    code: string,
    body: { label: string; description: string; visible: boolean; sortOrder: number },
  ) => request<BadgeDefinition>(`/api/v1/admin/badges/${code}`, { method: "PUT", auth: true, body }),

  rules: () => request<BadgeRule[]>("/api/v1/admin/badge-rules", { auth: true }),

  vocabulary: () => request<BadgeVocabulary>("/api/v1/admin/badge-rules/vocabulary", { auth: true }),

  /** **저장하지 않고** 결과를 본다 (#203). */
  dryRun: (body: unknown, userId?: number) =>
    request<BadgeDryRun>("/api/v1/admin/badge-rules/dry-run", {
      method: "POST",
      auth: true,
      body,
      query: userId ? { userId } : undefined,
    }),

  setRuleEnabled: (ruleKey: string, enabled: boolean) =>
    request<BadgeRule>(`/api/v1/admin/badge-rules/${ruleKey}/enabled`, {
      method: "PUT",
      auth: true,
      // 질의 인자는 문자열이다 — 불리언을 그대로 넣으면 타입이 맞지 않는다.
      query: { enabled: String(enabled) },
    }),
};
