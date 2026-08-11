"use client";

import { MIN_SHARED_PROBLEMS, VISIBILITY_LABELS, collectionApi } from "@/entities/collection";
import type { CollectionVisibility } from "@/entities/collection";
import { TierBadge } from "@/entities/problem";
import type { ProblemSummary } from "@/entities/problem";
import { ApiError } from "@/shared/api";
import { Alert, Button, Card, Field, Input, Select, Textarea, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ProblemPicker } from "./ProblemPicker";

/** 담은 문제. 서버에는 id 만 보내지만 화면에는 제목·티어가 있어야 한다. */
export interface PickedProblem {
  id: number;
  slug: string;
  title: string;
  difficulty: ProblemSummary["difficulty"];
  difficultyLabel: string;
}

export interface CollectionFormValues {
  name: string;
  description: string;
  visibility: CollectionVisibility;
  problems: PickedProblem[];
}

export const BLANK_COLLECTION: CollectionFormValues = {
  name: "",
  description: "",
  visibility: "PRIVATE",
  problems: [],
};

/** 문제집 만들기·수정 (#87). */
export function CollectionEditor({
  initial,
  collectionId,
}: {
  initial: CollectionFormValues;
  collectionId?: number;
}) {
  const router = useRouter();
  const toast = useToast();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const pick = (problem: ProblemSummary) =>
    setValues((previous) =>
      previous.problems.some((it) => it.id === problem.id)
        ? previous
        : { ...previous, problems: [...previous.problems, toPicked(problem)] },
    );

  const drop = (id: number) =>
    setValues((previous) => ({
      ...previous,
      problems: previous.problems.filter((it) => it.id !== id),
    }));

  const move = (index: number, delta: number) =>
    setValues((previous) => {
      const next = [...previous.problems];
      const target = index + delta;
      if (target < 0 || target >= next.length) return previous;
      [next[index], next[target]] = [next[target], next[index]];
      return { ...previous, problems: next };
    });

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const body = {
        name: values.name,
        description: values.description,
        visibility: values.visibility,
        problemIds: values.problems.map((it) => it.id),
      };
      const saved = collectionId
        ? await collectionApi.update(collectionId, body)
        : await collectionApi.create(body);
      toast.success("문제집을 저장했습니다.");
      router.push(`/collections/${saved.summary.id}`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const tooFewToShare =
    values.visibility !== "PRIVATE" && values.problems.length < MIN_SHARED_PROBLEMS;

  return (
    <form className="space-y-5" onSubmit={save}>
      {error ? <Alert>{error}</Alert> : null}

      <Card className="grid gap-4 p-5 sm:grid-cols-2">
        <Field label="이름">
          <Input
            value={values.name}
            onChange={(event) => setValues({ ...values, name: event.target.value })}
            placeholder="DP 입문 10선"
            required
          />
        </Field>
        <Field label="공개 범위">
          <Select
            value={values.visibility}
            onChange={(event) =>
              setValues({ ...values, visibility: event.target.value as CollectionVisibility })
            }
          >
            {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <div className="sm:col-span-2">
          <Field label="설명">
            <Textarea
              rows={3}
              value={values.description}
              onChange={(event) => setValues({ ...values, description: event.target.value })}
              placeholder="어떤 사람에게, 어떤 순서로 권하는 묶음인지 적어 주세요."
            />
          </Field>
        </div>
      </Card>

      <Card className="space-y-4 p-5">
        <h2 className="text-sm font-semibold text-ink">담은 문제 {values.problems.length}개</h2>
        <ProblemPicker pickedIds={values.problems.map((it) => it.id)} onPick={pick} />

        {values.problems.length === 0 ? (
          <p className="text-xs text-ink-muted">
            위에서 문제를 찾아 담으세요. 이름만 정해 두고 나중에 채워도 됩니다.
          </p>
        ) : (
          <ol className="divide-y divide-border rounded-lg border border-border">
            {values.problems.map((problem, index) => (
              <li key={problem.id} className="flex items-center gap-3 px-4 py-2.5">
                <span className="w-6 text-xs tabular-nums text-ink-muted">{index + 1}</span>
                <TierBadge difficulty={problem.difficulty} label={problem.difficultyLabel} />
                <span className="min-w-0 flex-1 truncate text-sm text-ink">{problem.title}</span>
                <Button
                  type="button"
                  variant="secondary"
                  className="px-2 py-1 text-xs"
                  onClick={() => move(index, -1)}
                  disabled={index === 0}
                  aria-label="위로"
                >
                  ↑
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  className="px-2 py-1 text-xs"
                  onClick={() => move(index, 1)}
                  disabled={index === values.problems.length - 1}
                  aria-label="아래로"
                >
                  ↓
                </Button>
                <Button
                  type="button"
                  variant="danger"
                  className="px-2 py-1 text-xs"
                  onClick={() => drop(problem.id)}
                >
                  빼기
                </Button>
              </li>
            ))}
          </ol>
        )}
      </Card>

      {/* 저장을 눌러야 알게 되면 늦다. 왜 저장할 수 없는지 미리 말한다. */}
      {tooFewToShare ? (
        <Alert>공유하려면 문제가 {MIN_SHARED_PROBLEMS}개 이상이어야 합니다.</Alert>
      ) : null}

      <Button type="submit" disabled={saving || tooFewToShare}>
        {saving ? "저장 중…" : "저장"}
      </Button>
    </form>
  );
}

function toPicked(problem: ProblemSummary): PickedProblem {
  return {
    id: problem.id,
    slug: problem.slug,
    title: problem.title,
    difficulty: problem.difficulty,
    difficultyLabel: problem.difficultyLabel,
  };
}
