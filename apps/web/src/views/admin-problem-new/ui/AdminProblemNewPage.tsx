"use client";

import { problemApi } from "@/entities/problem";
import { BLANK_PROBLEM, ProblemForm, toRequest } from "@/features/problem-editor";
export function AdminProblemNewPage() {
  return (
      <div className="space-y-4">
        <h1 className="text-2xl font-bold text-ink">문제 등록</h1>
        <ProblemForm
          initial={BLANK_PROBLEM}
          submitLabel="등록"
          onSubmit={(values) => problemApi.create(toRequest(values))}
        />
      </div>
  );
}
