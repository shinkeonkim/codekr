"use client";

import { collectionApi } from "@/entities/collection";
import type { CollectionSummary } from "@/entities/collection";
import { useAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { Badge, Button, Card, EmptyState, PAGE_WIDTH, Pagination } from "@/shared/ui";
import { CollectionTabs } from "@/widgets/collection-tabs";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

/**
 * 내 문제집 (#87).
 *
 * **로그인하지 않았으면 공개 문제집으로 보낸다** (#601). 내비의 "문제집" 은 여기로
 * 오는데, 로그인 벽을 세우면 **문제집이라는 것이 있다는 것조차 보지 못한다.**
 * 로그인이 필요한 것은 "내" 것이지 문제집 전체가 아니다.
 */
export function CollectionListPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) router.replace("/collections/explore");
  }, [loading, user, router]);

  if (loading || !user) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;
  return <MyCollections />;
}

function MyCollections() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<CollectionSummary> | null>(null);

  useEffect(() => {
    let cancelled = false;
    collectionApi
      .mine({ page, size: 20 })
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch(() => {
        if (!cancelled) setResult(null);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className={`${PAGE_WIDTH.wide} space-y-5`}>
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">문제집</h1>
        <Button asChild>
          <Link href="/collections/new" className="ml-auto">
            새 문제집
          </Link>
        </Button>
      </header>
      <CollectionTabs />

      {!result ? <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p> : null}

      {result && result.content.length === 0 ? (
        <EmptyState
          mascot="study"
          title="아직 문제집이 없습니다."
          description="풀 문제를 주제나 순서로 묶어 두면 다음에 무엇을 풀지 고르는 시간이 줄어듭니다."
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((collection) => (
          <Link key={collection.id} href={`/collections/${collection.id}`} className="block">
            <Card className="flex flex-wrap items-center gap-3 p-5 transition hover:border-brand/50">
              <span className="flex-1 truncate font-medium text-ink">{collection.name}</span>
              <Badge tone={collection.visibility === "PRIVATE" ? "muted" : "info"}>
                {collection.visibilityLabel}
              </Badge>
              {/* 진행률은 "몇 개 남았나" 를 바로 보여준다. 비율만으로는 감이 안 온다. */}
              <span className="text-xs tabular-nums text-ink-muted">
                {collection.solvedCount} / {collection.problemCount} 문제
              </span>
            </Card>
          </Link>
        ))}
      </div>

      {result ? (
        <Pagination
          page={result.page}
          totalPages={result.totalPages}
          totalElements={result.totalElements}
          onChange={setPage}
        />
      ) : null}
    </div>
  );
}
