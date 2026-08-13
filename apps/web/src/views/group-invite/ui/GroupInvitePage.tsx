"use client";

import { groupApi } from "@/entities/group";
import type { GroupInvitePreview } from "@/entities/group";
import { RequireAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { Button, Card, EmptyState, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";

/**
 * 초대 링크 (#401).
 *
 * **가입 전에 무엇에 들어가는지 보여 준다.** 누르자마자 들어가 버리면, 링크를 잘못
 * 눌렀을 때 되돌리는 일이 남는다.
 *
 * 로그인이 필요하다 — `RequireAuth` 가 로그인 뒤 이 주소로 돌려보낸다.
 */
export function GroupInvitePage({ params }: { params: Promise<{ token: string }> }) {
  return (
    <RequireAuth>
      <GroupInviteView token={use(params).token} />
    </RequireAuth>
  );
}

function GroupInviteView({ token }: { token: string }) {
  const toast = useToast();
  const router = useRouter();
  const [preview, setPreview] = useState<GroupInvitePreview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    groupApi
      .preview(token)
      .then(setPreview)
      // 링크가 죽었거나(새로 뽑혔거나) 그룹이 해산했다. 둘을 구분해 알려 주지 않는다.
      .catch((caught) =>
        setError(caught instanceof ApiError ? caught.message : "초대 링크가 유효하지 않습니다."),
      );
  }, [token]);

  if (error) return <EmptyState title={error} />;
  if (!preview) return <p className="py-16 text-center text-sm text-ink-muted">불러오는 중…</p>;

  const join = async () => {
    try {
      const { groupId } = await groupApi.joinByInvite(token);
      toast.success("들어왔습니다.");
      router.push(`/groups/${groupId}`);
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "들어오지 못했습니다.");
    }
  };

  return (
    <div className="mx-auto max-w-md space-y-4">
      <Card className="space-y-3 p-5">
        <div>
          <h1 className="text-xl font-bold text-ink">{preview.name}</h1>
          <p className="mt-1 text-xs text-ink-muted">{preview.memberCount}명이 있습니다.</p>
        </div>
        {preview.description ? (
          <p className="whitespace-pre-line text-sm text-ink">{preview.description}</p>
        ) : null}

        {preview.member ? (
          <Button onClick={() => router.push(`/groups/${preview.id}`)}>열기</Button>
        ) : (
          <Button onClick={join}>들어가기</Button>
        )}

        {/*
          **소속이 아니다.** 그룹은 누구나 만들고 이름도 아무거나 쓸 수 있다 —
          이름이 학교와 같아도 그 학교가 만든 것이라는 뜻이 아니다 (기획서 5절).
        */}
        <p className="text-xs text-ink-muted">
          그룹은 누구나 만들 수 있습니다. 이름이 학교·회사와 같아도 그곳이 만든 것은
          아닙니다.
        </p>
      </Card>
    </div>
  );
}
