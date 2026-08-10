"use client";

import { use, useEffect, useState } from "react";
import { SolveWorkspace } from "@/components/SolveWorkspace";
import { TierBadge } from "@/components/TierBadge";
import { Badge, Card, EmptyState } from "@/components/ui";
import { api } from "@/lib/api";
import { CATEGORY_LABELS } from "@/lib/labels";
import type { ProblemDetail } from "@/lib/types";

export default function ProblemDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .problem(slug)
      .then(setProblem)
      .catch(() => setError("문제를 찾을 수 없습니다."));
  }, [slug]);

  if (error) return <EmptyState title={error} description="목록에서 다른 문제를 골라 보세요." />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="space-y-4">
        <header className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="info">{CATEGORY_LABELS[problem.category]}</Badge>
            <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
            <span className="text-xs text-ink-muted">
              시간 {problem.timeLimitMs}ms · 메모리 {problem.memoryLimitMb}MB
            </span>
          </div>
          <h1 className="text-2xl font-bold text-ink">{problem.title}</h1>
        </header>

        <Card className="space-y-5 p-5">
          <Section title="문제">{problem.description}</Section>
          {problem.inputDescription ? <Section title="입력">{problem.inputDescription}</Section> : null}
          {problem.outputDescription ? <Section title="출력">{problem.outputDescription}</Section> : null}
        </Card>

        {problem.examples.length > 0 ? (
          <Card className="space-y-4 p-5">
            <h2 className="text-sm font-semibold text-ink">예제</h2>
            {problem.examples.map((example) => (
              <div key={example.seq} className="grid gap-2 sm:grid-cols-2">
                <ExampleBlock title={`입력 ${example.seq}`} body={example.input} />
                <ExampleBlock title={`출력 ${example.seq}`} body={example.output} />
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

function Section({ title, children }: { title: string; children: string }) {
  return (
    <div>
      <h2 className="mb-1.5 text-sm font-semibold text-ink">{title}</h2>
      <div className="prose-kr text-sm text-ink-muted">{children}</div>
    </div>
  );
}

function ExampleBlock({ title, body }: { title: string; body: string }) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium text-ink-muted">{title}</p>
      <pre className="overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-ink">{body}</pre>
    </div>
  );
}
