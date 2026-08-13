"use client";

import { ProblemStatsSummary, RuntimeLimitNotice, useProblem } from "@/entities/problem";
import type { ProblemDetail } from "@/entities/problem";
import { TagChips } from "@/entities/tag";
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
          <div className="mt-2">
            <ProblemStatsSummary stats={problem.stats} />
          </div>
          <RuntimeLimitNotice problem={problem} />
          {/* 접혀 있다 — 태그는 답의 일부다 (#232). */}
          <div className="mt-2">
            <TagChips tags={problem.tags} />
          </div>
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

      {/*
        누가 만들고 어디서 왔는지 (#236).

        **지문 아래, 예제 위가 아니라 맨 끝이다** — 문제를 푸는 데 필요한 것이 먼저다.
        아무것도 없으면 상자 자체를 그리지 않는다.
      */}
      <Credits problem={problem} />

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

/** 출제자·검수자·출처 (#236). */
function Credits({ problem }: { problem: ProblemDetail }) {
  const hasCredits = problem.setters.length > 0 || problem.reviewers.length > 0;
  if (!hasCredits && !problem.sourceLabel) return null;

  return (
    <Card className="space-y-2 p-5 text-xs text-ink-muted">
      {problem.setters.length > 0 ? (
        <p>
          출제{" "}
          {problem.setters.map((each, index) => (
            <span key={each.userId}>
              {index > 0 ? ", " : ""}
              <PersonLink nickname={each.nickname} />
            </span>
          ))}
        </p>
      ) : null}
      {problem.reviewers.length > 0 ? (
        <p>
          검수{" "}
          {problem.reviewers.map((each, index) => (
            <span key={each.userId}>
              {index > 0 ? ", " : ""}
              <PersonLink nickname={each.nickname} />
            </span>
          ))}
        </p>
      ) : null}
      {problem.sourceLabel ? (
        <p>
          출처{" "}
          {problem.sourceUrl ? (
            /*
              **바깥으로 나가는 링크다.** 같은 사이트가 아니라는 것을 사용자가 알아야 하고,
              새 창으로 열 때 opener 를 넘기지 않는다 (마크다운 링크와 같은 규칙, #137).
            */
            <a
              href={problem.sourceUrl}
              className="text-brand underline"
              target="_blank"
              rel="noreferrer noopener nofollow"
            >
              {problem.sourceLabel} ↗
            </a>
          ) : (
            <span className="text-ink">{problem.sourceLabel}</span>
          )}
        </p>
      ) : null}
    </Card>
  );
}

/** 탈퇴한 사람은 갈 곳이 없다 — 링크로 만들지 않는다 (#140). */
function PersonLink({ nickname }: { nickname: string }) {
  if (nickname === "탈퇴한 사용자") return <span>{nickname}</span>;
  return (
    <Link href={`/users/${encodeURIComponent(nickname)}`} className="text-brand hover:underline">
      {nickname}
    </Link>
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
