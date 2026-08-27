"use client";

import type { RegexSpec } from "@/entities/problem";
import { CheckboxField, Field, Textarea } from "@/shared/ui";
import { countCases } from "../model/regexCases";

/**
 * 정규식 문제의 확인 문자열 (#653).
 *
 * **정답 패턴 칸이 없다.** SQL·Redis 는 정답을 돌려 기대값을 만들지만 여기서는
 * `+`/`-` 가 곧 기대값이다 — 정답 패턴으로 만들면 **출제자가 실수한 패턴이 그대로
 * 정답이 되어** 아무도 그것을 잡을 수 없다.
 */
export function RegexSpecEditor({
  value,
  onChange,
}: {
  value: RegexSpec;
  onChange: (next: RegexSpec) => void;
}) {
  const counts = countCases(value.cases);

  return (
    <div className="space-y-4">
      <p className="text-xs text-ink-muted">
        푸는 사람은 <b>패턴 하나</b>를 냅니다. 언어를 고르지 않습니다 — 엔진은 문제가
        정합니다(Python <code>re</code>). 확인 문자열은 <b>푸는 사람에게 보이지 않습니다.</b>
      </p>

      <Field label="확인할 문자열">
        <div className="space-y-2">
          <p className="text-xs text-ink-muted">
            한 줄에 하나. <code>+</code> 는 맞아야 하는 것, <code>-</code> 는 맞으면 안 되는 것입니다.
          </p>
          <Textarea
            rows={10}
            className="font-mono text-xs"
            value={value.cases}
            onChange={(event) => onChange({ ...value, cases: event.target.value })}
            placeholder={"+user@codekr.kr\n+a.b@c.co.kr\n-user@\n-@codekr.kr"}
            required
          />
          <p className="text-xs text-ink-muted">
            맞아야 하는 것 {counts.positive}줄 · 맞으면 안 되는 것 {counts.negative}줄
            {counts.malformed > 0 ? (
              <span className="text-danger"> · 표시가 없는 줄 {counts.malformed}</span>
            ) : null}
          </p>
          {counts.negative === 0 ? (
            <p className="text-xs text-danger">
              <b>맞으면 안 되는 문자열이 없으면 문제가 아닙니다</b> — <code>.*</code> 가 통과합니다.
            </p>
          ) : null}
        </div>
      </Field>

      <div className="flex gap-4">
        <CheckboxField
          label="전체가 맞아야 함"
          checked={value.fullMatch}
          onCheckedChange={(checked) => onChange({ ...value, fullMatch: checked })}
        />
        <CheckboxField
          label="대소문자 무시"
          checked={value.ignoreCase}
          onCheckedChange={(checked) => onChange({ ...value, ignoreCase: checked })}
        />
      </div>
      <p className="text-xs text-ink-muted">
        <b>전체 일치와 부분 일치는 다른 문제입니다.</b> 어느 쪽인지 지문에 적어 주세요 —
        부분 일치로 두면 <code>.</code> 하나로도 통과하는 문제가 많습니다.
      </p>
    </div>
  );
}
