"use client";

import { RuntimeLimitNotice, useProblem } from "@/entities/problem";
import { Button, Card, EmptyState } from "@/shared/ui";
import { ProblemHeader } from "@/widgets/problem-tabs";
import Link from "next/link";
import { use } from "react";

export function ProblemDetailPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const { problem, error } = useProblem(slug);

  if (error) return <EmptyState title={error} description="목록에서 다른 문제를 골라 보세요." />;
  if (!problem) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <ProblemHeader problem={problem} />
          <RuntimeLimitNotice problem={problem} />
        </div>
        <Link href={`/problems/${slug}/solve`}>
          <Button>코드 작성하기</Button>
        </Link>
      </div>

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
