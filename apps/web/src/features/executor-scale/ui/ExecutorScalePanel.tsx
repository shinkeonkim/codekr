"use client";

import { executorScaleApi } from "../api";
import type { ExecutorScaleStatus } from "../model/types";
import { ApiError } from "@/shared/api";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, CardTitle } from "@/shared/ui";

/** 배포 상태도 초 단위로 바뀌므로 큐 모니터링과 같은 주기로 갱신한다. */
const REFRESH_INTERVAL_MS = 3000;

/**
 * 채점 파이프라인 조정 (#40, #390).
 *
 * 큐가 밀리는 것을 같은 화면에서 보고 바로 대응할 수 있게 큐 모니터링 위에 둔다.
 *
 * **무엇을 늘려야 하는지는 어디서 막혔느냐에 따라 다르다** (#390). 실행이 느리면
 * 실행기를, **채점기가 큐를 못 빼면 채점기를** 늘린다 — 그때 실행기를 늘려도 소용없다.
 * 그리고 대회 중에는 대회 채점기만 늘려야 한다 (#62 가 큐를 나눈 이유다).
 */
export function ExecutorScalePanel() {
  const [statuses, setStatuses] = useState<ExecutorScaleStatus[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const load = useCallback(() => {
    executorScaleApi.statuses().then(setStatuses).catch(() => setStatuses(null));
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [load]);

  const apply = async (run: () => Promise<ExecutorScaleStatus>) => {
    setPending(true);
    setError(null);
    try {
      const next = await run();
      setStatuses((current) => (current ?? []).map((each) => (each.key === next.key ? next : each)));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "바꾸지 못했습니다.");
    } finally {
      setPending(false);
    }
  };

  if (!statuses || statuses.length === 0) return null;

  return (
    <div className="space-y-3">
      {statuses.map((status) => (
        <WorkloadCard
          key={status.key}
          status={status}
          pending={pending}
          onScale={(replicas) => apply(() => executorScaleApi.scale(status.key, replicas))}
          onWorkers={(workers) => apply(() => executorScaleApi.setWorkers(status.key, workers))}
        />
      ))}
      {error ? <Alert>{error}</Alert> : null}
    </div>
  );
}

function WorkloadCard({
  status,
  pending,
  onScale,
  onWorkers,
}: {
  status: ExecutorScaleStatus;
  pending: boolean;
  onScale: (replicas: number) => void;
  onWorkers: (workers: number) => void;
}) {

  // 클러스터 밖이면 조정할 대상 자체가 없다. 고장이 아니라 설정이므로 조용히 안내만 한다.
  if (status.state === "OUTSIDE_CLUSTER") {
    return (
      <Card className="p-5">
        <CardTitle>{status.label}</CardTitle>
        <p className="mt-1 text-sm text-ink-muted">
          {status.reason ?? "이 환경에서는 조정할 수 없습니다."}
        </p>
      </Card>
    );
  }

  const unreadable = status.state === "UNREADABLE";
  const canDecrease = !unreadable && status.desiredReplicas > status.minReplicas;
  const canIncrease = !unreadable && status.desiredReplicas < status.maxReplicas;

  return (
    <Card className="space-y-3 p-5">
      <div className="flex flex-wrap items-center gap-3">
        <div>
          <CardTitle>{status.label}</CardTitle>
          <p className="mt-0.5 text-xs text-ink-muted">
            {unreadable ? "현재 수를 읽지 못했습니다" : `준비됨 ${status.readyReplicas} / 목표 ${status.desiredReplicas}`}
            {" · "}허용 {status.minReplicas}~{status.maxReplicas}
          </p>
          {/*
            어디를 보고 있는지 적는다 (#237). "그 배포가 없다" 는 말은 어느 네임스페이스에서
            없다는 것인지 알아야 고칠 수 있다.
          */}
          <p className="mt-0.5 text-[11px] text-ink-muted">
            {status.namespace ? `${status.namespace}/${status.deployment}` : status.deployment}
          </p>
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="secondary"
            onClick={() => onScale(status.desiredReplicas - 1)}
            disabled={pending || !canDecrease}
            aria-label={`${status.label} 파드 하나 줄이기`}
          >
            −
          </Button>
          <span className="w-10 text-center text-lg font-semibold text-ink">
            {unreadable ? "?" : status.desiredReplicas}
          </span>
          <Button
            variant="secondary"
            onClick={() => onScale(status.desiredReplicas + 1)}
            disabled={pending || !canIncrease}
            aria-label={`${status.label} 파드 하나 늘리기`}
          >
            +
          </Button>
        </div>
      </div>

      {/*
        읽기 실패는 **고장이다.** 왜 실패했는지를 그대로 보여준다 — 원인을 모르면
        어드민이 계속 새로 고치기만 한다.
      */}
      {unreadable ? <Alert>{status.reason ?? "배포 상태를 읽지 못했습니다."}</Alert> : null}

      {/* 준비된 수가 목표에 못 미치면 확장이 진행 중이라는 뜻이다. */}
      {!unreadable && status.readyReplicas < status.desiredReplicas ? (
        <p className="text-xs text-warn">파드를 늘리는 중입니다…</p>
      ) : null}

      {/*
        **워커 수는 채점기에만 있다** (#390). 파드 수와 듣는 곳이 다르다 —
        워커는 대기 시간에, 파드는 처리량과 격리에 듣는다. 화면이 그것을 말하지 않으면
        운영자는 무엇을 눌러야 할지 모른다.
      */}
      {status.workers !== undefined && status.workers !== null ? (
        <WorkerRow workers={status.workers} pending={pending} onChange={onWorkers} />
      ) : status.key.startsWith("judge") ? (
        <WorkerRow workers={null} pending={pending} onChange={onWorkers} />
      ) : null}
    </Card>
  );
}

/**
 * 한 채점기가 동시에 몇 건을 처리하는가 (#390).
 *
 * **재시작하지 않는다.** 전에는 `JUDGE_CONCURRENCY` 를 바꾸려면 배포를 다시 해야 했는데,
 * 늘리려는 상황이 곧 재시작하면 안 되는 상황이다 — 진행 중인 채점이 끊긴다.
 */
function WorkerRow({
  workers,
  pending,
  onChange,
}: {
  workers: number | null;
  pending: boolean;
  onChange: (workers: number) => void;
}) {
  const current = workers ?? 4;
  return (
    <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
      <div>
        <p className="text-sm font-medium text-ink">워커 수</p>
        <p className="text-xs text-ink-muted">
          한 채점기가 동시에 처리하는 건수 — <strong>대기 시간</strong>에 듣는다.
          {workers === null ? " 아직 정한 적이 없어 기동값으로 돕니다." : ""}
        </p>
      </div>
      <div className="ml-auto flex items-center gap-2">
        <Button variant="secondary" onClick={() => onChange(current - 1)} disabled={pending || current <= 1}>
          −
        </Button>
        <span className="w-10 text-center text-lg font-semibold text-ink">{workers ?? "—"}</span>
        <Button variant="secondary" onClick={() => onChange(current + 1)} disabled={pending}>
          +
        </Button>
      </div>
    </div>
  );
}
