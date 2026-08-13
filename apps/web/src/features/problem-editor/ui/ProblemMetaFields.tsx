"use client";

import {
  ALL_DIFFICULTIES,
  CATEGORY_LABELS,
  OUTPUT_COMPARISON_LABELS,
  SELECTABLE_KINDS,
  difficultyLabel,
} from "@/entities/problem";
import type { Difficulty, DifficultyState, OutputComparison } from "@/entities/problem";
import { Field, Input, Select } from "@/shared/ui";
import type { ProblemFormValues } from "../model/values";

/**
 * 유형과 무관한 공통 필드 (#127).
 *
 * slug·제목·분야·채점 방식·난이도·실행 제한. **유형이 늘어도 이 파일은 그대로다.**
 */
export function ProblemMetaFields({
  values,
  onChange,
  onChangeKind,
}: {
  values: ProblemFormValues;
  onChange: <K extends keyof ProblemFormValues>(key: K, value: ProblemFormValues[K]) => void;
  onChangeKind: (kind: string) => void;
}) {
  const update = onChange;
  const changeKind = onChangeKind;

  return (
    <div className="grid gap-4 sm:grid-cols-2">
        <Field label="slug (URL 식별자)">
          <Input
            value={values.slug}
            onChange={(event) => update("slug", event.target.value)}
            placeholder="two-sum"
            required
          />
        </Field>
        <Field label="제목">
          <Input value={values.title} onChange={(event) => update("title", event.target.value)} required />
        </Field>
        {/* '유형'이라 부르면 채점 방식과 헷갈린다. 무엇에 대한 문제인지는 '분야'다. */}
        <Field label="분야">
          <Select value={values.category} onChange={(event) => update("category", event.target.value)}>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        {/*
          채점 방식 (#59). 지금 고를 수 있는 것은 하나뿐이지만 자리를 만들어 둔다 —
          유형별 폼은 이 값에 따라 아래 입력 묶음을 갈아 끼우는 방식이 된다.
        */}
        <Field label="채점 방식">
          <Select
            value={values.problemKind}
            onChange={(event) => changeKind(event.target.value)}
          >
            {Object.entries(SELECTABLE_KINDS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="난이도">
          <Select
            // 빈 값이면 미평가다 (#195). 난이도를 고르면 상태도 함께 따라간다.
            value={values.difficulty ?? ""}
            onChange={(event) => {
              const picked = event.target.value;
              update("difficulty", (picked || null) as Difficulty | null);
              update("difficultyState", picked ? "RATED" : "UNRATED");
            }}
          >
            {/*
              **비워 두는 것이 기본이다** (#195). 실제 난이도는 사람들이 풀어 봐야 아는
              값이고, 등록 시점에 아무 값이나 박아 넣으면 그 숫자가 곧바로 점수가 된다.
            */}
            <option value="">미평가 (아직 정하지 않음)</option>
            {ALL_DIFFICULTIES.map((value) => (
              <option key={value} value={value}>
                {difficultyLabel(value)}
              </option>
            ))}
          </Select>
          {/* 영영 점수를 주지 않는 문제는 따로 고른다 — 튜토리얼·설문형 (#195). */}
          {values.difficulty === null ? (
            <Select
              aria-label="난이도 상태"
              value={values.difficultyState}
              onChange={(event) => update("difficultyState", event.target.value as DifficultyState)}
            >
              <option value="UNRATED">미평가 — 나중에 난이도를 매긴다</option>
              <option value="NO_RATE">평가 안 함 — 영영 점수를 주지 않는다</option>
            </Select>
          ) : null}
        </Field>
        <Field label="시간 제한 (ms)">
          <Input
            type="number"
            value={values.timeLimitMs}
            onChange={(event) => update("timeLimitMs", Number(event.target.value))}
          />
        </Field>
        <Field label="메모리 제한 (MB)">
          <Input
            type="number"
            value={values.memoryLimitMb}
            onChange={(event) => update("memoryLimitMb", Number(event.target.value))}
          />
        </Field>
        <Field label="출력 비교">
          <Select
            value={values.outputComparison}
            onChange={(event) => update("outputComparison", event.target.value as OutputComparison)}
          >
            {Object.entries(OUTPUT_COMPARISON_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        {/*
          오차 칸은 **실수 비교를 골랐을 때만** 보인다 (#279). 늘 보이면 정확 일치
          문제에도 값을 채우게 되고, 그 값은 아무 일도 하지 않으면서 "이 문제는 오차를
          허용한다" 고 읽힌다.
        */}
        {values.outputComparison === "FLOAT" ? (
          <Field label="허용 오차">
            <Input
              type="number"
              step="0.000001"
              min={0}
              max={0.1}
              value={values.floatEpsilon}
              onChange={(event) => update("floatEpsilon", Number(event.target.value))}
            />
            {/* 오차를 크게 잡으면 틀린 답이 통과한다 — 채점을 끄는 것과 같다. */}
            <p className="text-xs text-ink-muted">
              절대·상대 오차 중 하나만 만족해도 맞은 답으로 봅니다. 보통 0.000001 을 씁니다.
            </p>
          </Field>
        ) : null}
    </div>
  );
}
