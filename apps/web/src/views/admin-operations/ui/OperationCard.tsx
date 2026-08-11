"use client";

import { ApiError } from "@/shared/api";
import { Button, Card, Field, Input } from "@/shared/ui";
import { useState } from "react";

export interface Operation {
  key: string;
  label: string;
  description: string;
  /** 회원 ID 처럼 인자가 필요한 작업. 없으면 버튼만 있다. */
  argument?: { label: string; placeholder: string };
  /** 누르기 전에 받을 확인 문구. 없으면 바로 실행한다. */
  confirm?: string;
  run: (argument: number) => Promise<string>;
}

/**
 * 운영 작업 하나 (#180).
 *
 * **확인은 브라우저 `confirm` 이 아니라 화면 안에서 받는다.** 브라우저 대화상자는 화면
 * 밖의 것이라 무엇을 실행하려는지 다시 읽어 줄 수 없고, 자동화·테스트를 막는다.
 */
export function OperationCard({
  operation,
  onError,
}: {
  operation: Operation;
  onError: (message: string) => void;
}) {
  const [argument, setArgument] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  const needsArgument = operation.argument !== undefined;
  const parsed = Number(argument);
  const argumentValid = !needsArgument || (argument.trim() !== "" && Number.isInteger(parsed) && parsed > 0);

  const execute = async () => {
    setConfirming(false);
    setRunning(true);
    setResult(null);
    try {
      setResult(await operation.run(parsed));
    } catch (caught) {
      onError(caught instanceof ApiError ? caught.message : `${operation.label}에 실패했습니다.`);
    } finally {
      setRunning(false);
    }
  };

  return (
    <Card className="flex h-full flex-col gap-3 p-5">
      <div>
        <p className="font-medium text-ink">{operation.label}</p>
        <p className="mt-1 text-xs leading-relaxed text-ink-muted">{operation.description}</p>
      </div>

      {operation.argument ? (
        <Field label={operation.argument.label}>
          <Input
            value={argument}
            inputMode="numeric"
            placeholder={operation.argument.placeholder}
            onChange={(event) => {
              setArgument(event.target.value);
              setConfirming(false);
            }}
          />
        </Field>
      ) : null}

      <div className="mt-auto space-y-2">
        {confirming ? (
          <div className="space-y-2 rounded-lg border border-warn/40 bg-warn/10 p-3">
            <p className="text-xs text-ink">{operation.confirm}</p>
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
          <Button
            onClick={() => (operation.confirm ? setConfirming(true) : execute())}
            disabled={running || !argumentValid}
          >
            {running ? "실행 중…" : "실행"}
          </Button>
        )}

        {/* 결과를 화면에 남긴다 — 토스트는 사라져서 "돌긴 돌았나" 를 확인할 수 없다. */}
        {result ? <p className="text-xs text-ok">{result}</p> : null}
      </div>
    </Card>
  );
}
