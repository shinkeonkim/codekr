"use client";

import { badgeApi } from "@/entities/badge";
import type { BadgeDryRun, BadgeVocabulary } from "@/entities/badge";
import { Alert, Button, Card, CardTitle, Field, Input, Select } from "@/shared/ui";
import { useEffect, useState } from "react";

/**
 * 저장 전에 결과를 본다 (#203).
 *
 * **규칙은 사용자에게 보이는 것을 바꾼다.** 저장한 뒤에야 결과를 알면 되돌릴 방법이
 * 뱃지 회수뿐인데, 그것은 하지 않기로 했다 (#41).
 *
 * 이벤트·지표 목록은 **서버가 내려준다** — #200 에서 이벤트가 늘 때마다 이 화면을
 * 고치는 구조를 만들지 않는다.
 */
export function RuleDryRun() {
  const [vocabulary, setVocabulary] = useState<BadgeVocabulary | null>(null);
  const [event, setEvent] = useState("PROBLEM_ACCEPTED");
  const [measure, setMeasure] = useState("accepted_problem_count");
  const [op, setOp] = useState(">=");
  const [value, setValue] = useState("10");
  const [userId, setUserId] = useState("");
  const [result, setResult] = useState<BadgeDryRun | null>(null);

  useEffect(() => {
    badgeApi
      .vocabulary()
      .then(setVocabulary)
      .catch(() => setVocabulary(null));
  }, []);

  if (!vocabulary) return null;

  // 이벤트 지표는 그 이벤트에서만 뜻이 있다 (#200 §4.1) — 고를 수 있는 것만 보인다.
  const usable = vocabulary.measures.filter((each) => each.events.includes(event));

  const run = async () => {
    const parsed = value === "true" || value === "false" ? value === "true" : Number(value);
    setResult(
      await badgeApi.dryRun(
        {
          ruleKey: "__preview",
          event,
          code: "__PREVIEW",
          conditions: [{ measure, op, value: parsed }],
        },
        userId.trim() ? Number(userId.trim()) : undefined,
      ),
    );
  };

  return (
    <Card className="space-y-3 p-5">
      <div>
        <CardTitle>규칙 미리보기</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">저장하지 않고 지금 이 조건이면 누가 받는지 봅니다.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <Field label="이벤트">
          <Select value={event} onChange={(e) => setEvent(e.target.value)}>
            {vocabulary.events.map((each) => (
              <option key={each} value={each}>
                {each}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="지표">
          <Select value={measure} onChange={(e) => setMeasure(e.target.value)}>
            {usable.map((each) => (
              <option key={each.name} value={each.name}>
                {each.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="비교">
          <Select value={op} onChange={(e) => setOp(e.target.value)}>
            {vocabulary.operators.map((each) => (
              <option key={each} value={each}>
                {each}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="값">
          <Input value={value} onChange={(e) => setValue(e.target.value)} />
        </Field>
        <Field label="확인할 회원 ID (선택)">
          <Input inputMode="numeric" value={userId} onChange={(e) => setUserId(e.target.value)} />
        </Field>
      </div>

      <Button onClick={run}>미리보기</Button>

      {result ? (
        result.valid ? (
          <Alert tone="ok">
            표본 {result.sampled}명 중 <strong>{result.matched}명</strong>이 받습니다.
            {result.matchesUser !== null
              ? ` 지정한 회원은 ${result.matchesUser ? "받습니다" : "받지 못합니다"}.`
              : ""}
          </Alert>
        ) : (
          // **틀린 자리를 짚어 준다** — "잘못된 규칙입니다" 로는 고칠 수 없다.
          <Alert tone="danger">
            <ul className="list-disc space-y-0.5 pl-4">
              {result.errors.map((error) => (
                <li key={error}>{error}</li>
              ))}
            </ul>
          </Alert>
        )
      ) : null}
    </Card>
  );
}
