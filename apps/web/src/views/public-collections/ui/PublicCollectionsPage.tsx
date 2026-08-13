"use client";

import { collectionApi } from "@/entities/collection";
import type { CollectionSummary } from "@/entities/collection";
import { useAuth } from "@/features/auth";
import type { Page } from "@/shared/api";
import { Badge, Button, Card, EmptyState, Input, Pagination, useToast } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 공개 문제집 목록 (#208).
 *
 * **로그인 없이 열린다** — 링크 공유만으로는 아무도 새 문제집을 발견할 수 없고,
 * 그러면 문제집을 만드는 이유의 절반("남에게 도움이 되는 것")이 닿을 길이 없다.
 *
 * **최신순이다.** 인기순은 담은 사람 수나 조회수가 필요한데 둘 다 없다 — 대신
 * 어드민이 내리는 속도가 곧 목록의 품질이 된다.
 */
export function PublicCollectionsPage() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<CollectionSummary> | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  /*
    내릴 문제집과 사유 (#208).

    브라우저 `prompt` 를 쓰지 않는 이유: **사유가 주인에게 그대로 전해진다.** 한 줄
    대화상자에 급히 적게 하는 대신 화면에서 보면서 쓰게 한다.
  */
  const [takingDown, setTakingDown] = useState<number | null>(null);
  const [reason, setReason] = useState("");

  useEffect(() => {
    let cancelled = false;
    collectionApi
      .publicList({ page, size: 20 })
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch(() => {
        if (!cancelled) setResult(null);
      });
    return () => {
      cancelled = true;
    };
  }, [page, reloadKey]);

  const takedown = async (collection: CollectionSummary, reason: string) => {
    try {
      await collectionApi.takedown(collection.id, reason);
      toast.success("목록에서 내렸습니다. 주인에게 알림이 갑니다.");
      setTakingDown(null);
      setReason("");
      setReloadKey((key) => key + 1);
    } catch {
      toast.error("내리지 못했습니다.");
    }
  };

  return (
    <div className="space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">공개 문제집</h1>
        <p className="mt-1 text-sm text-ink-muted">다른 사람이 만든 커리큘럼을 그대로 따라갈 수 있습니다.</p>
      </header>

      {result && result.content.length === 0 ? (
        <EmptyState
          mascot="study"
          title="아직 공개된 문제집이 없습니다."
          description="문제집을 만들 때 '누구나 보기' 로 두면 여기에 올라옵니다."
        />
      ) : null}

      <div className="space-y-2">
        {result?.content.map((collection) => (
          <Card key={collection.id} className="flex flex-wrap items-center gap-3 p-5">
            <Link href={`/collections/${collection.id}`} className="flex-1 truncate font-medium text-ink">
              {collection.name}
            </Link>
            {/*
              만든 사람이 보여야 따라갈지 정할 수 있고, **눌러서 그 사람의 다른 문제집**
              으로 갈 수 있어야 한다 (#209) — 양방향이어야 길이 된다.
            */}
            <Link
              href={`/users/${encodeURIComponent(collection.ownerNickname)}`}
              className="text-xs text-ink-muted hover:text-ink hover:underline"
            >
              {collection.ownerNickname}
            </Link>
            <Badge tone="muted">{collection.problemCount}문제</Badge>
            {/* 어드민에게만 보인다 (#131 과 같은 결) — 눌러서 403 이 나는 버튼은 고장으로 보인다. */}
            {isAdmin ? (
              <Button
                variant="ghost"
                className="px-2 py-0.5 text-xs"
                onClick={() => setTakingDown(takingDown === collection.id ? null : collection.id)}
              >
                내리기
              </Button>
            ) : null}

            {takingDown === collection.id ? (
              <div className="flex w-full flex-wrap items-center gap-2">
                <Input
                  className="flex-1"
                  placeholder="사유 (주인에게 그대로 전해집니다)"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
                <Button
                  variant="danger"
                  className="px-3 py-1 text-xs"
                  disabled={!reason.trim()}
                  onClick={() => takedown(collection, reason.trim())}
                >
                  내리기 확인
                </Button>
              </div>
            ) : null}
          </Card>
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
