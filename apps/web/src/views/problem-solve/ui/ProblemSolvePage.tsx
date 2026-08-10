"use client";

import { useProblem } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { EmptyState } from "@/shared/ui";
import { ProblemHeader } from "@/widgets/problem-tabs";
import { SolveWorkspace } from "@/widgets/solve-workspace";
import { use, useState } from "react";

/**
 * 코드 제출 탭.
 *
 * 지문은 여기 두지 않는다 (#75). 문제를 읽는 탭이 따로 있는데 같은 내용을 옆에
 * 다시 두면 코드를 쓰는 공간만 좁아진다. 대신 **코드를 쓰는 동안 필요한 것**은 남긴다 —
 * 제목·난이도와 시간·메모리 제한(헤더), 그리고 예제 입력(실행 입력칸의 기본값).
 */
export function ProblemSolvePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const { problem, error } = useProblem(slug);
  const [runtime, setRuntime] = useState<Runtime | undefined>();

  if (error) return <EmptyState title={error} />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <ProblemHeader problem={problem} runtime={runtime} />
      <SolveWorkspace problem={problem} onRuntimeChange={setRuntime} />
    </div>
  );
}
