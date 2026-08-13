"use client";

import { problemApi } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { Badge, Button, CheckboxField } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 이 문제를 풀 수 있는 언어 (#419).
 *
 * **하나도 고르지 않은 것이 곧 "전부 허용" 이다.** 빈 목록과 전부 허용을 다른 값으로
 * 두면, 실수로 하나도 안 고른 문제가 **아무 언어로도 풀 수 없는 문제**가 된다 —
 * 그편이 훨씬 나쁘다. 대신 지금 무엇이 되는지 화면이 늘 한 줄로 말한다.
 */
export function AllowedRuntimeEditor({
  value,
  onChange,
}: {
  value: string[];
  onChange: (next: string[]) => void;
}) {
  const [runtimes, setRuntimes] = useState<Runtime[]>([]);

  useEffect(() => {
    problemApi.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  const toggle = (id: string, on: boolean) =>
    onChange(on ? [...value, id] : value.filter((it) => it !== id));

  return (
    <div className="space-y-3">
      <p className="text-xs text-ink-muted">
        {value.length === 0 ? (
          <>아무것도 고르지 않으면 <span className="text-ink">이 유형의 언어 전부</span>로 풀 수 있습니다.</>
        ) : (
          <>고른 언어로만 풀 수 있습니다. 나머지는 문제 화면에 나오지도 않고, 서버가 제출을 막습니다.</>
        )}
      </p>

      <div className="grid gap-1.5 sm:grid-cols-2">
        {runtimes.map((runtime) => (
          <CheckboxField
            key={runtime.id}
            label={runtime.label}
            checked={value.includes(runtime.id)}
            onCheckedChange={(on) => toggle(runtime.id, on)}
          />
        ))}
      </div>

      {value.length > 0 ? (
        <div className="flex flex-wrap items-center gap-1.5">
          {value.map((id) => (
            <Badge key={id} tone="muted">
              {runtimes.find((it) => it.id === id)?.label ?? id}
            </Badge>
          ))}
          {/* 되돌리는 길을 한 번에 준다 — 하나씩 끄다 보면 무엇이 남았는지 놓친다. */}
          <Button variant="ghost" className="px-2 py-0.5 text-xs" onClick={() => onChange([])}>
            전부 허용으로
          </Button>
        </div>
      ) : null}
    </div>
  );
}
