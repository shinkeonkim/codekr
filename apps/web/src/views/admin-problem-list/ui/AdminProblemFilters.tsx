"use client";

import { CATEGORY_LABELS, PROBLEM_SORTS, TIER_LABELS } from "@/entities/problem";
import type { ProblemCategory, DifficultyTier } from "@/entities/problem";
import { Field, Input, Select } from "@/shared/ui";

/** 목록을 좁히는 조건. 빈 문자열은 "안 걸었다" 는 뜻이다 (#626). */
export interface AdminProblemFilterValues {
  q: string;
  category: string;
  tier: string;
  published: string;
  sort: string;
}

export const EMPTY_FILTERS: AdminProblemFilterValues = {
  q: "",
  category: "",
  tier: "",
  published: "",
  sort: "LATEST",
};

/**
 * 어드민 문제 목록의 거르개 (#626).
 *
 * **공개 여부가 여기 있는 이유**는 묶음이 언제나 초안으로 들어오기 때문이다(#479).
 * 한 번에 스물다섯 개가 들어오면(#605) 초안이 공개된 문제 사이에 흩어지고, 지금은
 * 그것을 골라낼 방법이 페이지를 넘기며 눈으로 찾는 것뿐이다.
 *
 * 회원 관리(`AdminUserListPage`)와 같은 방식이다 — 글자를 칠 때마다 부르고, 조건이
 * 바뀌면 페이지를 0으로 되돌린다. 어드민 화면끼리 거르개가 다르게 굴면 익힐 것이 둘이 된다.
 */
export function AdminProblemFilters({
  values,
  onChange,
}: {
  values: AdminProblemFilterValues;
  onChange: (next: Partial<AdminProblemFilterValues>) => void;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      <Field label="검색">
        <Input
          placeholder="제목 또는 slug"
          value={values.q}
          onChange={(event) => onChange({ q: event.target.value })}
        />
      </Field>
      <Field label="공개 여부">
        {/* 이 화면에서 가장 자주 쓰는 조건이라 접어 두지 않는다. */}
        <Select value={values.published} onChange={(event) => onChange({ published: event.target.value })}>
          <option value="">전체</option>
          <option value="false">미공개만</option>
          <option value="true">공개만</option>
        </Select>
      </Field>
      <Field label="분야">
        <Select value={values.category} onChange={(event) => onChange({ category: event.target.value })}>
          <option value="">전체</option>
          {(Object.keys(CATEGORY_LABELS) as ProblemCategory[]).map((category) => (
            <option key={category} value={category}>
              {CATEGORY_LABELS[category]}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="티어">
        <Select value={values.tier} onChange={(event) => onChange({ tier: event.target.value })}>
          <option value="">전체</option>
          {(Object.keys(TIER_LABELS) as DifficultyTier[]).map((tier) => (
            <option key={tier} value={tier}>
              {TIER_LABELS[tier]}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="정렬">
        <Select value={values.sort} onChange={(event) => onChange({ sort: event.target.value })}>
          {PROBLEM_SORTS.map((sort) => (
            <option key={sort.value} value={sort.value}>
              {sort.label}
            </option>
          ))}
        </Select>
      </Field>
    </div>
  );
}
