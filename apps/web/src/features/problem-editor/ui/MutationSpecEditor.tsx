"use client";

import type { MutationSpec } from "@/entities/problem";
import { Button, Field, Input, Textarea } from "@/shared/ui";

/**
 * 테스트 작성 문제의 구현들 (#652).
 *
 * **정답 시험 칸이 없다.** 기대값은 구조가 정한다 — 올바른 구현은 통과, 버그 심은
 * 구현은 전부 실패다. 정답 시험으로 기대값을 만들면 **출제자가 놓친 버그가 곧
 * "잡지 않아도 되는 것"** 이 된다.
 */
export function MutationSpecEditor({
  value,
  onChange,
}: {
  value: MutationSpec;
  onChange: (next: MutationSpec) => void;
}) {
  const setMutants = (mutants: MutationSpec["mutants"]) => onChange({ ...value, mutants });

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        <b>채점이 뒤집혀 있습니다.</b> 사용자가 시험을 내고 우리가 구현을 숨깁니다 —
        올바른 구현은 <b>통과해야</b> 하고 버그 심은 구현은 <b>전부 실패해야</b> 합니다.
        구현도 이름표도 <b>푸는 사람에게 가지 않습니다.</b>
      </p>
      <p className="text-xs text-ink-muted">
        사용자의 시험이 <b>구현 수만큼 돕니다</b> — 뮤턴트 다섯이면 여섯 번입니다.
        시간 제한을 그만큼 넉넉히 잡으세요.
      </p>

      <Field label="올바른 구현">
        <Textarea
          rows={6}
          className="font-mono text-xs"
          value={value.referenceSource}
          onChange={(event) => onChange({ ...value, referenceSource: event.target.value })}
          placeholder={"def average(xs):\n    return sum(xs) / len(xs)"}
          required
        />
      </Field>

      <Field label="버그를 심은 구현">
        <div className="space-y-3">
          <p className="text-xs text-ink-muted">
            글자 하나만 바꾼 것이 좋습니다 — 그래야 <b>어떤 시험이 그것을 잡는가</b>가
            분명해집니다. 이름표는 출제자를 위한 것이고 나가지 않습니다.
          </p>
          {value.mutants.map((mutant, index) => (
            <div key={index} className="space-y-1 rounded-card border border-border p-3">
              <div className="flex items-center gap-2">
                <Input
                  value={mutant.label ?? ""}
                  onChange={(event) =>
                    setMutants(
                      value.mutants.map((it, i) =>
                        i === index ? { ...it, label: event.target.value || null } : it,
                      ),
                    )
                  }
                  placeholder={`무엇을 심었나 (예: 빈 목록을 안 본다)`}
                />
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setMutants(value.mutants.filter((_, i) => i !== index))}
                >
                  삭제
                </Button>
              </div>
              <Textarea
                rows={4}
                className="font-mono text-xs"
                value={mutant.source}
                onChange={(event) =>
                  setMutants(
                    value.mutants.map((it, i) =>
                      i === index ? { ...it, source: event.target.value } : it,
                    ),
                  )
                }
                placeholder={"def average(xs):\n    return sum(xs) / len(xs) - 1"}
              />
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            onClick={() => setMutants([...value.mutants, { label: null, source: "" }])}
          >
            구현 추가
          </Button>
          {value.mutants.length === 0 ? (
            <p className="text-xs text-danger">
              <b>버그를 심은 구현이 없으면 문제가 아닙니다</b> — 아무것도 확인하지 않는
              시험이 통과합니다.
            </p>
          ) : null}
        </div>
      </Field>
    </div>
  );
}
