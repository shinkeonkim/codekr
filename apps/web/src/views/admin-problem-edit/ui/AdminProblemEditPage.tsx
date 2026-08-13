"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemVerification } from "@/entities/problem";
import type { ProblemTag } from "@/entities/tag";
import { ProblemForm, toFormValues } from "@/features/problem-editor";
import type { ProblemFormValues } from "@/features/problem-editor";
import { toRequest } from "@/features/problem-editor";
import { ProblemTagEditor } from "@/features/problem-tags";
import { EmptyState } from "@/shared/ui";
import { use, useEffect, useState } from "react";

export function AdminProblemEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  // 인가는 app/admin/layout.tsx 가 한 번에 맡는다 (#131).
  return <EditProblem id={Number(id)} />;
}

function EditProblem({ id }: { id: number }) {
  const [initial, setInitial] = useState<ProblemFormValues | null>(null);
  const [verification, setVerification] = useState<ProblemVerification | null>(
    null,
  );
  const [tags, setTags] = useState<ProblemTag[]>([]);
  // 이 유형이 정답 코드 검증을 지원하는가 (#495). 서버가 말해 준다.
  const [canVerifySolution, setCanVerifySolution] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    problemApi
      .adminDetail(id)
      .then((problem) => {
        setInitial(toFormValues(problem));
        setVerification(problem.verification);
        setCanVerifySolution(problem.canVerifySolution ?? true);
        setTags(problem.tags);
      })
      .catch(() => setError("문제를 불러오지 못했습니다."));
  }, [id]);

  if (error) return <EmptyState title={error} />;
  if (!initial)
    return (
      <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>
    );

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">문제 수정</h1>
      <ProblemForm
        initial={initial}
        submitLabel="저장"
        problemId={id}
        verification={verification}
        canVerifySolution={canVerifySolution}
        onSubmit={(values) => problemApi.update(id, toRequest(values))}
      />
      {/* 문제 본문과 따로 저장한다 — 이유는 ProblemTagEditor 주석에 있다 (#232). */}
      <ProblemTagEditor problemId={id} initial={tags} />
    </div>
  );
}
