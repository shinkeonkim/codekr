"use client";

import { VERDICT_LABELS, verdictTone } from "@/entities/submission";
import type { JudgeProgress } from "../model/useJudgeStream";
import { formatMemory } from "@/shared/lib";
import { Badge, BrandCharacter, Card } from "@/shared/ui";
import Link from "next/link";

interface Props {
  progress: JudgeProgress;
  /**
   * 아직 워커가 집어가지 않은 상태인지. 큐가 밀려 기다리는 것과 지금 채점 중인 것은
   * 사용자에게 다른 사실이다 — "채점 중" 으로 뭉뚱그리면 왜 느린지 알 수 없다 (#78).
   */
  pending?: boolean;
}

/** 채점 진행 상황. 테스트케이스가 하나씩 채워지는 과정을 그대로 보여준다. */
export function JudgeProgressPanel({ progress, pending = false }: Props) {
  if (!progress.submissionId) return null;

  const total = Math.max(progress.totalCount, progress.results.length);
  const done = progress.results.length;
  const percent = total === 0 ? 0 : Math.round((done / total) * 100);

  return (
    <Card className="space-y-4 p-4">
      {/*
        채점이 도는 동안에만 그림을 둔다 (#261). 끝나면 결과가 주인공이라 치운다.
        가로로 납작한 구도를 골랐다 — 정사각 그림을 넣으면 진행 막대가 아래로 밀린다.
      */}
      {progress.finished ? null : (
        <BrandCharacter name="working" size={260} className="mx-auto" />
      )}

      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-ink">
          {progress.finished
            ? "채점 완료"
            : pending && done === 0
              ? "채점 대기 중…"
              : `채점 중… (${done}/${total})`}
        </h3>
        {progress.finished && progress.verdict ? (
          <Badge tone={verdictTone(progress.verdict)}>
            {VERDICT_LABELS[progress.verdict]} · {progress.passedCount}/{progress.totalCount}
          </Badge>
        ) : (
          <span className="text-xs text-ink-muted">
            {pending && done === 0 ? "순서를 기다리는 중" : "실시간"}
          </span>
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
