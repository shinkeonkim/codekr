"use client";

import { ALL_DIFFICULTIES, CATEGORY_LABELS, SELECTABLE_KINDS, difficultyLabel } from "@/entities/problem";
import type { Difficulty } from "@/entities/problem";
import { Card, Field, Input, Select } from "@/shared/ui";
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
      <Card className="grid gap-4 p-5 sm:grid-cols-2">
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
            value={values.difficulty}
            onChange={(event) => update("difficulty", event.target.value as Difficulty)}
          >
            {ALL_DIFFICULTIES.map((value) => (
              <option key={value} value={value}>
                {difficultyLabel(value)}
              </option>
            ))}
          </Select>
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
      </Card>
  );
}
