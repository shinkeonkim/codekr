"use client";

import {
  ALL_DIFFICULTIES,
  CATEGORY_LABELS,
  SELECTABLE_KINDS,
  difficultyLabel,
} from "@/entities/problem";
import type {
  Difficulty,
  ProblemVerification,
  SqlSpec,
  Testcase,
} from "@/entities/problem";
import {
  BLANK_MONGO_SPEC,
  BLANK_QUIZ_SPEC,
  BLANK_REGEX_SPEC,
  BLANK_REDIS_SPEC,
  BLANK_SQL_SPEC,
  EMPTY_TESTCASE,
} from "../model/values";
import type { ProblemFormValues } from "../model/values";
import { ApiError } from "@/shared/api";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { FormEvent } from "react";
import { CreditFields } from "./CreditFields";
import { DraftFromStatement } from "./DraftFromStatement";
import { ProblemDescriptionFields } from "./ProblemDescriptionFields";
import { FormSection } from "./FormSection";
import { ProblemMetaFields } from "./ProblemMetaFields";
import { ProblemTemplateEditor } from "./ProblemTemplateEditor";
import { AllowedRuntimeEditor } from "./AllowedRuntimeEditor";
import { HarnessEditor } from "./HarnessEditor";
import { RuntimeLimitEditor } from "./RuntimeLimitEditor";
import { MongoSpecEditor } from "./MongoSpecEditor";
import { QuizSpecEditor } from "./QuizSpecEditor";
import { RegexSpecEditor } from "./RegexSpecEditor";
import { RedisSpecEditor } from "./RedisSpecEditor";
import { SqlSpecEditor } from "./SqlSpecEditor";
import { SolutionVerifier } from "./SolutionVerifier";
import {
  Alert,
  Button,
  Card,
  CheckboxField,
  Field,
  Input,
  Select,
  Textarea,
  useToast,
} from "@/shared/ui";

interface Props {
  initial: ProblemFormValues;
  submitLabel: string;
  onSubmit: (values: ProblemFormValues) => Promise<unknown>;
  /** 수정 화면에서만 주어진다 — 검증은 저장된 문제에 대해서만 실행할 수 있다. */
  problemId?: number;
  verification?: ProblemVerification | null;
  /**
   * 이 유형이 정답 코드 검증(#39)을 지원하는가 (#495).
   *
   * **화면이 유형 이름을 나열하지 않는다** — 서버가 말해 준다. 나열하면 유형이 하나
   * 늘 때마다 여기를 고쳐야 하고, 빠뜨리면 눌러도 안 되는 버튼이 남는다.
   */
  canVerifySolution?: boolean;
}

/**
 * 문제 폼 (#127, #337).
 *
 * **일곱 덩어리를 한 화면에서 채운다.** 구획으로 나누되 탭이 아니라 접이식이다 —
 * 이유는 `FormSection` 에 있다.
 *
 * **등록과 수정의 껍데기만 다르다** (#337): 처음 만들 때는 순서가 도움이 되므로 전부
 * 펴 두고, 고칠 때는 방해가 되므로 접어서 목차로 쓴다. **구획 컴포넌트와 검증·저장은
 * 한 벌이다** — 갈라지면 "등록에서는 되는데 수정에서는 안 되는" 것이 생긴다.
 */
