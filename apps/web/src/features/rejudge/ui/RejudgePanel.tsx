"use client";

import { ProblemPicker } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { rejudgeApi } from "@/entities/rejudge";
import type { RejudgeStatus } from "@/entities/rejudge";
import { ApiError } from "@/shared/api";
import { Button, Card, Field, Input } from "@/shared/ui";
import { useEffect, useState } from "react";
import { RejudgeStatusView } from "./RejudgeStatusView";

/** 이유 길이 상한. 서버의 `@Size(max = 200)` 과 같아야 한다 — 여기서 막지 않으면 400 으로 돌아온다. */
const REASON_MAX = 200;

/** 진행 중일 때 상태를 다시 읽는 간격. 재채점은 낮은 등급으로 돌아(#102) 금방 끝나지 않는다. */
const POLL_MS = 3000;

/**
 * 문제 단위 재채점 (#219).
 *
 * **API 만 있고 화면이 없으면 있는 줄도 모른다** (#180). 게다가 재채점은 대상이었던
 * 모든 회원에게 알림을 보내는(#187) 되돌릴 수 없는 동작인데, 그동안 `curl` 로만
 * 부를 수 있었고 확인 절차도 없었다.
 *
 * 그래서 순서를 강제한다: **문제를 고르고 → 대상 수를 보고 → 이유를 적고 → 확인한다.**
 */
export function RejudgePanel({ onError }: { onError: (message: string) => void }) {
  const [problem, setProblem] = useState<ProblemSummary | null>(null);
  const [status, setStatus] = useState<RejudgeStatus | null>(null);
  const [reason, setReason] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [running, setRunning] = useState(false);

  const problemId = problem?.id ?? null;
  const batchRunning = status?.latest != null && !status.latest.finished;

  // 문제를 고르면 상태를 읽고, 진행 중이면 끝날 때까지 다시 읽는다.
  useEffect(() => {
    if (problemId === null) return;
    let alive = true;

    const load = () => {
      rejudgeApi
        .status(problemId)
        .then((next) => alive && setStatus(next))
        .catch(() => alive && setStatus(null));
    };

    load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [problemId]);

  const pick = (picked: ProblemSummary) => {
    setProblem(picked);
    setStatus(null);
    setConfirming(false);
  };

  const ready =
    problemId !== null &&
    status !== null &&
    status.targetCount > 0 &&
    !batchRunning &&
    reason.trim().length > 0;

  const execute = async () => {
    if (problemId === null) return;
    setConfirming(false);
    setRunning(true);
    try {
      const batch = await rejudgeApi.run(problemId, reason.trim());
      setStatus((previous) => (previous === null ? previous : { ...previous, latest: batch }));
      setReason("");
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : "재채점을 시작하지 못했습니다.");
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card className="flex h-full flex-col gap-3 p-5">
      <div>
        <p className="font-medium text-ink">문제 재채점</p>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">
          문제의 사용자 제출을 모두 다시 채점합니다. 끝나면 대상이었던 회원에게 결과 알림이
          갑니다 — 되돌릴 수 없습니다. 정답 검증 제출은 대상이 아닙니다.
        </p>
      </div>

      {/* 문제 ID 를 외워 넣게 하지 않는다. 문제집(#87)이 쓰는 것과 같은 자동완성이다. */}
      <ProblemPicker
        pickedIds={problemId === null ? [] : [problemId]}
        onPick={pick}
        labels={{ idle: "고르기", picked: "고름" }}
      />

      {problem ? (
        <div className="space-y-3">
          <p className="text-sm text-ink">
            고른 문제: <span className="font-medium">{problem.title}</span>
          </p>

          {status ? <RejudgeStatusView status={status} /> : <p className="text-xs text-ink-muted">상태를 읽는 중…</p>}

          <Field label={`재채점 이유 (필수 · 알림에 그대로 들어갑니다)`}>
            <Input
              value={reason}
              maxLength={REASON_MAX}
              placeholder="예: 테스트케이스 3번의 기댓값이 잘못돼 있었습니다."
              onChange={(event) => {
                setReason(event.target.value);
                setConfirming(false);
              }}
            />
          </Field>
        </div>
      ) : null}

      <div className="mt-auto space-y-2">
        {confirming && status ? (
          <div className="space-y-2 rounded-lg border border-warn/40 bg-warn/10 p-3">
            <p className="text-xs leading-relaxed text-ink">
              <strong className="font-medium">{status.targetCount.toLocaleString("ko-KR")}건</strong>
              을 다시 채점하고, 대상이었던 회원 모두에게 결과 알림을 보냅니다. 판정이 나빠지는
              방향으로도 바뀝니다. 진행할까요?
            </p>
            <div className="flex gap-2">
              <Button onClick={execute} disabled={running}>
                실행
              </Button>
              <Button variant="ghost" onClick={() => setConfirming(false)}>
                취소
              </Button>
            </div>
          </div>
        ) : (
          <Button onClick={() => setConfirming(true)} disabled={!ready || running}>
            {running ? "시작하는 중…" : "재채점"}
          </Button>
        )}
      </div>
    </Card>
  );
}
