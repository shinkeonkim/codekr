"use client";

import { request } from "@/shared/api";
import { Card, EmptyState } from "@/shared/ui";
import { use, useEffect, useState } from "react";

interface TermDetail {
  id: number;
  title: string;
  version: string;
  body: string;
  effectiveAt: string;
}

/**
 * 약관 전문 (#235).
 *
 * **마크다운으로 그리지 않는다.** 약관은 쓴 그대로 보여야 하는 글이고, 서식 해석이
 * 끼어들면 원문과 보이는 것이 달라진다 (#338 이 지문에서 겪는 것과 반대 방향이다).
 */
export function TermDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [term, setTerm] = useState<TermDetail | null>(null);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    request<TermDetail>(`/api/v1/terms/${id}`)
      .then(setTerm)
      .catch(() => setMissing(true));
  }, [id]);

  if (missing) return <EmptyState title="약관을 찾을 수 없습니다." />;
  if (!term) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold text-ink">{term.title}</h1>
        <p className="mt-1 text-xs text-ink-muted">
          {term.version} · {term.effectiveAt.slice(0, 10)} 시행
        </p>
      </div>
      <Card className="p-5">
        <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink">{term.body}</p>
      </Card>
    </div>
  );
}
