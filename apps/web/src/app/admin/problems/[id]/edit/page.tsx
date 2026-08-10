"use client";

import { use, useEffect, useState } from "react";
import { ProblemForm, toFormValues } from "@/components/ProblemForm";
import type { ProblemFormValues } from "@/components/ProblemForm";
import { RequireAuth } from "@/components/RequireAuth";
import { EmptyState } from "@/components/ui";
import { api } from "@/lib/api";
import type { ProblemVerification } from "@/lib/types";

export default function EditProblemPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireAuth adminOnly>
      <EditProblem id={Number(id)} />
    </RequireAuth>
  );
}

function EditProblem({ id }: { id: number }) {
  const [initial, setInitial] = useState<ProblemFormValues | null>(null);
  const [verification, setVerification] = useState<ProblemVerification | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .adminProblem(id)
      .then((problem) => {
        setInitial(toFormValues(problem));
        setVerification(problem.verification);
      })
      .catch(() => setError("문제를 불러오지 못했습니다."));
  }, [id]);

  if (error) return <EmptyState title={error} />;
  if (!initial) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">문제 수정</h1>
      <ProblemForm
        initial={initial}
        submitLabel="저장"
        problemId={id}
        verification={verification}
        onSubmit={(values) => api.updateProblem(id, values)}
      />
    </div>
  );
}
