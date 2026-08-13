"use client";

import { ALL_DIFFICULTIES, CATEGORY_LABELS, SELECTABLE_KINDS, difficultyLabel } from "@/entities/problem";
import type { Difficulty, ProblemVerification, SqlSpec, Testcase } from "@/entities/problem";
import { BLANK_SQL_SPEC, EMPTY_TESTCASE } from "../model/values";
import type { ProblemFormValues } from "../model/values";
import { ApiError } from "@/shared/api";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { ProblemDescriptionFields } from "./ProblemDescriptionFields";
import { ProblemMetaFields } from "./ProblemMetaFields";
import { ProblemTemplateEditor } from "./ProblemTemplateEditor";
import { RuntimeLimitEditor } from "./RuntimeLimitEditor";
import { SqlSpecEditor } from "./SqlSpecEditor";
import { SolutionVerifier } from "./SolutionVerifier";
import { Alert, Button, Card, CheckboxField, Field, Input, Select, Textarea, useToast } from "@/shared/ui";

interface Props {
  initial: ProblemFormValues;
  submitLabel: string;
  onSubmit: (values: ProblemFormValues) => Promise<unknown>;
  /** 수정 화면에서만 주어진다 — 검증은 저장된 문제에 대해서만 실행할 수 있다. */
  problemId?: number;
  verification?: ProblemVerification | null;
}

export function ProblemForm({ initial, submitLabel, onSubmit, problemId, verification }: Props) {
  const toast = useToast();
  const router = useRouter();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = <K extends keyof ProblemFormValues>(key: K, value: ProblemFormValues[K]) =>
    setValues((previous) => ({ ...previous, [key]: value }));

  const isSql = values.problemKind === "JUDGE_SQL";

  /**
   * 채점 방식을 바꾸면 **그 유형의 자료만 남긴다** (#60).
   *
   * 둘 다 실어 보내면 서버가 거부한다 — 섞이면 어느 쪽이 진짜인지 알 수 없기 때문이다.
   */
  const changeKind = (nextKind: string) =>
    setValues((previous) => ({
      ...previous,
      problemKind: nextKind,
      sqlSpec: nextKind === "JUDGE_SQL" ? (previous.sqlSpec ?? BLANK_SQL_SPEC) : null,
      testcases: nextKind === "JUDGE_SQL" ? [] : previous.testcases,
    }));

  const updateTestcase = (index: number, patch: Partial<Testcase>) =>
    setValues((previous) => ({
      ...previous,
      testcases: previous.testcases.map((it, i) => (i === index ? { ...it, ...patch } : it)),
    }));

  const addTestcase = () =>
    setValues((previous) => ({
      ...previous,
      testcases: [
        ...previous.testcases,
        { ...EMPTY_TESTCASE, seq: previous.testcases.length + 1, visibility: "HIDDEN" },
      ],
    }));

  const removeTestcase = (index: number) =>
    setValues((previous) => ({
      // 순번은 항상 1부터 이어지도록 다시 매긴다 (서버가 중복/누락을 거부한다).
      ...previous,
      testcases: previous.testcases
        .filter((_, i) => i !== index)
        .map((testcase, i) => ({ ...testcase, seq: i + 1 })),
    }));

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit(values);
      // 저장 직후 목록으로 떠나므로 화면 안의 안내는 보이지 않는다 — 토스트여야 한다 (#112).
      toast.success(`"${values.title}" 문제를 저장했습니다.`);
      router.push("/admin/problems");
    } catch (caught) {
      // 저장 실패는 이 화면에 남아 고쳐야 하는 일이라 인라인으로도 남긴다.
      const message = caught instanceof ApiError ? caught.message : "저장에 실패했습니다.";
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      {error ? <Alert>{error}</Alert> : null}

      <ProblemMetaFields values={values} onChange={update} onChangeKind={changeKind} />

      <ProblemDescriptionFields values={values} onChange={update} />

      {/*
        유형별 입력 묶음 (#59, #60). 채점 대상이 유형마다 다르다 —
        stdin/stdout 은 테스트케이스, SQL 은 스키마와 정답 쿼리다.
      */}
      {isSql ? (
        <SqlSpecEditor value={values.sqlSpec ?? BLANK_SQL_SPEC} onChange={(spec) => update("sqlSpec", spec)} />
      ) : (
      <Card className="space-y-4 p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-ink">테스트케이스</h2>
          <Button type="button" variant="secondary" onClick={addTestcase}>
            추가
          </Button>
        </div>

        {values.testcases.map((testcase, index) => (
          <div key={index} className="space-y-2 rounded-lg border border-border p-4">
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-ink">#{testcase.seq}</span>
              <Select
                className="w-40"
                value={testcase.visibility}
                onChange={(event) =>
                  updateTestcase(index, { visibility: event.target.value as Testcase["visibility"] })
                }
              >
                <option value="PUBLIC">공개 (예제)</option>
                <option value="HIDDEN">비공개</option>
              </Select>
              <Button
                type="button"
                variant="danger"
                className="ml-auto"
                onClick={() => removeTestcase(index)}
                disabled={values.testcases.length === 1}
              >
                삭제
              </Button>
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <Textarea
                rows={3}
                placeholder="입력"
                value={testcase.input}
                onChange={(event) => updateTestcase(index, { input: event.target.value })}
              />
              <Textarea
                rows={3}
                placeholder="기대 출력"
                value={testcase.expectedOutput}
                onChange={(event) => updateTestcase(index, { expectedOutput: event.target.value })}
              />
            </div>
          </div>
        ))}
      </Card>
      )}

      <RuntimeLimitEditor
        limits={values.runtimeLimits}
        baseTimeLimitMs={values.timeLimitMs}
        baseMemoryLimitMb={values.memoryLimitMb}
        onChange={(runtimeLimits) => update("runtimeLimits", runtimeLimits)}
      />

      <ProblemTemplateEditor
        templates={values.templates}
        onChange={(templates) => update("templates", templates)}
      />

      <SolutionVerifier
        problemId={problemId ?? null}
        solution={values.solution}
        verification={verification ?? null}
        onChange={(solution) => update("solution", solution)}
      />

      <div className="flex items-center gap-3">
        <CheckboxField
          label="공개하기"
          checked={values.published}
          onCheckedChange={(next) => update("published", next)}
        />
        <Button type="submit" className="ml-auto" disabled={submitting}>
          {submitting ? "저장 중…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
