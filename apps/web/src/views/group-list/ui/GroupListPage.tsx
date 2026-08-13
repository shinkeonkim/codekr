"use client";

import { groupApi } from "@/entities/group";
import type { GroupSummary } from "@/entities/group";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Badge, Button, Card, CardTitle, EmptyState, Field, Input, useToast } from "@/shared/ui";
import Link from "next/link";
import { useEffect, useState } from "react";

/**
 * 내 그룹 (#401, #240 6단계).
 *
 * **소속과 절대 같은 목록에 섞지 않는다** (기획서 5절). 그룹은 아무 이름이나 쓸 수
 * 있어 사칭이 가능하고, 사칭의 피해는 **섞여 보일 때** 생긴다 — 소속은 설정 화면에
 * 따로 있다.
 */
export function GroupListPage() {
  return (
    <RequireAuth>
      <GroupListView />
    </RequireAuth>
  );
}

function GroupListView() {
  const toast = useToast();
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const [name, setName] = useState("");

  const reload = () =>
    groupApi
      .mine()
      .then(setGroups)
      .catch(() => setGroups([]));

  useEffect(() => {
    reload();
  }, []);

  const create = async () => {
    if (!name.trim()) {
      toast.error("이름을 입력해 주세요.");
      return;
    }
    try {
      // 만들면 바로 방장이자 멤버다. 초대 링크는 상세에서 뽑는다.
      await groupApi.create({ name: name.trim(), description: "", openJoin: false });
      setName("");
      toast.success("만들었습니다. 초대 링크로 사람을 부르세요.");
      await reload();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "만들지 못했습니다.");
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <header>
        <h1 className="text-2xl font-bold text-ink">그룹</h1>
        <p className="mt-1 text-sm text-ink-muted">
          같이 준비하는 사람들끼리 순위를 봅니다. 학교·회사 소속은 설정에서 따로 붙입니다.
        </p>
      </header>

      <Card className="space-y-3 p-5">
        <CardTitle>새 그룹</CardTitle>
        <div className="flex gap-2">
          <Input
            placeholder="예: 알고리즘 스터디"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <Button onClick={create}>만들기</Button>
        </div>
        {/* 처음부터 공개면 스팸 가입이 온다 (기획서 5절). 공개 가입은 상세에서 켠다. */}
        <p className="text-xs text-ink-muted">
          만들면 초대 링크가 생깁니다. 링크를 아는 사람만 들어옵니다.
        </p>
      </Card>

      {groups?.length === 0 ? (
        <EmptyState title="아직 든 그룹이 없습니다." />
      ) : (
        <div className="space-y-2">
          {groups?.map((group) => (
            <Link key={group.id} href={`/groups/${group.id}`} className="block">
              <Card className="flex items-center gap-2 p-4 hover:border-brand">
                <span className="flex-1 truncate text-ink">{group.name}</span>
                {group.owner ? <Badge tone="muted">방장</Badge> : null}
                <span className="text-xs tabular-nums text-ink-muted">{group.memberCount}명</span>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
