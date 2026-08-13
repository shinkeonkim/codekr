"use client";

import { Badge, Card } from "@/shared/ui";
import type { ReactNode } from "react";

/**
 * 폼의 구획 하나 (#337).
 *
 * **탭이 아니라 접이식이다.** 탭은 세 가지를 잃는다.
 *
 * 1. **오류가 어느 구획에 있는지** 보이지 않는다 — 이슈가 "탭 방식의 가장 큰 위험" 이라
 *    적은 것이다. 접이식은 그 구획을 펴 두면 그만이다
 * 2. 저장하기 전에 **전체를 훑어볼 수 없다.** 문제 등록은 지문과 테스트케이스가
 *    맞물리는 일이라, 한쪽만 보면서 채우기 어렵다
 * 3. AI 초안(#230)이 여러 구획을 한 번에 채울 때 **어디가 채워졌는지** 알 수 없다
 *
 * 접이식은 **모든 구획이 늘 마운트되어 있다** — 열고 닫아도 채운 것이 사라지지 않는다.
 */
export function FormSection({
  title,
  description,
  /** 공개하려면 채워야 하는 구획인가 (#337). 화면이 검증기의 규칙을 알게 한다. */
  required = false,
  /** 처음에 펼쳐 둘지. 등록은 순서대로 펴 두고, 수정은 접어서 목차로 쓴다. */
  defaultOpen = true,
  children,
}: {
  title: string;
  description?: string;
  required?: boolean;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  return (
    <Card className="p-0">
      <details open={defaultOpen} className="group">
        <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 p-5">
          <h2 className="text-sm font-semibold text-ink">{title}</h2>
          {required ? <Badge tone="warn">공개에 필요</Badge> : <Badge tone="muted">선택</Badge>}
          {description ? (
            <span className="w-full text-xs text-ink-muted sm:w-auto">{description}</span>
          ) : null}
          {/* 접힘 표시. 화살표만으로도 여닫는 것임을 알 수 있다. */}
          <span className="ml-auto text-xs text-ink-muted group-open:hidden">펼치기 ▾</span>
          <span className="ml-auto hidden text-xs text-ink-muted group-open:inline">접기 ▴</span>
        </summary>
        <div className="space-y-4 border-t border-border p-5">{children}</div>
      </details>
    </Card>
  );
}
