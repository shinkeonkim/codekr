"use client";

import { useProblem } from "@/entities/problem";
import { Card, EmptyState } from "@/shared/ui";
import { ProblemHeader } from "@/widgets/problem-tabs";
import { SolveWorkspace } from "@/widgets/solve-workspace";
import { use } from "react";

export function ProblemSolvePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const { problem, error } = useProblem(slug);

  if (error) return <EmptyState title={error} />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* 코드를 쓰는 동안에도 지문을 볼 수 있어야 하므로 요약을 함께 둔다. */}
      <section className="space-y-4">
        <ProblemHeader problem={problem} />
        <Card className="p-5">
          <h2 className="mb-1.5 text-sm font-semibold text-ink">문제</h2>
          <div className="prose-kr max-h-[28rem] overflow-auto text-sm text-ink-muted">
            {problem.description}
          </div>
        </Card>
        {problem.examples.length > 0 ? (
          <Card className="space-y-3 p-5">
            <h2 className="text-sm font-semibold text-ink">예제</h2>
            {problem.examples.map((example) => (
              <div key={example.seq} className="grid gap-2 sm:grid-cols-2">
                <div>
                  <p className="mb-1 text-xs font-medium text-ink-muted">입력 {example.seq}</p>
                  <pre className="overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-ink">
                    {example.input}
                  </pre>
                </div>
                <div>
                  <p className="mb-1 text-xs font-medium text-ink-muted">출력 {example.seq}</p>
                  <pre className="overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-ink">
                    {example.output}
                  </pre>
                </div>
              </div>
            ))}
          </Card>
        ) : null}
      </section>

      <section>
        <SolveWorkspace problem={problem} />
      </section>
    </div>
  );
}
