"use client";

import Link from "next/link";
import { Badge, Card } from "@/components/ui";
import { VERDICT_LABELS, formatMemory, verdictTone } from "@/lib/labels";
import type { JudgeProgress } from "@/lib/useJudgeStream";

/** 채점 진행 상황. 테스트케이스가 하나씩 채워지는 과정을 그대로 보여준다. */
export function JudgeProgressPanel({ progress }: { progress: JudgeProgress }) {
  if (!progress.submissionId) return null;

  const total = Math.max(progress.totalCount, progress.results.length);
  const done = progress.results.length;
  const percent = total === 0 ? 0 : Math.round((done / total) * 100);

  return (
    <Card className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-ink">
          {progress.finished ? "채점 완료" : `채점 중… (${done}/${total})`}
        </h3>
        {progress.finished && progress.verdict ? (
          <Badge tone={verdictTone(progress.verdict)}>
            {VERDICT_LABELS[progress.verdict]} · {progress.passedCount}/{progress.totalCount}
          </Badge>
        ) : (
          <span className="text-xs text-ink-muted">실시간</span>
        )}
      </div>

      <div className="h-1.5 overflow-hidden rounded-full bg-surface-muted">
        <div
          className="h-full rounded-full bg-brand transition-all duration-300"
          style={{ width: `${percent}%` }}
        />
      </div>

      {progress.compileError ? (
        <pre className="max-h-40 overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-danger">
          {progress.compileError}
        </pre>
      ) : null}

      <ul className="space-y-1.5">
        {progress.results.map((result) => (
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

      {progress.finished ? (
        <Link
          href={`/submissions/${progress.submissionId}`}
          className="block text-center text-sm font-medium text-brand hover:underline"
        >
          제출 상세 보기
        </Link>
      ) : null}
    </Card>
  );
}
