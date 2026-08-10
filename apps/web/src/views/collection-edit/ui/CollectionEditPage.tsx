"use client";

import { collectionApi } from "@/entities/collection";
import { RequireAuth } from "@/features/auth";
import { BLANK_COLLECTION, CollectionEditor } from "@/features/collection-editor";
import type { CollectionFormValues } from "@/features/collection-editor";
import { problemApi } from "@/entities/problem";
import { EmptyState } from "@/shared/ui";
import { use, useEffect, useState } from "react";

export function CollectionNewPage() {
  return (
    <RequireAuth>
      <div className="space-y-5">
        <h1 className="text-2xl font-bold text-ink">새 문제집</h1>
        <CollectionEditor initial={BLANK_COLLECTION} />
      </div>
    </RequireAuth>
  );
}

export function CollectionEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireAuth>
      <EditCollection id={Number(id)} />
    </RequireAuth>
  );
}

function EditCollection({ id }: { id: number }) {
  const [initial, setInitial] = useState<CollectionFormValues | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    collectionApi
      .detail(id)
      .then(async (detail) => {
        // 상세 응답에는 문제 id 가 없다(slug 로 준다). 편집기는 id 로 보내야 하므로 채워 온다.
        const problems = await Promise.all(
          detail.problems.map((problem) => problemApi.detail(problem.slug)),
        );
        setInitial({
          name: detail.summary.name,
          description: detail.summary.description,
          visibility: detail.summary.visibility,
          problems: problems.map((problem) => ({
            id: problem.id,
            slug: problem.slug,
            title: problem.title,
            difficulty: problem.difficulty,
            difficultyLabel: problem.difficultyLabel,
          })),
        });
      })
      .catch(() => setError("문제집을 불러오지 못했습니다."));
  }, [id]);

  if (error) return <EmptyState title={error} />;
  if (!initial) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-ink">문제집 수정</h1>
      <CollectionEditor initial={initial} collectionId={id} />
    </div>
  );
}
