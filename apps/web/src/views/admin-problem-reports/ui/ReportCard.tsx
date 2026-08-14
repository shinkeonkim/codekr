"use client";

import type { ProblemReport, ReportStatus } from "@/entities/problem-report";
import { formatDateTime } from "@/shared/lib";
import { Badge, Button, Card, Input } from "@/shared/ui";
import Link from "next/link";
import { useState } from "react";

/**
 * 신고 한 건 (#548).
 *
 * **`openCount` 를 크게 보인다.** 열 명이 같은 것을 말하면 그만큼 급하다는 뜻이고,
 * 어드민이 무엇부터 볼지 정하는 값이다 — 서버가 그 이유로 이 값을 내려 준다.
 */
export function ReportCard({
  report,
  onResolve,
}: {
  report: ProblemReport;
  onResolve: (id: number, status: ReportStatus, resolution: string) => void;
}) {
  const [resolution, setResolution] = useState("");
  const open = report.status === "OPEN";

  return (
    <Card className="space-y-2 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={open ? "warn" : "muted"}>{report.statusLabel}</Badge>
        <Badge tone="muted">{report.kindLabel}</Badge>
        <Link href={`/admin/problems/${report.problemId}/edit`} className="text-sm text-ink hover:underline">
          문제 #{report.problemId}
        </Link>
        {/* 같은 문제에 신고가 몰리면 그것부터 봐야 한다. */}
        {report.openCount > 1 ? (
          <span className="text-xs text-danger">이 문제에 열린 신고 {report.openCount}건</span>
        ) : null}
        <span className="ml-auto text-xs text-ink-muted">{formatDateTime(report.createdAt)}</span>
      </div>

      <p className="whitespace-pre-wrap break-words text-sm text-ink">{report.body}</p>

      {report.resolution ? (
        <p className="border-t border-border pt-2 text-xs text-ink-muted">
          처리 내용: <span className="text-ink">{report.resolution}</span>
        </p>
      ) : null}

      {open ? (
        <div className="space-y-2 border-t border-border pt-2">
          {/*
            **기각할 때 설명이 중요하다.** "문제 없음" 만 보이면 신고한 사람은 왜인지
            모른다. 고쳤을 때도 무엇을 고쳤는지 적으면 같은 신고가 또 오지 않는다.
          */}
          <Input
            value={resolution}
            placeholder="처리 내용 (신고한 사람에게 남습니다)"
            onChange={(event) => setResolution(event.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            <Button className="px-3 py-1 text-xs" onClick={() => onResolve(report.id, "ACCEPTED", resolution)}>
              고쳤음
            </Button>
            <Button
              variant="secondary"
              className="px-3 py-1 text-xs"
              onClick={() => onResolve(report.id, "REJECTED", resolution)}
            >
              문제 없음
            </Button>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
