"use client";

import { problemApi } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { Alert, Badge, Button, CodeEditor } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 함수형 문제의 언어별 하네스 (#448, #421).
 *
 * **하네스는 남에게 보이지 않는 코드다.** 입력을 읽고 사용자가 구현한 함수를 부르고
 * 결과를 찍는다 — 정답의 일부나 판정 방식이 들어가므로 사용자 화면에도 오류 메시지에도
 * 나가지 않는다.
 *
 * **여기 쓴 언어가 곧 이 문제를 풀 수 있는 언어다.** 허용 목록(#419)을 따로 고르지
 * 않는다 — 두 곳이 같은 것을 정하면 어긋난다.
 */
export function HarnessEditor({
  value,
  onChange,
}: {
  value: Record<string, string>;
  onChange: (next: Record<string, string>) => void;
}) {
  const [runtimes, setRuntimes] = useState<Runtime[]>([]);

  useEffect(() => {
    // **함수형을 지원하는 런타임만 온다** — 실행기가 방식을 아는 것만이다.
    // **고치는 문제(#651)도 같은 목록이다** — 하네스를 실을 수 있는 언어가 곧
    // 허용 목록이고, 그 규칙은 두 유형이 공유한다 (`RuntimeDefinition.canSolve`).
    problemApi.runtimes("JUDGE_FUNCTION").then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  const written = Object.keys(value);

  const set = (runtimeId: string, source: string) => onChange({ ...value, [runtimeId]: source });
  const remove = (runtimeId: string) => {
    const next = { ...value };
    delete next[runtimeId];
    onChange(next);
  };

  return (
    <div className="space-y-4">
      <Alert tone="warn">
        하네스는 **푸는 사람에게 보이지 않습니다.** 오류 메시지에도 나가지 않습니다.
        여기에 쓴 언어로만 이 문제를 풀 수 있습니다.
      </Alert>

      {runtimes.length === 0 ? (
        <p className="text-xs text-ink-muted">
          함수 구현 문제를 지원하는 실행 환경이 없습니다. 실행기가 그 언어의 방식을 알아야
          합니다.
        </p>
      ) : null}

      {runtimes.map((runtime) => {
        const source = value[runtime.id];
        const active = source !== undefined;
        return (
          <div key={runtime.id} className="space-y-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-ink">{runtime.label}</span>
              {active ? <Badge tone="muted">이 언어로 풀 수 있음</Badge> : null}
              <span className="flex-1" />
              {active ? (
                <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => remove(runtime.id)}>
                  빼기
                </Button>
              ) : (
                <Button
                  variant="secondary"
                  className="px-2 py-0.5 text-xs"
                  onClick={() => set(runtime.id, "")}
                >
                  이 언어 열기
                </Button>
              )}
            </div>
            {active ? (
              <CodeEditor
                language={runtime.monacoLanguage}
                value={source}
                onChange={(next) => set(runtime.id, next)}
                height={220}
              />
            ) : null}
          </div>
        );
      })}

      {written.length === 0 ? (
        // 공개하려면 하나는 있어야 한다 — 없으면 아무도 풀 수 없는 문제가 된다.
        <p className="text-xs text-danger">
          하네스가 하나도 없으면 공개할 수 없습니다. 아무도 풀 수 없는 문제가 됩니다.
        </p>
      ) : null}
    </div>
  );
}
