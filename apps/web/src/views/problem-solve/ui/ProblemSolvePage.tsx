"use client";

import { useProblem } from "@/entities/problem";
import type { Runtime } from "@/entities/problem";
import { RequireAuth } from "@/features/auth";
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
/**
 * **로그인이 필요하다** (#459, #113).
 *
 * 전에는 막는 것이 제출 버튼뿐이라, 비로그인 사용자가 **에디터를 열고 언어를 고르고
 * 코드를 다 쓴 다음**에야 로그인이 필요하다는 것을 알았다. #113 이 정한 것은
 * "안내하고, 로그인 후 그 자리로 돌려보낸다" 이고 이 화면만 그것을 안 지키고 있었다.
 *
 * **탭은 그대로 보인다.** 감추면 비로그인 사용자는 제출이라는 것이 있는지도 모른다 —
 * 눌리되 안내하는 쪽이 #113 이 정한 동작이다.
 *
 * 문제 내용·제출 내역 탭은 그대로 열려 있다. 로그인 없이 문제를 읽는 것은 막을 이유가 없다.
 */
export function ProblemSolvePage({ params }: { params: Promise<{ slug: string }> }) {
  return (
    <RequireAuth>
      <SolveView params={params} />
    </RequireAuth>
  );
}

function SolveView({ params }: { params: Promise<{ slug: string }> }) {
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
