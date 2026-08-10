"use client";

import { BLANK_PROBLEM, ProblemForm } from "@/components/ProblemForm";
import { RequireAuth } from "@/components/RequireAuth";
import { api } from "@/lib/api";

export default function NewProblemPage() {
  return (
    <RequireAuth adminOnly>
      <div className="space-y-4">
        <h1 className="text-2xl font-bold text-ink">문제 등록</h1>
        <ProblemForm
          initial={BLANK_PROBLEM}
          submitLabel="등록"
          onSubmit={(values) => api.createProblem(values)}
        />
      </div>
    </RequireAuth>
  );
}
