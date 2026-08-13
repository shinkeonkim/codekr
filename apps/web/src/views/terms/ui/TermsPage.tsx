"use client";

import { userApi } from "@/entities/user";
import type { TermSummary } from "@/entities/user";
import { Card, EmptyState } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/** 약관 목록 (#235). footer 에서 온다 (#234). */
export function TermsPage() {
  const [terms, setTerms] = useState<TermSummary[] | null>(null);

  useEffect(() => {
    userApi
      .terms()
      .then(setTerms)
      .catch(() => setTerms([]));
  }, []);

  if (terms && terms.length === 0) return <EmptyState title="약관이 아직 없습니다." />;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-ink">약관</h1>
      <div className="space-y-2">
        {terms?.map((term) => (
          <Link key={term.id} href={`/terms/${term.id}`} className="block">
            <Card className="flex flex-wrap items-baseline gap-2 p-4 hover:bg-surface-muted">
              <span className="font-medium text-ink">{term.title}</span>
              {/* 판 번호와 시행일이 함께 보여야 "지금 무엇이 적용되는지" 를 안다. */}
              <span className="text-xs text-ink-muted">
                {term.version} · {term.effectiveAt.slice(0, 10)} 시행
              </span>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
