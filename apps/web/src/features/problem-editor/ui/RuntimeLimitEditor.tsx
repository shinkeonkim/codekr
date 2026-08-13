"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemRuntimeLimit, Runtime } from "@/entities/problem";
import { Button, Input, Select } from "@/shared/ui";
import { useEffect, useState } from "react";

interface Props {
  limits: ProblemRuntimeLimit[];
  /** 문제 기본 제한. 새 항목의 출발값으로 쓴다. */
  baseTimeLimitMs: number;
  baseMemoryLimitMb: number;
  onChange: (limits: ProblemRuntimeLimit[]) => void;
}

/**
 * 언어/버전별 실행 제한 편집기 (#97).
 *
 * 언어마다 속도가 다르다. C++ 로 200ms 에 도는 풀이가 Python 으로는 2초가 걸린다.
 * **적지 않은 언어는 문제 기본 제한을 그대로 쓴다** — 여기에 모든 언어를 채워 넣으면
 * 런타임이 하나 늘 때마다 모든 문제를 손봐야 한다.
 */
export function RuntimeLimitEditor({
  limits,
  baseTimeLimitMs,
  baseMemoryLimitMb,
  onChange,
}: Props) {
  const [runtimes, setRuntimes] = useState<Runtime[]>([]);
  const [selected, setSelected] = useState("");

  useEffect(() => {
    problemApi.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  const available = runtimes.filter((runtime) => !limits.some((it) => it.runtimeId === runtime.id));
  const labelOf = (runtimeId: string) => runtimes.find((it) => it.id === runtimeId)?.label ?? runtimeId;

  const add = () => {
    const runtime = runtimes.find((it) => it.id === selected) ?? available[0];
    if (!runtime) return;
    // 문제 기본값에서 출발한다. 무엇을 얼마나 늘릴지는 기준값이 보여야 정할 수 있다.
    onChange([
      ...limits,
      {
        runtimeId: runtime.id,
        timeLimitMs: baseTimeLimitMs,
        memoryLimitMb: baseMemoryLimitMb,
      },
    ]);
    setSelected("");
  };

  const update = (runtimeId: string, patch: Partial<ProblemRuntimeLimit>) =>
    onChange(limits.map((it) => (it.runtimeId === runtimeId ? { ...it, ...patch } : it)));

  const remove = (runtimeId: string) =>
    onChange(limits.filter((it) => it.runtimeId !== runtimeId));

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <div>
                    <p className="mt-0.5 text-xs text-ink-muted">
            지정하지 않은 언어는 문제 기본 제한({baseTimeLimitMs}ms · {baseMemoryLimitMb}MB)을 씁니다.
          </p>
        </div>
        <Select
          className="ml-auto w-56"
          aria-label="추가할 언어"
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
          disabled={available.length === 0}
        >
          <option value="">추가할 언어 선택</option>
          {available.map((runtime) => (
            <option key={runtime.id} value={runtime.id}>
              {runtime.label}
            </option>
          ))}
        </Select>
        <Button type="button" variant="secondary" onClick={add} disabled={available.length === 0}>
          추가
        </Button>
      </div>

      {limits.length === 0 ? (
        <p className="text-xs text-ink-muted">모든 언어가 문제 기본 제한을 씁니다.</p>
      ) : (
        <ul className="space-y-2">
          {limits.map((limit) => (
            <li
              key={limit.runtimeId}
              className="flex flex-wrap items-center gap-2 rounded-lg border border-border px-3 py-2"
            >
              <span className="min-w-32 text-sm font-medium text-ink">{labelOf(limit.runtimeId)}</span>
              <label className="flex items-center gap-1.5 text-xs text-ink-muted">
                시간
                <Input
                  type="number"
                  className="w-24"
                  value={limit.timeLimitMs}
                  onChange={(event) =>
                    update(limit.runtimeId, { timeLimitMs: Number(event.target.value) })
                  }
                />
                ms
              </label>
              <label className="flex items-center gap-1.5 text-xs text-ink-muted">
                메모리
                <Input
                  type="number"
                  className="w-24"
                  value={limit.memoryLimitMb}
                  onChange={(event) =>
                    update(limit.runtimeId, { memoryLimitMb: Number(event.target.value) })
                  }
                />
                MB
              </label>
              <Button
                type="button"
                variant="ghost"
                className="ml-auto"
                onClick={() => remove(limit.runtimeId)}
              >
                제거
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
