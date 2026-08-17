"use client";

import type { Runtime } from "@/entities/problem";
import { VERDICT_LABELS } from "@/entities/submission";
import { Button, Field, Input, Select } from "@/shared/ui";
import { useState } from "react";
import { FILTER_LABELS, SORTS, VERDICTS, activeChips } from "../model/filters";
import type { FilterKey, Filters } from "../model/filters";

/**
 * 제출 목록 필터 (#76).
 *
 * **모든 입력에 라벨을 붙인다.** placeholder 로 대신하지 않는다 — 값을 넣으면 사라져서,
 * 정작 값이 들어 있을 때 그것이 무슨 필터인지 알 수 없게 된다.
 */
export function FilterPanel({
  filters,
  hidden,
  runtimes,
  onChange,
}: {
  filters: Filters;
  /** 문제 상세·프로필처럼 범위가 고정된 곳에서는 그 필터를 아예 그리지 않는다. */
  hidden: FilterKey[];
  runtimes: Runtime[];
  onChange: (key: FilterKey, value: string) => void;
}) {
  // 접어 둔 필터가 이미 걸려 있으면 펼친 채로 시작한다 —
  // 걸린 필터를 고치려고 매번 펼치게 하면 안 된다.
  const secondaryActive = activeChips(filters, hidden).some((key) =>
    (["problemKey", "nickname", "from", "to"] as FilterKey[]).includes(key),
  );
  const [expanded, setExpanded] = useState(secondaryActive);

  const show = (key: FilterKey) => !hidden.includes(key);

  return (
    <div className="space-y-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label={FILTER_LABELS.verdict}>
          <Select value={filters.verdict ?? ""} onChange={(e) => onChange("verdict", e.target.value)}>
            <option value="">전체</option>
            {VERDICTS.map((verdict) => (
              <option key={verdict} value={verdict}>
                {VERDICT_LABELS[verdict]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={FILTER_LABELS.runtimeId}>
          <Select value={filters.runtimeId ?? ""} onChange={(e) => onChange("runtimeId", e.target.value)}>
            <option value="">전체</option>
            {runtimes.map((runtime) => (
              <option key={runtime.id} value={runtime.id}>
                {runtime.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={FILTER_LABELS.sort}>
          <Select value={filters.sort ?? "LATEST"} onChange={(e) => onChange("sort", e.target.value)}>
            {SORTS.map((sort) => (
              <option key={sort.value} value={sort.value}>
                {sort.label}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <Button
        type="button"
        variant="secondary"
        className="px-3 py-1 text-xs"
        onClick={() => setExpanded((it) => !it)}
        aria-expanded={expanded}
      >
        {expanded ? "필터 접기" : "필터 더보기"}
      </Button>

      {expanded ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {show("problemKey") ? (
            <Field label={FILTER_LABELS.problemKey}>
              <Input
                value={filters.problemKey ?? ""}
                onChange={(e) => onChange("problemKey", e.target.value)}
                placeholder="9 또는 two-sum"
              />
            </Field>
          ) : null}
          {show("nickname") ? (
            <Field label={FILTER_LABELS.nickname}>
              <Input
                value={filters.nickname ?? ""}
                onChange={(e) => onChange("nickname", e.target.value)}
                placeholder="닉네임"
              />
            </Field>
          ) : null}
          <Field label={FILTER_LABELS.from}>
            <Input type="date" value={filters.from ?? ""} onChange={(e) => onChange("from", e.target.value)} />
          </Field>
          <Field label={FILTER_LABELS.to}>
            <Input type="date" value={filters.to ?? ""} onChange={(e) => onChange("to", e.target.value)} />
          </Field>
        </div>
      ) : null}
    </div>
  );
}
