"use client";

import { groupApi } from "@/entities/group";
import type { OpenGroupSummary } from "@/entities/group";
import { ApiError } from "@/shared/api";
import { Button, Card, CardTitle, EmptyState, Pagination, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

const PAGE_SIZE = 20;

/**
 * 공개 가입을 켜 둔 그룹을 둘러본다 (#554).
 *
 * **`openJoin` 은 켤 수 있는데 들어갈 문이 없는 설정이었다.** 들어가는 경로
 * (`POST /groups/{id}/members`)는 서버에 있었지만 아무도 부르지 않았고, 목록은
 * 내 그룹만 줘서 **그런 그룹이 있다는 것조차 알 수 없었다.**
 *
 * 그래서 들어가는 버튼만 붙이는 것으로는 부족했다 — 찾을 길이 함께 있어야 한다.
 */
export function OpenGroupBrowser() {
  const router = useRouter();
  const toast = useToast();
  const [groups, setGroups] = useState<OpenGroupSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [joining, setJoining] = useState<number | null>(null);

  const load = useCallback(() => {
    groupApi
      .open(page, PAGE_SIZE)
      .then((result) => {
        setGroups(result.content);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch(() => setGroups([]));
  }, [page]);

  useEffect(load, [load]);

  const join = async (group: OpenGroupSummary) => {
    setJoining(group.id);
    try {
      const { groupId } = await groupApi.joinOpen(group.id);
      toast.success(`${group.name} 에 들어갔습니다.`);
      router.push(`/groups/${groupId}`);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "들어가지 못했습니다.");
      setJoining(null);
    }
  };

  return (
    <section className="space-y-3">
      <div>
        <CardTitle>둘러보기</CardTitle>
        <p className="mt-1 text-xs text-ink-muted">
          초대 없이 들어갈 수 있게 열어 둔 그룹입니다. 방장이 공개 가입을 끄면 목록에서 빠집니다.
        </p>
      </div>

      {groups === null ? (
        <p className="text-sm text-ink-muted">불러오는 중…</p>
      ) : groups.length === 0 ? (
        <EmptyState title="열려 있는 그룹이 없습니다." description="지금은 초대 링크로만 들어갈 수 있습니다." />
      ) : (
        <div className="space-y-2">
          {groups.map((group) => (
            <Card key={group.id} className="flex flex-wrap items-center gap-2 p-4">
              <div className="min-w-0 flex-1">
                <p className="truncate text-ink">{group.name}</p>
                {group.description ? (
                  <p className="truncate text-xs text-ink-muted">{group.description}</p>
                ) : null}
              </div>
              <span className="text-xs tabular-nums text-ink-muted">
                {group.memberCount}/{group.memberLimit}명
              </span>
              {/* 이미 든 그룹에는 들어가기 대신 그 사실을 보인다. */}
              {group.member ? (
                <Button asChild variant="secondary" className="px-3 py-1 text-xs">
                  <a href={`/groups/${group.id}`}>이미 들어 있음</a>
                </Button>
              ) : (
                <Button
                  className="px-3 py-1 text-xs"
                  disabled={joining !== null || group.memberCount >= group.memberLimit}
                  onClick={() => join(group)}
                >
                  {group.memberCount >= group.memberLimit ? "정원 참" : "들어가기"}
                </Button>
              )}
            </Card>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onChange={setPage} />
    </section>
  );
}
