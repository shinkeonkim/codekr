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

/** 규칙 저장 요청 (#549). 서버의 `BadgeRuleUpsertRequest` 와 같다. */
export interface BadgeRuleUpsert {
  ruleKey: string;
  event: string;
  code: string;
  groupBy?: string | null;
  conditions: BadgeCondition[];
}

export const badgeApi = {
  /** 공개 목록 — 무슨 뱃지가 있는지 (#201). */
  all: () => request<BadgeDefinition[]>("/api/v1/badges"),

  adminAll: () => request<BadgeDefinition[]>("/api/v1/admin/badges", { auth: true }),

  updateDefinition: (
    code: string,
    body: { label: string; description: string; visible: boolean; sortOrder: number },
  ) => request<BadgeDefinition>(`/api/v1/admin/badges/${code}`, { method: "PUT", auth: true, body }),

  /** 새 뱃지 정의 (#549). **코드는 만든 뒤 못 바꾼다** — `user_badges` 에 박힌다. */
  createDefinition: (body: {
    code: string;
    label: string;
    description: string;
    ruleKey: string;
    visible?: boolean;
    sortOrder?: number;
  }) => request<BadgeDefinition>("/api/v1/admin/badges", { method: "POST", auth: true, body }),

  rules: () => request<BadgeRule[]>("/api/v1/admin/badge-rules", { auth: true }),

  /** 규칙을 만든다 (#549). 미리보기만 되고 저장이 없던 것을 메운다. */
  createRule: (body: BadgeRuleUpsert) =>
    request<BadgeRule>("/api/v1/admin/badge-rules", { method: "POST", auth: true, body }),

  /** 규칙을 고친다 (#549). `ruleKey` 로 찾는다. */
  updateRule: (ruleKey: string, body: BadgeRuleUpsert) =>
    request<BadgeRule>(`/api/v1/admin/badge-rules/${encodeURIComponent(ruleKey)}`, {
      method: "PUT",
      auth: true,
      body,
    }),

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
