"use client";

import { groupApi } from "@/entities/group";
import type { GroupDetail } from "@/entities/group";
import { UserLink } from "@/entities/user";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Badge, Button, Card, CardTitle, ConfirmDialog, EmptyState, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { use, useCallback, useEffect, useState } from "react";
import { GroupOwnerPanel } from "./GroupOwnerPanel";
import { GroupRanking } from "./GroupRanking";
import { PAGE_WIDTH } from "@/shared/ui/pageWidth";

/**
 * 그룹 상세 (#401).
 *
 * **명단은 멤버만 본다.** 이름과 인원까지는 초대 링크가 보여 주지만(가입 전에 무엇에
 * 들어가는지 알아야 한다), 누가 있는지는 그 안의 일이다.
 */
export function GroupDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <RequireAuth>
      <GroupDetailView id={Number(use(params).id)} />
    </RequireAuth>
  );
}

function GroupDetailView({ id }: { id: number }) {
  const toast = useToast();
  const router = useRouter();
  const [group, setGroup] = useState<GroupDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(
    () =>
      groupApi
        .detail(id)
        .then((loaded) => {
          setGroup(loaded);
          setError(null);
        })
        .catch((caught) =>
          setError(caught instanceof ApiError ? caught.message : "그룹을 불러오지 못했습니다."),
        ),
    [id],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  const act = async (run: Promise<unknown>, done: string, failed: string) => {
    try {
      await run;
      toast.success(done);
      await reload();
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : failed);
    }
  };

  if (error) return <EmptyState title={error} />;
  if (!group) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  return (
    <div className={`${PAGE_WIDTH.wide} space-y-5`}>
      <header>
        <div className="flex items-center gap-2">
          <h1 className="text-2xl font-bold text-ink">{group.name}</h1>
          {group.owner ? <Badge tone="muted">방장</Badge> : null}
        </div>
        {group.description ? (
          <p className="mt-1 whitespace-pre-line text-sm text-ink">{group.description}</p>
        ) : null}
        <p className="mt-1 text-xs text-ink-muted">
          {group.memberCount}명 / 최대 {group.memberLimit}명
        </p>
      </header>

      {group.owner ? <GroupOwnerPanel group={group} onChanged={reload} /> : null}

      {/* 그룹에 들어오는 이유가 이것이다 — 명단보다 위에 둔다 (#402). */}
      <GroupRanking groupId={group.id} />

      <Card className="space-y-3 p-5">
        <CardTitle>멤버</CardTitle>
        {group.members.map((member) => (
          <div key={member.userId} className="flex items-center gap-2 text-sm">
            <UserLink nickname={member.nickname} />
            {member.owner ? <Badge tone="muted">방장</Badge> : null}
            <span className="flex-1" />
            {group.owner && !member.owner ? (
              <>
                {/* 넘긴 사람은 그대로 멤버로 남는다 — 나가는 것과 다른 일이다. */}
                <ConfirmDialog
                  trigger={
                    <Button variant="ghost" className="px-2 py-0.5 text-xs">
                      방장 넘기기
                    </Button>
                  }
                  title={`${member.nickname} 님에게 방장을 넘깁니다`}
                  description="넘기면 이름·공개 가입·초대 링크를 그 사람이 관리합니다. 되돌리려면 그 사람이 다시 넘겨야 합니다."
                  confirmLabel="넘기기"
                  tone="primary"
                  onConfirm={() =>
                    act(
                      groupApi.transferOwner(group.id, member.userId),
                      "넘겼습니다.",
                      "넘기지 못했습니다.",
                    )
                  }
                />
                <ConfirmDialog
                  trigger={
                    <Button variant="ghost" className="px-2 py-0.5 text-xs">
                      내보내기
                    </Button>
                  }
                  title={`${member.nickname} 님을 내보냅니다`}
                  description="그룹 랭킹에서도 빠집니다. 초대 링크를 다시 주면 들어올 수 있습니다."
                  confirmLabel="내보내기"
                  onConfirm={() =>
                    act(groupApi.kick(group.id, member.userId), "내보냈습니다.", "내보내지 못했습니다.")
                  }
                />
              </>
            ) : null}
          </div>
        ))}
      </Card>

      {group.owner ? null : (
        <ConfirmDialog
          trigger={<Button variant="secondary">나가기</Button>}
          title={`'${group.name}' 에서 나갑니다`}
          description="그룹 랭킹에서 빠집니다. 다시 들어오려면 초대 링크가 필요합니다."
          confirmLabel="나가기"
          onConfirm={async () => {
            try {
              await groupApi.leave(group.id);
              toast.success("나왔습니다.");
              router.push("/groups");
            } catch (caught) {
              toast.error(caught instanceof ApiError ? caught.message : "나가지 못했습니다.");
            }
          }}
        />
      )}
    </div>
  );
}
