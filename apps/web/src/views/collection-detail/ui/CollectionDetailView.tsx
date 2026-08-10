"use client";

import { TierBadge } from "@/entities/problem";
import type { CollectionDetail } from "@/entities/collection";
import { Badge, Button, Card } from "@/shared/ui";
import Link from "next/link";

/** 문제집 상세 (#87). 링크 공유로 열든 내 것으로 열든 같은 화면이다. */
export function CollectionDetailView({
  detail,
  onCopyLink,
}: {
  detail: CollectionDetail;
  onCopyLink?: () => void;
}) {
  const { summary } = detail;
  const progress = summary.problemCount === 0 ? 0 : (summary.solvedCount / summary.problemCount) * 100;

  return (
    <div className="space-y-5">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold text-ink">{summary.name}</h1>
          <Badge tone={summary.visibility === "PRIVATE" ? "muted" : "info"}>
            {summary.visibilityLabel}
          </Badge>
        </div>
        <p className="text-xs text-ink-muted">{summary.ownerNickname} 님의 문제집</p>
        {summary.description ? (
          <p className="whitespace-pre-wrap text-sm text-ink">{summary.description}</p>
        ) : null}
      </header>

      <Card className="space-y-2 p-5">
        <p className="text-xs text-ink-muted">
          <span className="font-semibold text-ink">
            {summary.solvedCount} / {summary.problemCount}
          </span>{" "}
          문제를 풀었습니다
        </p>
        <div className="h-1.5 overflow-hidden rounded-full bg-line">
          <div className="h-full rounded-full bg-brand" style={{ width: `${progress}%` }} />
        </div>
      </Card>

      {detail.editable ? (
        <div className="flex flex-wrap gap-2">
          <Link href={`/collections/${summary.id}/edit`}>
            <Button variant="secondary">수정</Button>
          </Link>
          {/* 공유 토큰은 주인에게만 내려온다. 비공개면 링크가 없다. */}
          {summary.shareToken && summary.visibility !== "PRIVATE" ? (
            <Button variant="secondary" onClick={onCopyLink}>
              공유 링크 복사
            </Button>
          ) : null}
        </div>
      ) : null}

      <Card className="divide-y divide-border p-0">
        {detail.problems.map((problem, index) => (
          <Link
            key={problem.slug}
            href={`/problems/${encodeURIComponent(problem.slug)}`}
            className="flex items-center gap-3 px-5 py-3 transition hover:bg-surface-muted"
          >
            <span className="w-6 text-xs tabular-nums text-ink-muted">{index + 1}</span>
            <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
            <span className="min-w-0 flex-1 truncate text-ink">{problem.title}</span>
            {problem.solved ? <span className="text-xs text-ok">✔ 풀었음</span> : null}
          </Link>
        ))}
      </Card>
    </div>
  );
}
