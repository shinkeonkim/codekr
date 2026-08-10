"use client";

import { RequireAuth } from "@/features/auth";
import { SubmissionExplorer } from "@/features/submission-explorer";
import { Suspense } from "react";

export function SubmissionExplorePage() {
  return (
    <RequireAuth>
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">전체 제출</h1>
          <p className="mt-1 text-sm text-ink-muted">
            다른 회원이 어떤 문제를 어떤 언어로 풀었는지 살펴보세요. 코드는 작성자가 공개한 경우에만 보입니다.
          </p>
        </div>
        {/* useSearchParams 를 쓰는 컴포넌트는 Suspense 경계가 필요하다. */}
        <Suspense fallback={<p className="py-8 text-center text-sm text-ink-muted">불러오는 중…</p>}>
          <SubmissionExplorer />
        </Suspense>
      </div>
    </RequireAuth>
  );
}
