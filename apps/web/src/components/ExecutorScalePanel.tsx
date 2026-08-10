"use client";

import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card } from "./ui";
import { ApiError, api } from "@/lib/api";
import type { ExecutorScaleStatus } from "@/lib/types";

/** 배포 상태도 초 단위로 바뀌므로 큐 모니터링과 같은 주기로 갱신한다. */
const REFRESH_INTERVAL_MS = 3000;

/**
 * 실행기 수 조절 (#40).
 *
 * 큐가 밀리는 것을 같은 화면에서 보고 바로 대응할 수 있게 큐 모니터링 위에 둔다.
 */
export function ExecutorScalePanel() {
  const [status, setStatus] = useState<ExecutorScaleStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const load = useCallback(() => {
    api.executorScale().then(setStatus).catch(() => setStatus(null));
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [load]);

  const scale = async (replicas: number) => {
    setPending(true);
    setError(null);
    try {
      setStatus(await api.scaleExecutors(replicas));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "실행기 수를 바꾸지 못했습니다.");
    } finally {
      setPending(false);
    }
  };

  if (!status) return null;

  if (!status.available) {
    return (
      <Card className="p-5">
        <h2 className="text-sm font-semibold text-ink">실행기 수</h2>
        <p className="mt-1 text-sm text-ink-muted">
          {status.reason ?? "이 환경에서는 실행기 수를 조정할 수 없습니다."}
        </p>
      </Card>
    );
  }

  const canDecrease = status.desiredReplicas > status.minReplicas;
  const canIncrease = status.desiredReplicas < status.maxReplicas;

  return (
    <Card className="space-y-3 p-5">
      <div className="flex flex-wrap items-center gap-3">
        <div>
          <h2 className="text-sm font-semibold text-ink">실행기 수</h2>
          <p className="mt-0.5 text-xs text-ink-muted">
            준비됨 {status.readyReplicas} / 목표 {status.desiredReplicas}
            {" · "}허용 {status.minReplicas}~{status.maxReplicas}
          </p>
        </div>
        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="secondary"
            onClick={() => scale(status.desiredReplicas - 1)}
            disabled={pending || !canDecrease}
            aria-label="실행기 하나 줄이기"
          >
            −
          </Button>
          <span className="w-10 text-center text-lg font-semibold text-ink">
            {status.desiredReplicas}
          </span>
          <Button
            variant="secondary"
            onClick={() => scale(status.desiredReplicas + 1)}
            disabled={pending || !canIncrease}
            aria-label="실행기 하나 늘리기"
          >
            +
          </Button>
        </div>
      </div>

      {/* 준비된 수가 목표에 못 미치면 확장이 진행 중이라는 뜻이다. */}
      {status.readyReplicas < status.desiredReplicas ? (
        <p className="text-xs text-warn">실행기를 늘리는 중입니다…</p>
      ) : null}

      {error ? <Alert>{error}</Alert> : null}
    </Card>
  );
}
