"use client";

import { groupApi } from "@/entities/group";
import type { GroupDetail } from "@/entities/group";
import { ApiError } from "@/shared/api";
import {
  Button,
  Card,
  CardTitle,
  CheckboxField,
  ConfirmDialog,
  Input,
  Textarea,
  useToast,
} from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * 방장이 하는 일 (#401).
 *
 * **초대 링크는 여기에만 있다.** 멤버 아무나 부를 수 있게 하면 방장이 인원을 통제할
 * 길이 없고, 그러면 초대 링크가 사실상 공개 가입과 같아진다.
 */
export function GroupOwnerPanel({
  group,
  onChanged,
}: {
  group: GroupDetail;
  onChanged: () => Promise<unknown>;
}) {
  const toast = useToast();
  const router = useRouter();
  const [name, setName] = useState(group.name);
  const [description, setDescription] = useState(group.description);
  const [openJoin, setOpenJoin] = useState(group.openJoin);

  const fail = (caught: unknown, fallback: string) =>
    toast.error(caught instanceof ApiError ? caught.message : fallback);

  const save = async () => {
    try {
      await groupApi.update(group.id, { name: name.trim(), description: description.trim(), openJoin });
      toast.success("저장했습니다.");
      await onChanged();
    } catch (caught) {
      fail(caught, "저장하지 못했습니다.");
    }
  };

  const inviteUrl =
    group.inviteToken && typeof window !== "undefined"
      ? `${window.location.origin}/groups/join/${group.inviteToken}`
      : "";

  return (
    <Card className="space-y-3 p-5">
      <CardTitle>방장</CardTitle>

      <Input value={name} onChange={(event) => setName(event.target.value)} />
      <Textarea
        rows={2}
        placeholder="무엇을 하는 그룹인지 (200자)"
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
      <CheckboxField
        label="링크 없이도 누구나 가입"
        checked={openJoin}
        onCheckedChange={setOpenJoin}
      />
      {/* 켜는 것이 선택이어야 한다 — 처음부터 공개면 스팸 가입이 온다 (기획서 5절). */}
      <Button onClick={save}>저장</Button>

      <div className="space-y-1 border-t border-border pt-3">
        <p className="text-xs text-ink-muted">초대 링크</p>
        <div className="flex gap-2">
          <Input readOnly value={inviteUrl} onFocus={(event) => event.target.select()} />
          <ConfirmDialog
            trigger={
              <Button variant="secondary" className="shrink-0">
                새로 뽑기
              </Button>
            }
            title="초대 링크를 새로 뽑습니다"
            description="지금 링크는 그 자리에서 죽습니다. 이미 나눠 준 링크로는 아무도 들어올 수 없습니다."
            confirmLabel="새로 뽑기"
            onConfirm={async () => {
              try {
                await groupApi.rotateInvite(group.id);
                toast.success("새 링크를 뽑았습니다.");
                await onChanged();
              } catch (caught) {
                fail(caught, "뽑지 못했습니다.");
              }
            }}
          />
        </div>
      </div>

      <div className="border-t border-border pt-3">
        <ConfirmDialog
          trigger={
            <Button variant="ghost" className="text-xs text-danger">
              그룹 해산
            </Button>
          }
          title={`'${group.name}' 을 해산합니다`}
          description="멤버 모두에게서 사라지고 초대 링크도 죽습니다. 되돌릴 수 없습니다."
          confirmLabel="해산"
          onConfirm={async () => {
            try {
              await groupApi.remove(group.id);
              toast.success("해산했습니다.");
              router.push("/groups");
            } catch (caught) {
              fail(caught, "해산하지 못했습니다.");
            }
          }}
        />
      </div>
    </Card>
  );
}
