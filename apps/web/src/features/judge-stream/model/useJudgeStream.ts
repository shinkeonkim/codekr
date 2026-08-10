"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { submissionApi } from "@/entities/submission";
import { tokenStore } from "@/shared/api";
import { WS_BASE_URL } from "@/shared/config";
import type { SubmissionDetail, TestcaseResult, Verdict } from "@/entities/submission";
import type { JudgeEvent } from "./types";

export interface JudgeProgress {
  submissionId: number | null;
  totalCount: number;
  results: TestcaseResult[];
  verdict: Verdict | null;
  passedCount: number;
  compileError: string | null;
  finished: boolean;
}

const EMPTY: JudgeProgress = {
  submissionId: null,
  totalCount: 0,
  results: [],
  verdict: null,
  passedCount: 0,
  compileError: null,
  finished: false,
};

/**
 * 제출 하나의 채점 진행을 WebSocket 으로 따라간다.
 *
 * WebSocket 이 막힌 환경이나 이벤트 유실에 대비해, 채점이 끝나지 않은 채로 조용해지면
 * REST 로 한 번 더 확인한다 — 화면이 영원히 "채점 중"에 머무는 일을 막는다.
 */
export function useJudgeStream(): {
  progress: JudgeProgress;
  watch: (submissionId: number, totalCount: number) => void;
  reset: () => void;
} {
  const [progress, setProgress] = useState<JudgeProgress>(EMPTY);
  const socketRef = useRef<WebSocket | null>(null);
  const fallbackRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cleanup = useCallback(() => {
    socketRef.current?.close();
    socketRef.current = null;
    if (fallbackRef.current) clearTimeout(fallbackRef.current);
  }, []);

  useEffect(() => cleanup, [cleanup]);

  const reset = useCallback(() => {
    cleanup();
    setProgress(EMPTY);
  }, [cleanup]);

  const watch = useCallback(
    (submissionId: number, totalCount: number) => {
      cleanup();
      setProgress({ ...EMPTY, submissionId, totalCount });

      const token = tokenStore.read();
      if (!token) return;

      const socket = new WebSocket(`${WS_BASE_URL}/ws/submissions`);
      socketRef.current = socket;

      socket.onopen = () => socket.send(JSON.stringify({ type: "SUBSCRIBE", submissionId, token }));
      socket.onmessage = (message) => {
        const event = JSON.parse(message.data as string) as JudgeEvent;
        setProgress((previous) => applyEvent(previous, event));
      };

      // 이벤트가 끊겨도 결과를 확인할 수 있도록 마지막 안전망을 건다.
      fallbackRef.current = setTimeout(() => {
        void submissionApi
      .detail(submissionId)
          .then((detail) => setProgress((previous) => (previous.finished ? previous : fromDetail(detail))))
          .catch(() => undefined);
      }, FALLBACK_DELAY_MS);
    },
    [cleanup],
  );

  return { progress, watch, reset };
}

const FALLBACK_DELAY_MS = 20_000;

function applyEvent(previous: JudgeProgress, event: JudgeEvent): JudgeProgress {
  switch (event.type) {
    case "JUDGING":
      return { ...previous, totalCount: event.totalCount ?? previous.totalCount };
    case "TESTCASE":
      return {
        ...previous,
        results: upsertResult(previous.results, {
          seq: event.seq ?? 0,
          verdict: event.verdict ?? "SYSTEM_ERROR",
          runtimeMs: event.runtimeMs ?? 0,
          memoryKb: event.memoryKb ?? 0,
          stderrExcerpt: event.stderrExcerpt ?? null,
        }),
      };
    case "COMPLETED":
      return {
        ...previous,
        verdict: event.verdict ?? null,
        passedCount: event.passedCount ?? 0,
        totalCount: event.totalCount ?? previous.totalCount,
        compileError: event.compileError ?? null,
        finished: true,
      };
    default:
      return previous;
  }
}

function upsertResult(results: TestcaseResult[], incoming: TestcaseResult): TestcaseResult[] {
  const next = results.filter((result) => result.seq !== incoming.seq);
  next.push(incoming);
  return next.sort((a, b) => a.seq - b.seq);
}

function fromDetail(detail: SubmissionDetail): JudgeProgress {
  return {
    submissionId: detail.id,
    totalCount: detail.totalCount,
    results: detail.results,
    verdict: detail.verdict,
    passedCount: detail.passedCount,
    compileError: detail.compileError,
    finished: detail.status === "COMPLETED" || detail.status === "FAILED",
  };
}
