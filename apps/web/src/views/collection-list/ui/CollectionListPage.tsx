"use client";

import { collectionApi } from "@/entities/collection";
import type { CollectionSummary } from "@/entities/collection";
import { RequireAuth } from "@/features/auth";
import { Badge, Button, Card, EmptyState } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

export function CollectionListPage() {
  return (
    <RequireAuth>
      <MyCollections />
    </RequireAuth>
  );
}

function MyCollections() {
  const [collections, setCollections] = useState<CollectionSummary[] | null>(null);

  useEffect(() => {
    collectionApi.mine().then(setCollections).catch(() => setCollections([]));
  }, []);

  if (!collections) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold text-ink">내 문제집</h1>
        <Link href="/collections/new" className="ml-auto">
          <Button>새 문제집</Button>
        </Link>
      </header>

      {collections.length === 0 ? (
        <EmptyState
          mascot="laptop"
          title="아직 문제집이 없습니다."
          description="풀 문제를 주제나 순서로 묶어 두면 다음에 무엇을 풀지 고르는 시간이 줄어듭니다."
        />
      ) : (
        <div className="space-y-2">
          {collections.map((collection) => (
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
      )}
    </div>
  );
}
