"use client";

import { problemApi } from "@/entities/problem";
import type { ProblemSolution, ProblemVerification, Runtime } from "@/entities/problem";
import { STATUS_LABELS, VERDICT_LABELS, verdictTone } from "@/entities/submission";
import { ApiError } from "@/shared/api";
import { formatMemory } from "@/shared/lib";
import { useEffect, useState } from "react";
import { Alert, Badge, Button, Card, Select, Textarea } from "@/shared/ui";

interface Props {
  problemId: number | null;
  solution: ProblemSolution | null;
  verification: ProblemVerification | null;
  onChange: (solution: ProblemSolution | null) => void;
}

/** 채점이 끝나지 않았으면 결과가 바뀌므로 짧게 다시 읽는다. */
const POLL_INTERVAL_MS = 1500;

/**
 * 정답 코드 편집과 전체 테스트케이스 검증.
 *
 * 검증은 사용자 제출과 같은 채점 큐를 쓰므로, 여기 보이는 결과는 실제 채점 결과와 같은 경로로 나온다.
 */
export function SolutionVerifier({ problemId, solution, verification, onChange }: Props) {
  const [runtimes, setRuntimes] = useState<Runtime[]>([]);
  const [latest, setLatest] = useState<ProblemVerification | null>(verification);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    problemApi.runtimes().then(setRuntimes).catch(() => setRuntimes([]));
  }, []);

  // 채점이 진행 중이면 끝날 때까지 결과를 다시 읽는다.
  useEffect(() => {
    if (!problemId || !latest) return;
    if (latest.status !== "PENDING" && latest.status !== "JUDGING") return;

    const timer = setTimeout(() => {
      problemApi
      .adminDetail(problemId)
        .then((problem) => setLatest(problem.verification))
        .catch(() => undefined);
    }, POLL_INTERVAL_MS);
    return () => clearTimeout(timer);
  }, [problemId, latest]);

  const enabled = Boolean(solution?.sourceCode?.trim());

  const update = (patch: Partial<ProblemSolution>) =>
    onChange({
      runtimeId: patch.runtimeId ?? solution?.runtimeId ?? runtimes[0]?.id ?? "",
      sourceCode: patch.sourceCode ?? solution?.sourceCode ?? "",
    });

  const verify = async () => {
    if (!problemId) return;
    setRunning(true);
    setError(null);
    try {
      setLatest(await problemApi.verify(problemId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "검증을 시작하지 못했습니다.");
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card className="space-y-4 p-5">
      <div className="flex flex-wrap items-center gap-2">
        <div>
          <h2 className="text-sm font-semibold text-ink">정답 코드 (선택)</h2>
          <p className="mt-0.5 text-xs text-ink-muted">
            등록하면 공개 전에 전체 테스트케이스를 검증할 수 있습니다. 사용자에게는 노출되지 않습니다.
          </p>
        </div>
        <Select
          className="ml-auto w-56"
          value={solution?.runtimeId ?? ""}
          onChange={(event) => update({ runtimeId: event.target.value })}
        >
          <option value="">실행 환경 선택</option>
          {runtimes.map((runtime) => (
            <option key={runtime.id} value={runtime.id}>
              {runtime.label}
            </option>
          ))}
        </Select>
        {enabled ? (
          <Button type="button" variant="danger" onClick={() => onChange(null)}>
            지우기
          </Button>
        ) : null}
      </div>

      <Textarea
        rows={10}
        placeholder="정답 코드를 붙여 넣으세요."
        value={solution?.sourceCode ?? ""}
        onChange={(event) => update({ sourceCode: event.target.value })}
      />

      {error ? <Alert>{error}</Alert> : null}

      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          onClick={verify}
          disabled={!problemId || !enabled || running}
        >
          {running ? "검증 시작 중…" : "전체 테스트케이스 검증"}
        </Button>
        {!problemId ? (
          <span className="text-xs text-ink-muted">문제를 먼저 저장하면 검증할 수 있습니다.</span>
        ) : null}
        {problemId && enabled ? (
          <span className="text-xs text-ink-muted">저장한 정답 코드로 검증합니다.</span>
        ) : null}
      </div>

      {latest ? <VerificationResult verification={latest} /> : null}
    </Card>
  );
}

function VerificationResult({ verification }: { verification: ProblemVerification }) {
  const finished = verification.status === "COMPLETED" || verification.status === "FAILED";

  return (
    <div className="space-y-3 rounded-lg border border-border p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium text-ink">검증 결과</span>
        {finished && verification.verdict ? (
          <Badge tone={verdictTone(verification.verdict)}>
            {VERDICT_LABELS[verification.verdict]} · {verification.passedCount}/{verification.totalCount}
          </Badge>
        ) : (
          <Badge>{STATUS_LABELS[verification.status]}</Badge>
        )}
        {verification.stale ? (
          <Badge tone="warn">문제가 바뀌어 결과가 낡았습니다 — 다시 검증하세요</Badge>
        ) : null}
      </div>

      {verification.compileError ? (
        <pre className="max-h-40 overflow-auto rounded-lg bg-surface-muted p-3 text-xs text-danger">
          {verification.compileError}
        </pre>
      ) : null}

      {verification.results.length > 0 ? (
        <ul className="space-y-1.5">
          {verification.results.map((result) => (
            <li
              key={result.seq}
              className="flex items-center gap-3 rounded-lg border border-border px-3 py-2 text-sm"
            >
              <span className="w-14 shrink-0 text-ink-muted">#{result.seq}</span>
              <Badge tone={verdictTone(result.verdict)}>{VERDICT_LABELS[result.verdict]}</Badge>
              <span className="ml-auto text-xs text-ink-muted">
                {result.runtimeMs}ms · {formatMemory(result.memoryKb)}
              </span>
            </li>
          ))}
        </ul>
      ) : null}

      {finished && verification.verdict !== "ACCEPTED" ? (
        <p className="text-xs text-warn">
          정답 코드가 통과하지 못했습니다. 기대 출력, 실행 환경, 시간·메모리 제한을 확인하세요.
        </p>
      ) : null}
    </div>
  );
}