export function ProblemForm({
  initial,
  submitLabel,
  onSubmit,
  problemId,
  verification,
  canVerifySolution = true,
}: Props) {
  /*
    **등록과 수정의 유일한 차이** (#337).

    처음 만들 때는 순서가 도움이 되므로 전부 펴 두고, 고칠 때는 방해가 되므로 접어서
    목차로 쓴다 — 고치려는 한 구획만 열면 된다. `problemId` 가 있으면 수정이다.

    구획 컴포넌트와 검증·저장은 **한 벌**이다. 갈라지면 "등록에서는 되는데 수정에서는
    안 되는" 것이 생긴다.
  */
  const open = problemId === undefined;
  const toast = useToast();
  const router = useRouter();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = <K extends keyof ProblemFormValues>(
    key: K,
    value: ProblemFormValues[K],
  ) => setValues((previous) => ({ ...previous, [key]: value }));

  /**
   * AI 가 채운 칸 (#230).
   *
   * **사람이 쓴 것과 구분되지 않으면 검토가 형식이 된다.** 구획 제목 옆에 뱃지를
   * 달아, 저장을 누르기 전에 어디를 봐야 하는지 보이게 한다.
   *
   * 사람이 그 칸을 손대면 표시가 사라진다 — 검토를 마쳤다는 뜻이다.
   */
  const [drafted, setDrafted] = useState<Set<keyof ProblemFormValues>>(
    new Set(),
  );

  const fillFromDraft = (
    patch: Partial<ProblemFormValues>,
    filled: (keyof ProblemFormValues)[],
  ) => {
    setValues((previous) => ({ ...previous, ...patch }));
    setDrafted(new Set(filled));
  };

  const touched = <K extends keyof ProblemFormValues>(
    key: K,
    value: ProblemFormValues[K],
  ) => {
    update(key, value);
    setDrafted((previous) => {
      if (!previous.has(key)) return previous;
      const next = new Set(previous);
      next.delete(key);
      return next;
    });
  };

  /** 이 구획에 아직 검토하지 않은 초안이 있는가. */
  const draftBadge = (...keys: (keyof ProblemFormValues)[]) =>
    keys.some((key) => drafted.has(key)) ? "AI 초안 — 확인하세요" : undefined;

  const isSql = values.problemKind === "JUDGE_SQL";
  // 함수형은 허용 언어를 따로 고르지 않는다 — 하네스가 그것을 정한다 (#446).
  const isFunction = values.problemKind === "JUDGE_FUNCTION";
  const isRedis = values.problemKind === "JUDGE_REDIS";
  const isMongo = values.problemKind === "JUDGE_MONGODB";
  // 퀴즈는 실행기를 쓰지 않는다 (#650) — 언어·제한·테스트케이스가 모두 뜻이 없다.
  const isQuiz = values.problemKind === "QUIZ";
  // 정규식도 실행기를 쓰지만 **언어를 고르지 않는다** (#653) — 엔진은 문제가 정한다.
  const isRegex = values.problemKind === "JUDGE_REGEX";

  /**
   * 채점 방식을 바꾸면 **그 유형의 자료만 남긴다** (#60).
   *
   * 둘 다 실어 보내면 서버가 거부한다 — 섞이면 어느 쪽이 진짜인지 알 수 없기 때문이다.
   */
  const changeKind = (nextKind: string) =>
    setValues((previous) => ({
      ...previous,
      problemKind: nextKind,
      sqlSpec:
        nextKind === "JUDGE_SQL" ? (previous.sqlSpec ?? BLANK_SQL_SPEC) : null,
      redisSpec:
        nextKind === "JUDGE_REDIS"
          ? (previous.redisSpec ?? BLANK_REDIS_SPEC)
          : null,
      mongoSpec:
        nextKind === "JUDGE_MONGODB"
          ? (previous.mongoSpec ?? BLANK_MONGO_SPEC)
          : null,
      quizSpec: nextKind === "QUIZ" ? (previous.quizSpec ?? BLANK_QUIZ_SPEC) : null,
      regexSpec:
        nextKind === "JUDGE_REGEX" ? (previous.regexSpec ?? BLANK_REGEX_SPEC) : null,
      // 테스트케이스로 채점하지 않는 유형은 그 칸을 비운다 (#455, #527).
      testcases:
        nextKind === "JUDGE_SQL" ||
        nextKind === "JUDGE_REDIS" ||
        nextKind === "JUDGE_MONGODB" ||
        nextKind === "QUIZ" ||
        nextKind === "JUDGE_REGEX"
          ? []
          : previous.testcases,
    }));

  const updateTestcase = (index: number, patch: Partial<Testcase>) =>
    setValues((previous) => ({
      ...previous,
      testcases: previous.testcases.map((it, i) =>
        i === index ? { ...it, ...patch } : it,
      ),
    }));

  const addTestcase = () =>
    setValues((previous) => ({
      ...previous,
      testcases: [
        ...previous.testcases,
        {
          ...EMPTY_TESTCASE,
          seq: previous.testcases.length + 1,
          visibility: "HIDDEN",
        },
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
      const message =
        caught instanceof ApiError ? caught.message : "저장에 실패했습니다.";
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="space-y-4" onSubmit={handleSubmit}>
      {error ? <Alert>{error}</Alert> : null}

      {/*
        구획은 **유형에 따라 사라진다** (#337). SQL 문제에 테스트케이스 칸을 그리면
        채워도 쓰이지 않는 값을 받게 된다.

        `defaultOpen` 이 등록·수정의 유일한 차이다 — 나머지는 같은 컴포넌트다.
      */}
      {/*
        지문에서 초안 만들기 (#230). **등록할 때만 보인다** — 이미 있는 문제를
        고치는 중에 초안을 덮어쓰면 사람이 쓴 것을 잃는다.

        키가 없으면 서버가 404 를 주고, 그때는 실패를 알리고 끝이다. 이 구획을 열지
        않아도 아래 칸들을 손으로 채우는 길은 그대로다.
      */}
      {open ? (
        <FormSection
          title="지문에서 초안 만들기"
          defaultOpen={false}
          description="붙여 넣으면 아래 칸을 채운다 — 저장은 사람이 한다 (#230)"
        >
          <DraftFromStatement onFill={fillFromDraft} />
        </FormSection>
      ) : null}

      <FormSection
        title="기본 정보"
        required
        defaultOpen={open}
        description={draftBadge(
          "title",
          "category",
          "timeLimitMs",
          "memoryLimitMb",
        )}
      >
        <ProblemMetaFields
          values={values}
          onChange={touched}
          onChangeKind={changeKind}
        />
      </FormSection>

      <FormSection
        title="지문"
        required
        defaultOpen={open}
        description={
          draftBadge("description", "inputDescription", "outputDescription") ??
          "문제 설명과 입출력 형식"
        }
      >
        <ProblemDescriptionFields values={values} onChange={touched} />
      </FormSection>

      <FormSection
        title="출제 정보"
        defaultOpen={open}
        description="누가 만들고 어디서 왔는지 (#236)"
      >
        <CreditFields
          setters={values.setters}
          reviewers={values.reviewers}
          sourceLabel={values.sourceLabel}
          sourceUrl={values.sourceUrl}
          onChange={(patch: Record<string, unknown>) => {
            for (const [key, next] of Object.entries(patch)) {
              update(key as keyof typeof values, next as never);
            }
          }}
        />
      </FormSection>

      {/*
        유형별 입력 묶음 (#59, #60). 채점 대상이 유형마다 다르다 —
        stdin/stdout 은 테스트케이스, SQL 은 스키마와 정답 쿼리다.
      */}
      {isSql ? (
        <FormSection title="SQL 스키마와 정답" required defaultOpen={open}>
          <SqlSpecEditor
            value={values.sqlSpec ?? BLANK_SQL_SPEC}
            onChange={(spec) => update("sqlSpec", spec)}
          />
        </FormSection>
      ) : isRedis ? (
        <FormSection title="Redis 시드와 정답" required defaultOpen={open}>
          <RedisSpecEditor
            value={values.redisSpec ?? BLANK_REDIS_SPEC}
            onChange={(spec) => update("redisSpec", spec)}
          />
        </FormSection>
      ) : isMongo ? (
        <FormSection title="MongoDB 시드와 정답" required defaultOpen={open}>
          <MongoSpecEditor
            value={values.mongoSpec ?? BLANK_MONGO_SPEC}
            onChange={(spec) => update("mongoSpec", spec)}
          />
        </FormSection>
      ) : isRegex ? (
        <FormSection title="확인할 문자열" required defaultOpen={open}>
          <RegexSpecEditor
            value={values.regexSpec ?? BLANK_REGEX_SPEC}
            onChange={(spec) => update("regexSpec", spec)}
          />
        </FormSection>
      ) : isQuiz ? (
        <FormSection title="보기와 정답" required defaultOpen={open}>
          <QuizSpecEditor
            value={values.quizSpec ?? BLANK_QUIZ_SPEC}
            onChange={(spec) => update("quizSpec", spec)}
          />
        </FormSection>
      ) : (
        <FormSection
          title="테스트케이스"
          required
          defaultOpen={open}
          description={draftBadge("testcases") ?? "공개(예제)와 비공개"}
        >
          <div className="flex items-center justify-end">
            <Button type="button" variant="secondary" onClick={addTestcase}>
              추가
            </Button>
          </div>

          {values.testcases.map((testcase, index) => (
            <div
              key={index}
              className="space-y-2 rounded-lg border border-border p-4"
            >
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-ink">
                  #{testcase.seq}
                </span>
                <Select
                  className="w-40"
                  value={testcase.visibility}
                  onChange={(event) =>
                    updateTestcase(index, {
                      visibility: event.target.value as Testcase["visibility"],
                    })
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
                  onChange={(event) =>
                    updateTestcase(index, { input: event.target.value })
                  }
                />
                <Textarea
                  rows={3}
                  placeholder="기대 출력"
                  value={testcase.expectedOutput}
                  onChange={(event) =>
                    updateTestcase(index, {
                      expectedOutput: event.target.value,
                    })
                  }
                />
              </div>
            </div>
          ))}
        </FormSection>
      )}

      {isFunction ? (
      <FormSection
        title="하네스 (보이지 않는 실행 코드)"
        defaultOpen={open}
        description="여기 쓴 언어로만 풀 수 있다"
      >
      <HarnessEditor
        value={values.harnesses}
        onChange={(harnesses) => update("harnesses", harnesses)}
      />
      </FormSection>
      ) : (
      <FormSection
        title="풀 수 있는 언어"
        defaultOpen={open}
        description="비우면 이 유형의 전부"
      >
        <AllowedRuntimeEditor
          value={values.allowedRuntimeIds}
          onChange={(allowedRuntimeIds) =>
            update("allowedRuntimeIds", allowedRuntimeIds)
          }
        />
      </FormSection>
      )}

      <FormSection
        title="언어별 제한"
        defaultOpen={open}
        description="비우면 기본 제한을 쓴다"
      >
        <RuntimeLimitEditor
          limits={values.runtimeLimits}
          baseTimeLimitMs={values.timeLimitMs}
          baseMemoryLimitMb={values.memoryLimitMb}
          onChange={(runtimeLimits) => update("runtimeLimits", runtimeLimits)}
        />
      </FormSection>

      <FormSection
        title="초기 코드"
        defaultOpen={open}
        description="언어마다 처음 보이는 코드"
      >
        <ProblemTemplateEditor
          templates={values.templates}
          onChange={(templates) => update("templates", templates)}
        />
      </FormSection>

      {/*
        **검증할 수 없는 유형에서는 자리를 그리지 않는다** (#495). SQL·Redis 문제는
        테스트케이스가 0개인 것이 정상이고, 그 상태로 누르면 "테스트케이스가 없다" 는
        고칠 수 없는 말이 돌아왔다. 유형 이름을 여기서 나열하지 않는 이유는 유형이
        늘 때마다 이 화면을 고쳐야 하기 때문이다 — 서버가 말해 준다.
      */}
      {canVerifySolution ? (
        <FormSection
          title="정답 코드와 검증"
          defaultOpen={open}
          description="사람이 아니라 기계가 확인한다 (#39)"
        >
          <SolutionVerifier
            problemId={problemId ?? null}
            solution={values.solution}
            verification={verification ?? null}
            onChange={(solution) => update("solution", solution)}
          />
        </FormSection>
      ) : null}

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
