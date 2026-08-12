"use client";

import { userApi } from "@/entities/user";
import { useAuth } from "@/features/auth";
import { ApiError } from "@/shared/api";
import { BrandCharacter, Button, Card, Input, useToast } from "@/shared/ui";
import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * 회원 탈퇴 (#140).
 *
 * **되돌릴 수 없다.** 유예 기간을 두지 않았으므로 그 사실을 분명히 말하고,
 * 닉네임을 직접 치게 해서 실수로 눌리지 않게 한다.
 */
export function WithdrawalCard() {
  const router = useRouter();
  const toast = useToast();
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);

  if (!user) return null;
  const matches = confirmation === user.nickname;

  const withdraw = async () => {
    setBusy(true);
    try {
      await userApi.withdraw();
      signOut();
      toast.success("탈퇴했습니다. 그동안 감사했습니다.");
      router.push("/");
    } catch (caught) {
      toast.error(caught instanceof ApiError ? caught.message : "탈퇴하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="space-y-3 border-danger/40 p-5">
      <div>
        <h2 className="text-sm font-semibold text-danger">회원 탈퇴</h2>
        <p className="mt-1 text-xs text-ink-muted">
          닉네임과 이메일은 지워지고 <strong>되돌릴 수 없습니다.</strong>
          <span className="block">
            쓰신 글과 댓글은 그대로 남고 작성자만 &ldquo;탈퇴한 사용자&rdquo;로 바뀝니다 —
            지우면 답을 단 사람의 글까지 뜻을 잃기 때문입니다.
          </span>
        </p>
      </div>

      {open ? (
        <div className="space-y-2">
          {/* 떠나는 자리다 (#261). 웃으며 축하하는 그림은 어울리지 않는다. */}
          <BrandCharacter name="goodbye" size={200} className="mx-auto" />
          <p className="text-xs text-ink">
            확인을 위해 닉네임 <strong>{user.nickname}</strong> 을 입력하세요.
          </p>
          <Input
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            placeholder={user.nickname}
          />
          <div className="flex gap-2">
            <Button variant="danger" disabled={!matches || busy} onClick={withdraw}>
              {busy ? "처리 중…" : "탈퇴합니다"}
            </Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              취소
            </Button>
          </div>
        </div>
      ) : (
        <Button variant="secondary" onClick={() => setOpen(true)}>
          탈퇴하기
        </Button>
      )}
    </Card>
  );
}
