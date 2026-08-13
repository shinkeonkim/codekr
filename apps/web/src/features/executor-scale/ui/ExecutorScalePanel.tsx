"use client";

import { executorScaleApi } from "../api";
import type { ExecutorScaleStatus } from "../model/types";
import { ApiError } from "@/shared/api";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, CardTitle } from "@/shared/ui";

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
    executorScaleApi.status().then(setStatus).catch(() => setStatus(null));
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
      setStatus(await executorScaleApi.scale(replicas));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "실행기 수를 바꾸지 못했습니다.");
    } finally {
      setPending(false);
    }
  };

  if (!status) return null;

  // 클러스터 밖이면 조정할 대상 자체가 없다. 고장이 아니라 설정이므로 조용히 안내만 한다.
  if (status.state === "OUTSIDE_CLUSTER") {
    return (
      <Card className="p-5">
        <CardTitle>실행기 수</CardTitle>
        <p className="mt-1 text-sm text-ink-muted">
          {status.reason ?? "이 환경에서는 실행기 수를 조정할 수 없습니다."}
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
          <CardTitle>실행기 수</CardTitle>
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
            onClick={() => scale(status.desiredReplicas - 1)}
            disabled={pending || !canDecrease}
            aria-label="실행기 하나 줄이기"
          >
            −
          </Button>
          <span className="w-10 text-center text-lg font-semibold text-ink">
            {unreadable ? "?" : status.desiredReplicas}
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

      {/*
        읽기 실패는 **고장이다.** 왜 실패했는지를 그대로 보여준다 — 원인을 모르면
        어드민이 계속 새로 고치기만 한다.
      */}
      {unreadable ? <Alert>{status.reason ?? "실행기 배포 상태를 읽지 못했습니다."}</Alert> : null}

      {/* 준비된 수가 목표에 못 미치면 확장이 진행 중이라는 뜻이다. */}
      {!unreadable && status.readyReplicas < status.desiredReplicas ? (
        <p className="text-xs text-warn">실행기를 늘리는 중입니다…</p>
      ) : null}

      {error ? <Alert>{error}</Alert> : null}
    </Card>
  );
}
