"use client";

import { ALL_DIFFICULTIES, CATEGORY_LABELS, difficultyLabel } from "@/entities/problem";
import type { AdminProblemDetail, Difficulty, ProblemRuntimeLimit, ProblemSolution, ProblemTemplate, ProblemVerification, Testcase } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { ProblemTemplateEditor } from "./ProblemTemplateEditor";
import { RuntimeLimitEditor } from "./RuntimeLimitEditor";
import { SolutionVerifier } from "./SolutionVerifier";
import { Alert, Button, Card, Field, Input, Select, Textarea, useToast } from "@/shared/ui";

export interface ProblemFormValues {
  slug: string;
  title: string;
  category: string;
  difficulty: Difficulty;
  description: string;
  inputDescription: string;
  outputDescription: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  published: boolean;
  testcases: Testcase[];
  templates: ProblemTemplate[];
  runtimeLimits: ProblemRuntimeLimit[];
  solution: ProblemSolution | null;
}

const EMPTY_TESTCASE: Testcase = { seq: 1, input: "", expectedOutput: "", visibility: "PUBLIC" };

export function toFormValues(problem: AdminProblemDetail): ProblemFormValues {
  return {
    slug: problem.slug,
    title: problem.title,
    category: problem.category,
    difficulty: problem.difficulty,
    description: problem.description,
    inputDescription: problem.inputDescription ?? "",
    outputDescription: problem.outputDescription ?? "",
    timeLimitMs: problem.timeLimitMs,
    memoryLimitMb: problem.memoryLimitMb,
    published: problem.published,
    testcases: problem.testcases,
    templates: problem.templates,
    runtimeLimits: problem.runtimeLimits ?? [],
    solution: problem.solution,
  };
}

export const BLANK_PROBLEM: ProblemFormValues = {
  slug: "",
  title: "",
  category: "ALGORITHM",
  difficulty: "BRONZE_5",
  description: "",
  inputDescription: "",
  outputDescription: "",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  published: false,
  testcases: [EMPTY_TESTCASE],
  templates: [],
  runtimeLimits: [],
  solution: null,
};

interface Props {
  initial: ProblemFormValues;
  submitLabel: string;
  onSubmit: (values: ProblemFormValues) => Promise<unknown>;
  /** 수정 화면에서만 주어진다 — 검증은 저장된 문제에 대해서만 실행할 수 있다. */
  problemId?: number;
  verification?: ProblemVerification | null;
}

/** 문제 등록과 수정이 같은 폼을 쓴다 — 요청 본문 모양이 동일하기 때문이다. */
export function ProblemForm({ initial, submitLabel, onSubmit, problemId, verification }: Props) {
  const toast = useToast();
  const router = useRouter();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = <K extends keyof ProblemFormValues>(key: K, value: ProblemFormValues[K]) =>
    setValues((previous) => ({ ...previous, [key]: value }));

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
        <Field label="유형">
          <Select value={values.category} onChange={(event) => update("category", event.target.value)}>
            {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
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

      <Card className="space-y-4 p-5">
        <Field label="문제 설명">
          <Textarea
            rows={8}
            value={values.description}
            onChange={(event) => update("description", event.target.value)}
            required
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="입력 형식">
            <Textarea
              rows={3}
              value={values.inputDescription}
              onChange={(event) => update("inputDescription", event.target.value)}
            />
          </Field>
          <Field label="출력 형식">
            <Textarea
              rows={3}
              value={values.outputDescription}
              onChange={(event) => update("outputDescription", event.target.value)}
            />
          </Field>
        </div>
      </Card>

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
        <label className="flex items-center gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={values.published}
            onChange={(event) => update("published", event.target.checked)}
          />
          공개하기
        </label>
        <Button type="submit" className="ml-auto" disabled={submitting}>
          {submitting ? "저장 중…" : submitLabel}
        </Button>
      </div>
    </form>
  );
}
