"use client";

import { STATUS_LABELS, VERDICT_LABELS, VISIBILITY_LABELS, submissionApi, verdictTone } from "@/entities/submission";
import type { SubmissionDetail, SubmissionVisibility } from "@/entities/submission";
import { UserLink } from "@/entities/user";
import { RequireAuth, useAuth } from "@/features/auth";
import { JudgeProgressPanel, useJudgeStream } from "@/features/judge-stream";
import { formatDateTime, formatMemory } from "@/shared/lib";
import { ApiError } from "@/shared/api";
import { Badge, Card, EmptyState, useToast } from "@/shared/ui";
import Link from "next/link";
import { use, useEffect, useState } from "react";

/** 채점이 끝나지 않았다면 짧게 폴링한다 (이 화면에 늦게 들어온 경우). */
const POLL_INTERVAL_MS = 2000;

export function SubmissionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireAuth>
      <SubmissionView id={Number(id)} />
    </RequireAuth>
  );
}

function SubmissionView({ id }: { id: number }) {
  const { user } = useAuth();
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { progress, watch } = useJudgeStream();
  const toast = useToast();

  const changeVisibility = async (visibility: SubmissionVisibility) => {
    try {
      await submissionApi.changeVisibility(id, visibility);
      setSubmission((previous) => (previous ? { ...previous, visibility } : previous));
      // 공개 범위는 되돌리기 어려운 결정이라, 바뀐 사실을 분명히 알린다.
      toast.success(`공개 범위를 "${VISIBILITY_LABELS[visibility]}" 로 바꿨습니다.`);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "공개 범위를 바꾸지 못했습니다.");
    }
  };

  // 화면 데이터는 폴링이 채운다. 채점이 끝나면 스스로 멈춘다.
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;

    const load = () => {
      submissionApi
        .detail(id)
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

  /**
   * 진행 표시는 WebSocket 이 채운다 (#78, #80).
   *
   * 폴링만 쓰면 2초마다 뭉텅이로 바뀌어 "멈춰 있는 것"과 구분되지 않는다. 테스트케이스가
   * 하나씩 통과하는 것이 보여야 기다릴 만하다고 느낀다. 폴링은 그대로 두어 이벤트가
   * 유실돼도 결과는 반드시 확정된다.
   */
  const judging = submission?.status === "PENDING" || submission?.status === "JUDGING";
  useEffect(() => {
    if (judging && progress.submissionId !== id) watch(id, submission?.totalCount ?? 0);
  }, [judging, id, progress.submissionId, submission?.totalCount, watch]);

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
            <UserLink nickname={submission.nickname} /> · {submission.runtimeId} ·{" "}
            {formatDateTime(submission.createdAt)}
          </p>
        </div>
        {submission.verdict ? (
          <Badge tone={verdictTone(submission.verdict)}>{VERDICT_LABELS[submission.verdict]}</Badge>
        ) : (
          <Badge>{STATUS_LABELS[submission.status]}</Badge>
        )}
      </header>

      {judging ? (
        <JudgeProgressPanel progress={progress} pending={submission?.status === "PENDING"} />
      ) : null}

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
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <h2 className="text-sm font-semibold text-ink">제출한 코드</h2>
          {/* 공개 범위는 작성자만 바꿀 수 있다. */}
          {user?.nickname === submission.nickname ? (
            <select
              className="ml-auto rounded-lg border border-border bg-surface px-2 py-1 text-xs text-ink"
              value={submission.visibility}
              onChange={(event) => changeVisibility(event.target.value as SubmissionVisibility)}
            >
              {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          ) : (
            <Badge>{VISIBILITY_LABELS[submission.visibility]}</Badge>
          )}
        </div>
        {submission.sourceVisible && submission.sourceCode !== null ? (
          <pre className="max-h-96 overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-ink">
            {submission.sourceCode}
          </pre>
        ) : (
          <p className="rounded-lg border border-dashed border-border px-4 py-8 text-center text-sm text-ink-muted">
            작성자가 공개하지 않은 코드입니다.
          </p>
        )}
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
