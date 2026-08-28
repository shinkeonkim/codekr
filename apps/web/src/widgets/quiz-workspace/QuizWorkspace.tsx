"use client";

import { useState } from "react";
import type { ProblemDetail, QuizResult } from "@/entities/problem";
import { problemApi } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, CheckboxField, Input, Markdown } from "@/shared/ui";
import { useToast } from "@/shared/ui";
import { isAnswerEmpty, toggleChoice } from "./selection";

/**
 * 퀴즈를 푸는 화면 (#650).
 *
 * **`SolveWorkspace` 와 나눈다.** 저쪽은 에디터·언어 선택·실행·소켓으로 판정 기다리기가
 * 한 덩어리인데, 여기에는 그중 무엇도 없다 — 답을 고르면 **그 자리에서 채점이 끝난다.**
 * 한 컴포넌트에 담으면 유형에 따라 절반이 죽어 있는 화면이 된다.
 */
export function QuizWorkspace({ problem }: { problem: ProblemDetail }) {
  const quiz = problem.quiz;
  const [selected, setSelected] = useState<number[]>([]);
  const [text, setText] = useState("");
  const [result, setResult] = useState<QuizResult | null>(null);
  const [sending, setSending] = useState(false);
  const toast = useToast();

  if (!quiz) {
    // 유형은 퀴즈인데 보기가 없다 — 출제자가 덜 채운 것이다.
    return <Alert tone="warn">아직 준비되지 않은 문제입니다.</Alert>;
  }

  const single = quiz.answerType === "SINGLE";
  const short = quiz.answerType === "SHORT";

  const toggle = (seq: number) =>
    setSelected((previous) => toggleChoice(previous, seq, single));

  const submit = async () => {
    setSending(true);
    try {
      setResult(await problemApi.submitQuiz(problem.slug, { selected, text: short ? text : null }));
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : "제출하지 못했습니다.");
    } finally {
      setSending(false);
    }
  };

  const empty = isAnswerEmpty(selected, text, short);

  return (
    <Card className="space-y-4 p-5">
      <p className="text-xs text-ink-muted">{quiz.answerTypeLabel}</p>

      {short ? (
        <Input
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="답을 적어 주세요"
          aria-label="답"
        />
      ) : (
        <div className="space-y-2">
          {quiz.choices.map((choice) => (
            <CheckboxField
              key={choice.seq}
              label={choice.content}
              checked={selected.includes(choice.seq)}
              onCheckedChange={() => toggle(choice.seq)}
            />
          ))}
        </div>
      )}

      <div className="flex items-center gap-3">
        <Button onClick={submit} disabled={sending || empty}>
          {sending ? "채점 중…" : "제출"}
        </Button>
        {!single && !short ? (
          <span className="text-xs text-ink-muted">맞는 것을 모두 고르세요.</span>
        ) : null}
      </div>

      {result ? <QuizResultView result={result} /> : null}
    </Card>
  );
}

/**
 * 채점 결과.
 *
 * **해설은 맞았을 때도 틀렸을 때도 보여 준다.** 틀린 사람에게 더 필요하지만, 맞힌
 * 사람도 **왜 맞았는지**를 확인해야 찍어서 맞은 것과 구별된다.
 */
function QuizResultView({ result }: { result: QuizResult }) {
  return (
    <div className="space-y-3">
      <Alert tone={result.correct ? "ok" : "warn"}>
        {result.correct ? "맞았습니다." : "틀렸습니다."}
      </Alert>
      {result.explanation ? (
        <div className="rounded-card border border-border bg-surface-muted/30 p-4">
          <p className="mb-2 text-xs font-medium text-ink-muted">해설</p>
          <Markdown source={result.explanation} />
        </div>
      ) : null}
    </div>
  );
}
