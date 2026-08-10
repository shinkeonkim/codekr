"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { RequireAuth } from "@/components/RequireAuth";
import { Badge, Card, EmptyState } from "@/components/ui";
import { api } from "@/lib/api";
import { STATUS_LABELS, VERDICT_LABELS, formatDateTime, formatMemory, verdictTone } from "@/lib/labels";
import type { SubmissionDetail } from "@/lib/types";

/** 채점이 끝나지 않았다면 짧게 폴링한다 (이 화면에 늦게 들어온 경우). */
const POLL_INTERVAL_MS = 2000;

export default function SubmissionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireAuth>
      <SubmissionView id={Number(id)} />
    </RequireAuth>
  );
}

function SubmissionView({ id }: { id: number }) {
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;

    const load = () => {
      api
        .submission(id)
        .then((detail) => {
          setSubmission(detail);
          if (detail.status === "PENDING" || detail.status === "JUDGING") {
            timer = setTimeout(load, POLL_INTERVAL_MS);
          }
        })
        .catch(() => setError("제출을 찾을 수 없거나 조회 권한이 없습니다."));
    };

    load();
    return () => clearTimeout(timer);
  }, [id]);

  if (error) return <EmptyState title={error} />;
  if (!submission) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1">
          <Link href={`/problems/${submission.problemSlug}`} className="text-sm text-brand hover:underline">
            {submission.problemTitle}
          </Link>
          <h1 className="text-2xl font-bold text-ink">제출 #{submission.id}</h1>
          <p className="mt-1 text-xs text-ink-muted">
            {submission.runtimeId} · {formatDateTime(submission.createdAt)}
          </p>
        </div>
        {submission.verdict ? (
          <Badge tone={verdictTone(submission.verdict)}>{VERDICT_LABELS[submission.verdict]}</Badge>
        ) : (
          <Badge>{STATUS_LABELS[submission.status]}</Badge>
        )}
      </header>

      <Card className="grid grid-cols-2 gap-4 p-5 sm:grid-cols-4">
        <Stat label="통과" value={`${submission.passedCount} / ${submission.totalCount}`} />
        <Stat label="최대 실행 시간" value={`${submission.maxRuntimeMs} ms`} />
        <Stat label="최대 메모리" value={formatMemory(submission.maxMemoryKb)} />
        <Stat label="상태" value={STATUS_LABELS[submission.status]} />
      </Card>

      {submission.compileError ? (
        <Card className="p-5">
          <h2 className="mb-2 text-sm font-semibold text-ink">컴파일 오류</h2>
          <pre className="max-h-60 overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-danger">
            {submission.compileError}
          </pre>
        </Card>
      ) : null}

      {submission.results.length > 0 ? (
        <Card className="p-5">
          <h2 className="mb-3 text-sm font-semibold text-ink">테스트케이스</h2>
          <ul className="space-y-1.5">
            {submission.results.map((result) => (
              <li
                key={result.seq}
                className="flex items-center gap-3 rounded-lg border border-border px-3 py-2 text-sm"
              >
                <span className="w-16 shrink-0 text-ink-muted">#{result.seq}</span>
                <Badge tone={verdictTone(result.verdict)}>{VERDICT_LABELS[result.verdict]}</Badge>
                <span className="ml-auto text-xs text-ink-muted">
                  {result.runtimeMs}ms · {formatMemory(result.memoryKb)}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <Card className="p-5">
        <h2 className="mb-2 text-sm font-semibold text-ink">제출한 코드</h2>
        <pre className="max-h-96 overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-ink">
          {submission.sourceCode}
        </pre>
      </Card>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-ink-muted">{label}</p>
      <p className="mt-0.5 font-semibold text-ink">{value}</p>
    </div>
  );
}
