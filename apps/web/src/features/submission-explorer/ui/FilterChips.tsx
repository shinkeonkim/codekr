"use client";

import type { Runtime } from "@/entities/problem";
import { VERDICT_LABELS } from "@/entities/submission";
import type { Verdict } from "@/entities/submission";
import { FILTER_LABELS, activeChips } from "../model/filters";
import type { FilterKey, Filters } from "../model/filters";

/**
 * 걸려 있는 필터를 칩으로 보여주고 개별 제거한다 (#76).
 *
 * **접어 둔 필터가 걸려 있을 때 그 사실이 보여야 한다.** 보이지 않으면 사용자는
 * 목록이 왜 비었는지 알 수 없다.
 */
export function FilterChips({
  filters,
  hidden,
  runtimes,
  onRemove,
  onClear,
}: {
  filters: Filters;
  hidden: FilterKey[];
  runtimes: Runtime[];
  onRemove: (key: FilterKey) => void;
  onClear: () => void;
}) {
  const chips = activeChips(filters, hidden);
  if (chips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {chips.map((key) => (
        <button
          key={key}
          type="button"
          onClick={() => onRemove(key)}
          className="inline-flex items-center gap-1.5 rounded-full border border-brand/40 bg-brand/10 px-2.5 py-1 text-xs text-ink transition hover:border-brand"
          aria-label={`${FILTER_LABELS[key]} 필터 제거`}
        >
          <span className="text-ink-muted">{FILTER_LABELS[key]}</span>
          <span className="font-medium">{displayValue(key, filters[key] ?? "", runtimes)}</span>
          <span aria-hidden className="text-ink-muted">
            ×
          </span>
        </button>
      ))}
      {chips.length > 1 ? (
        <button
          type="button"
          onClick={onClear}
          className="px-1.5 text-xs text-ink-muted underline-offset-2 hover:underline"
        >
          모두 지우기
        </button>
      ) : null}
    </div>
  );
}

/** 저장된 값이 아니라 **사람이 읽는 값**을 보여준다. `python:3.12` 가 아니라 `Python 3.12`. */
function displayValue(key: FilterKey, value: string, runtimes: Runtime[]): string {
  if (key === "verdict") return VERDICT_LABELS[value as Verdict] ?? value;
  if (key === "runtimeId") return runtimes.find((it) => it.id === value)?.label ?? value;
  return value;
}
