"use client";

import { queueApi } from "@/entities/queue";
import type { QueueStatus } from "@/entities/queue";
import { RequireAuth } from "@/features/auth";
import { ExecutorScalePanel } from "@/features/executor-scale";
import { Badge, Card, EmptyState } from "@/shared/ui";
import { useEffect, useState } from "react";

/** 적체는 초 단위로 변하므로 주기적으로 다시 읽는다. */
const REFRESH_INTERVAL_MS = 2000;

const STREAM_LABELS: Record<string, string> = {
  "codekr:judge": "채점 큐",
  "codekr:exec": "실행 큐",
};

export function AdminQueuesPage() {
  return (
    <RequireAuth adminOnly>
      <QueueMonitor />
    </RequireAuth>
  );
}

function QueueMonitor() {
  const [status, setStatus] = useState<QueueStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = () =>
      queueApi
      .status()
        .then((next) => {
          setStatus(next);
          setError(null);
        })
        .catch(() => setError("큐 상태를 불러오지 못했습니다."));

    load();
    const timer = setInterval(load, REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  if (error) return <EmptyState title={error} />;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold text-ink">큐 모니터링</h1>
        <p className="mt-1 text-sm text-ink-muted">
          채점·실행 큐의 적체와 워커 수를 실시간으로 확인하고, 밀릴 때 실행기를 늘립니다.
        </p>
      </div>

      <ExecutorScalePanel />

      <div className="grid gap-3 sm:grid-cols-2">
        {status?.streams.map((stream) => (
          <Card key={stream.name} className="space-y-3 p-5">
            <div className="flex items-center gap-2">
              <h2 className="font-semibold text-ink">{STREAM_LABELS[stream.name] ?? stream.name}</h2>
              <Badge tone={stream.ready ? "ok" : "warn"}>{stream.ready ? "정상" : "워커 대기"}</Badge>
            </div>
            <dl className="grid grid-cols-3 gap-3 text-sm">
              <Metric label="누적 메시지" value={stream.length} />
              <Metric label="처리 대기" value={stream.pending} tone={stream.pending > 0 ? "warn" : undefined} />
              <Metric label="워커" value={stream.consumers} />
            </dl>
            <p className="text-xs text-ink-muted">
              그룹 {stream.group} · 마지막 전달 {stream.lastDeliveredId ?? "-"}
            </p>
          </Card>
        ))}
      </div>
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: number; tone?: "warn" }) {
  return (
    <div>
      <dt className="text-xs text-ink-muted">{label}</dt>
      <dd className={`mt-0.5 text-lg font-semibold ${tone === "warn" ? "text-warn" : "text-ink"}`}>
        {value}
      </dd>
    </div>
  );
}
