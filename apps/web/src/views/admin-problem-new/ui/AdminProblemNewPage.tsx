"use client";

import { problemApi } from "@/entities/problem";
import { RequireAuth } from "@/features/auth";
import { BLANK_PROBLEM, ProblemForm } from "@/features/problem-editor";
export function AdminProblemNewPage() {
  return (
    <RequireAuth adminOnly>
      <div className="space-y-4">
        <h1 className="text-2xl font-bold text-ink">문제 등록</h1>
        <ProblemForm
          initial={BLANK_PROBLEM}
          submitLabel="등록"
          onSubmit={(values) => problemApi.create(values)}
        />
      </div>
    </RequireAuth>
  );
}
